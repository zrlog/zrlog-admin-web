package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.exception.AIActiveConnectionException;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.rest.request.AIConnectionRequest;
import com.zrlog.admin.business.rest.request.AIGlobalSettingsRequest;
import com.zrlog.admin.business.rest.request.AISettingsRequest;
import com.zrlog.admin.business.rest.response.AIConnectionResponse;
import com.zrlog.admin.business.rest.response.AIWebSiteInfoResponse;
import com.zrlog.admin.business.service.WebSiteSettingsService;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.test.support.ZrLogTestDatabase;
import java.sql.SQLException;
import org.junit.Test;

public class AIConnectionsTest {
    private static final Gson JSON = new Gson();

    @Test
    public void legacySettingsRemainReadableWithoutWritesAndSurviveSwitchingOnBothBackends()
            throws Exception {
        for (boolean webApi : new boolean[] {false, true}) {
            try (ZrLogTestDatabase db =
                    webApi ? ZrLogTestDatabase.openWebApi() : ZrLogTestDatabase.open()) {
                seed(db);
                AIConfigService service = service(db);
                Object rows = db.scalar("SELECT count(*) FROM website");
                AIWebSiteInfoResponse original = service.aiResponse();
                assertEquals(rows, db.scalar("SELECT count(*) FROM website"));
                assertNull(value(db, "ai_connections"));
                assertEquals("legacy-model", service.ai().getAi_model());
                assertMasked(original);

                AISettingsRequest b = request(original);
                b.ai_connection_id = "";
                b.ai_connection_name = "公司 AI 🤖";
                b.setAi_provider(AIProviderType.QWEN);
                b.setAi_model("second-model");
                b.setAi_api_key("second-secret");
                save(db, b);
                AIWebSiteInfoResponse second = service.aiResponse();
                assertEquals(3, second.ai_connections.size());
                AIConnectionResponse a =
                        second.ai_connections.stream()
                                .filter(c -> c.ai_provider == AIProviderType.DEEP_SEEK)
                                .findFirst()
                                .orElseThrow();
                AIConnectionResponse savedB =
                        second.ai_connections.stream()
                                .filter(c -> c.ai_provider == AIProviderType.QWEN)
                                .findFirst()
                                .orElseThrow();
                assertEquals("公司 AI 🤖", savedB.ai_connection_name);
                assertEquals("image-secret", service.ai().getAi_image_api_key());

                AISettingsRequest back = request(second);
                select(back, a);
                save(db, back);
                assertEquals("legacy-secret", service.ai().getAi_api_key());
                assertEquals("legacy-model", service.ai().getAi_model());
                assertEquals("Keep my prompt", service.ai().getAi_prompt());
                assertFalse(service.ai().isReasoningEnabled());
                assertEquals(Integer.valueOf(512), service.ai().getAi_max_completion_tokens());
                assertEquals(3, service.aiResponse().ai_connections.size());
                select(back, savedB);
                save(db, back);
                assertEquals("second-secret", service.ai().getAi_api_key());
                assertEquals(3, service.aiResponse().ai_connections.size());
                assertMasked(service.aiResponse());
                assertFalse(
                        JSON.toJson(new WebSiteRepository(db.dataSource()).getPublicWebSite())
                                .contains("secret"));
                assertFalse(String.valueOf(value(db, "admin_audit_log")).contains("secret"));
            }
        }
    }

