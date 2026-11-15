package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.audit.AdminAuditContext;
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
        WebSiteSettingsService settings =
                new WebSiteSettingsService(
                        null,
                        null,
                        () -> null,
                        ApplicationServicesFixture.audit(db.dataSource()),
                        ApplicationServicesFixture.websiteKv(db.dataSource()),
                        () -> new WebSiteRepository(db.dataSource()));
        service(db)
                .updateAi(
                        request,
                        new AdminAuditContext(1, "127.0.0.1", "/api/admin/website/ai", "JUnit"),
                        settings);
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