    @Test
    public void separateAccountsAndChangedEndpointsNeverBorrowAnotherSecret() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open()) {
            seed(db);
            AISettingsRequest newAccount = request(service(db).aiResponse());
            newAccount.ai_connection_id = "";
            assertThrows(ArgsException.class, () -> save(db, newAccount));
            assertEquals("legacy-secret", service(db).ai().getAi_api_key());
            newAccount.setAi_api_key("other-account-secret");
            save(db, newAccount);
            assertEquals(3, service(db).aiResponse().ai_connections.size());
            AISettingsRequest changed = request(service(db).aiResponse());
            changed.setAi_base_url("http://localhost:11434/v1/");
            save(db, changed);
            assertEquals("", service(db).ai().getAi_api_key());
            assertEquals(4, service(db).aiResponse().ai_connections.size());
            changed.ai_connection_id = "unknown-id";
            assertThrows(ArgsException.class, () -> save(db, changed));
            assertEquals("", service(db).ai().getAi_api_key());
        }
    }

    @Test
    public void imageServicesRemainAvailableAfterDisablingAndSwitchIndependently()
            throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.openWebApi()) {
            seed(db);
            AISettingsRequest off = request(service(db).aiResponse());
            off.setAi_image_provider(null);
            off.ai_image_connection_id = "";
            save(db, off);
            AIWebSiteInfoResponse disabled = service(db).aiResponse();
            assertNull(disabled.getAi_image_provider());
            AIConnectionResponse image =
                    disabled.ai_connections.stream()
                            .filter(c -> c.ai_kind.equals("image"))
                            .findFirst()
                            .orElseThrow();
            AISettingsRequest on = request(disabled);
            on.ai_image_connection_id = image.ai_connection_id;
            on.setAi_image_provider(image.ai_provider);
            on.setAi_image_model(image.ai_model);
            on.setAi_image_base_url(image.ai_base_url);
            save(db, on);
            assertEquals("image-secret", service(db).ai().getAi_image_api_key());
            assertEquals("legacy-secret", service(db).ai().getAi_api_key());
            assertEquals(2, service(db).aiResponse().ai_connections.size());
        }
    }

    @Test
    public void failedAtomicWriteLeavesCurrentConfigurationIntactAndRetryDoesNotDuplicate()
            throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.openWebApi()) {
            seed(db);
            AISettingsRequest b = request(service(db).aiResponse());
            b.ai_connection_id = "";
            b.setAi_provider(AIProviderType.QWEN);
            b.setAi_api_key("second-secret");
            db.beforeWebApiUpdate(
                    (sql, args) -> {
                        if (sql.startsWith("UPDATE website SET value=CASE"))
                            throw new SQLException("injected failure");
                    });
            assertThrows(SQLException.class, () -> save(db, b));
            assertEquals("legacy-secret", service(db).ai().getAi_api_key());
            assertEquals("legacy-model", service(db).ai().getAi_model());
            assertNull(value(db, "ai_connections"));
            db.beforeWebApiUpdate((sql, args) -> {});
            save(db, b);
            save(db, request(service(db).aiResponse()));
            assertEquals(3, service(db).aiResponse().ai_connections.size());
        }
    }

    @Test
    public void legacyClientsStillSaveAndUnreadableSavedConnectionsAreNeverOverwritten()
            throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open()) {
            seed(db);
            AISettingsRequest legacy = request(service(db).aiResponse());
            legacy.ai_connection_id = null;
            legacy.ai_image_connection_id = null;
            legacy.setAi_model("updated-model");
            save(db, legacy);
            assertEquals("legacy-secret", service(db).ai().getAi_api_key());
            db.putWebsite("ai_connections", "invalid-json");
            assertThrows(ArgsException.class, () -> save(db, legacy));
            assertEquals("invalid-json", value(db, "ai_connections"));
            assertEquals("updated-model", service(db).ai().getAi_model());
        }
    }

    @Test
    public void standbyEditsRequireExplicitActivationAndKeepCredentialsBoundToTheirEndpoint()
            throws Exception {
        for (boolean webApi : new boolean[] {false, true}) {
            try (ZrLogTestDatabase db =
                    webApi ? ZrLogTestDatabase.openWebApi() : ZrLogTestDatabase.open()) {
                seed(db);
                AIConfigService service = service(db);
                AIConnectionRequest standby =
                        JSON.fromJson(
                                "{\"action\":\"save\",\"ai_kind\":\"text\",\"ai_provider\":\"DEEP_SEEK\"} ",
                                AIConnectionRequest.class);
                standby.ai_connection_name = "另一个 DeepSeek";
                standby.ai_model = " standby-model ";
                standby.ai_api_key = "standby-secret";
                updateConnection(db, standby);

                AIWebSiteInfoResponse saved = service.aiResponse();
                String previousId = saved.ai_connection_id;
                String standbyId =
                        saved.ai_connections.stream()
                                .filter(
                                        connection ->
                                                standby.ai_connection_name.equals(
                                                        connection.ai_connection_name))
                                .findFirst()
                                .orElseThrow()
                                .ai_connection_id;
                assertNotEquals(previousId, standbyId);
                assertNotEquals("current-text", previousId);
                assertEquals("legacy-secret", service.ai().getAi_api_key());
                assertEquals("legacy-model", service.ai().getAi_model());
                assertMasked(saved);

                standby.ai_connection_id = standbyId;
                standby.ai_api_key = "";
                standby.ai_model = " another-model ";
                updateConnection(db, standby);
                assertEquals("legacy-model", service.ai().getAi_model());
                updateConnection(db, action(AIConnectionRequest.Action.ACTIVATE, standbyId));
                assertEquals("standby-secret", service.ai().getAi_api_key());
                assertEquals("another-model", service.ai().getAi_model());
                assertEquals("image-secret", service.ai().getAi_image_api_key());
                assertEquals(standbyId, service.aiResponse().ai_connection_id);
                assertThrows(
                        AIActiveConnectionException.class,
                        () ->
                                updateConnection(
                                        db, action(AIConnectionRequest.Action.DELETE, standbyId)));

                standby.ai_base_url = "http://localhost:11434/v1/";
                updateConnection(db, standby);
                assertEquals(standbyId, service.aiResponse().ai_connection_id);
                assertEquals("http://localhost:11434/v1", service.ai().getAi_base_url());
                assertEquals("", service.ai().getAi_api_key());
                assertEquals(3, service.aiResponse().ai_connections.size());

                updateConnection(db, action(AIConnectionRequest.Action.ACTIVATE, previousId));
                updateConnection(db, action(AIConnectionRequest.Action.DELETE, standbyId));
                assertEquals("legacy-secret", service.ai().getAi_api_key());
                assertEquals(2, service.aiResponse().ai_connections.size());
                assertFalse(String.valueOf(value(db, "admin_audit_log")).contains("secret"));
            }
        }
    }

    @Test
    public void disablingImagesRetainsTheirConnectionAndGlobalSettingsHaveIndependentWrites()
            throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open()) {
            AIGlobalSettingsRequest settings = new AIGlobalSettingsRequest();
            settings.ai_prompt = "Updated before configuring a connection";
            settings.ai_reasoning_enabled = false;
            settings.ai_max_completion_tokens = 512;
            service(db).updateGlobal(settings, audit("global"), settingsService(db));
            assertNull(service(db).aiResponse().ai_connection_id);
            assertNull(value(db, "ai_connections"));
            assertEquals(settings.ai_prompt, service(db).ai().getAi_prompt());

            seed(db);
            AIConfigService service = service(db);
            String legacyImage = service.aiResponse().ai_image_connection_id;
            updateConnection(db, action(AIConnectionRequest.Action.DISABLE_IMAGE, legacyImage));
            AIWebSiteInfoResponse disabled = service.aiResponse();
            assertNull(disabled.ai_image_connection_id);
            assertNull(disabled.getAi_image_provider());
            assertFalse(disabled.isHasAiImageApiKey());
            String imageId =
                    disabled.ai_connections.stream()
                            .filter(connection -> "image".equals(connection.ai_kind))
                            .findFirst()
                            .orElseThrow()
                            .ai_connection_id;

            Object connections = value(db, "ai_connections");
            settings.ai_max_completion_tokens = null;
            service.updateGlobal(settings, audit("global"), settingsService(db));
            assertEquals(connections, value(db, "ai_connections"));
            assertEquals(disabled.ai_connection_id, service.aiResponse().ai_connection_id);
            assertNull(service.ai().getAi_max_completion_tokens());
            assertFalse(service.ai().isReasoningEnabled());

            updateConnection(db, action(AIConnectionRequest.Action.ACTIVATE, imageId));
            assertEquals("image-secret", service.ai().getAi_image_api_key());
            assertEquals("legacy-secret", service.ai().getAi_api_key());
            assertEquals(settings.ai_prompt, service.ai().getAi_prompt());
            assertThrows(
                    ArgsException.class,
                    () ->
                            updateConnection(
                                    db,
                                    action(AIConnectionRequest.Action.DISABLE_IMAGE, legacyImage)));
            assertEquals(imageId, service.aiResponse().ai_image_connection_id);
        }
    }

    private static void seed(ZrLogTestDatabase db) throws Exception {
        db.putWebsite("ai_provider", "DEEP_SEEK");
        db.putWebsite("ai_model", "legacy-model");
        db.putWebsite("ai_api_key", "legacy-secret");
        db.putWebsite("ai_prompt", "Keep my prompt");
        db.putWebsite("ai_max_completion_tokens", "512");
        db.putWebsite("ai_reasoning_enabled", "false");
        db.putWebsite("ai_image_provider", "OPEN_AI");
        db.putWebsite("ai_image_model", "image-model");
        db.putWebsite("ai_image_base_url", "http://localhost:9000/v1");
        db.putWebsite("ai_image_api_key", "image-secret");
    }

    private static AIConfigService service(ZrLogTestDatabase db) {
        return new AIConfigService(() -> new WebSiteRepository(db.dataSource()));
    }

    private static void save(ZrLogTestDatabase db, AISettingsRequest request) throws Exception {
        service(db)
                .updateAi(
                        request,
                        new AdminAuditContext(1, "127.0.0.1", "/api/admin/website/ai", "JUnit"),
                        settingsService(db));
    }

    private static void updateConnection(ZrLogTestDatabase db, AIConnectionRequest request)
            throws SQLException {
        service(db).updateConnection(request, audit("connections"), settingsService(db));
    }

    private static AdminAuditContext audit(String endpoint) {
        return new AdminAuditContext(1, "127.0.0.1", "/api/admin/website/ai/" + endpoint, "JUnit");
    }

    private static WebSiteSettingsService settingsService(ZrLogTestDatabase db) {
        return new WebSiteSettingsService(
                null,
                null,
                () -> null,
                ApplicationServicesFixture.audit(db.dataSource()),
                ApplicationServicesFixture.websiteKv(db.dataSource()),
                () -> new WebSiteRepository(db.dataSource()));
    }

    private static AIConnectionRequest action(AIConnectionRequest.Action action, String id) {
        AIConnectionRequest request = new AIConnectionRequest();
        request.action = action;
        request.ai_connection_id = id;
        return request;
    }

    private static AISettingsRequest request(AIWebSiteInfoResponse response) {
        return JSON.fromJson(JSON.toJson(response), AISettingsRequest.class);
    }

    private static void select(AISettingsRequest request, AIConnectionResponse connection) {
        request.ai_connection_id = connection.ai_connection_id;
        request.ai_connection_name = connection.ai_connection_name;
        request.setAi_provider(connection.ai_provider);
        request.setAi_model(connection.ai_model);
        request.setAi_base_url(connection.ai_base_url);
        request.setAi_api_key("");
    }

    private static Object value(ZrLogTestDatabase db, String key) throws Exception {
        return db.scalar("SELECT value FROM website WHERE name=?", key);
    }

    private static void assertMasked(AIWebSiteInfoResponse response) {
        String json = JSON.toJson(response);
        assertFalse(json.contains("secret"));
        assertEquals("", response.getAi_api_key());
        assertEquals("", response.getAi_image_api_key());
    }
}
