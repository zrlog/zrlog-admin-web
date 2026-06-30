package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.exception.AdminAuthException;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminDashboardCardResponse;
import com.zrlog.admin.business.rest.response.AdminDashboardConfigResponse;
import com.zrlog.admin.business.rest.response.AdminManifestResponse;
import com.zrlog.admin.business.rest.response.ArticleActivityData;
import com.zrlog.admin.business.rest.request.PersonalDataPreviewRequest;
import com.zrlog.admin.business.rest.request.ReadCommentRequest;
import com.zrlog.admin.business.rest.request.TagManageRequest;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.ErrorPageResponse;
import com.zrlog.admin.business.rest.response.IndexResponse;
import com.zrlog.admin.business.rest.response.LinkPreviewResponse;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.business.rest.response.MfaStatusResponse;
import com.zrlog.admin.business.rest.response.PersonalDataCommentExportResponse;
import com.zrlog.admin.business.rest.response.PersonalDataPreviewResponse;
import com.zrlog.admin.business.rest.response.PluginInfoResponse;
import com.zrlog.admin.business.rest.response.PublicVersionResponse;
import com.zrlog.admin.business.rest.response.StatisticsInfoResponse;
import com.zrlog.admin.business.rest.response.TagManagementEntryResponse;
import com.zrlog.admin.business.rest.response.TagManagementPreviewResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.rest.response.UserBasicInfoResponse;
import com.zrlog.admin.business.rest.response.UserInfoResponse;
import com.zrlog.admin.business.service.AdminCommentService;
import com.zrlog.admin.business.service.LinkPreviewService;
import com.zrlog.admin.business.service.MessageCenterService;
import com.zrlog.admin.business.service.MfaService;
import com.zrlog.admin.business.service.PersonalDataService;
import com.zrlog.admin.business.service.TagManagementService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.support.MfaTestCodeGenerator;
import com.zrlog.admin.support.UploadFallbackZrLogConfig;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.common.Constants;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.dto.CommentDTO;
import com.zrlog.util.BlogBuildInfoUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AdminControllerDelegationTest {

    private com.zrlog.common.ZrLogConfig previousConfig;
    private String previousRunMode;

    @Before
    public void setUp() {
        previousConfig = Constants.zrLogConfig;
        previousRunMode = System.getProperty("sws.run.mode");
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();
    }

    @After
    public void tearDown() {
        AdminTokenThreadLocal.remove();
        Constants.zrLogConfig = previousConfig;
        restoreProperty("sws.run.mode", previousRunMode);
    }

    @Test
    public void shouldDelegateLinkPreviewRequestToService() throws Exception {
        LinkPreviewController controller = new LinkPreviewController();
        FakeLinkPreviewService service = new FakeLinkPreviewService();
        LinkPreviewResponse preview = new LinkPreviewResponse();
        preview.setTitle("ZrLog");
        service.response = preview;
        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/link-preview",
                Map.of("url", "https://www.zrlog.com"), Map.of("User-Agent", "Browser"), null));
        setField(controller, LinkPreviewController.class, "linkPreviewService", service);

        ApiStandardResponse<LinkPreviewResponse> response = controller.index();

        assertSame(preview, response.getData());
        assertEquals("https://www.zrlog.com", service.url);
        assertEquals("Browser", service.userAgent);
    }

    @Test
    public void shouldReturnMessageCenterNoticesFromService() throws Exception {
        MessageCenterController controller = new MessageCenterController();
        FakeMessageCenterService service = new FakeMessageCenterService();
        MessageCenterNoticeResponse notice = MessageCenterNoticeResponse.of(
                "task", "type", "notice", 1L, MessageCenterNoticeResponse.unreadCommentPayload(2));
        service.notices = List.of(notice);
        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/message-center", Map.of(), Map.of(),
                null));
        setField(controller, MessageCenterController.class, "messageCenterService", service);

        ApiStandardResponse<List<MessageCenterNoticeResponse>> response = controller.index();

        assertEquals(1, response.getData().size());
        assertSame(notice, response.getData().get(0));
    }

    @Test
    public void shouldMarkMessageCenterNoticeReadFromRequestBody() throws Exception {
        MessageCenterController controller = new MessageCenterController();
        FakeMessageCenterService service = new FakeMessageCenterService();
        service.markReadResult = true;
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/message-center/read", Map.of(),
                Map.of(), "{\"taskKey\":\"server.comment.unread\"}"));
        setField(controller, MessageCenterController.class, "messageCenterService", service);

        UpdateRecordResponse response = controller.read();

        assertEquals("server.comment.unread", service.taskKey);
        assertEquals(0, response.getError());
    }

    @Test
    public void shouldRejectInvalidMessageCenterReadBodyBeforeServiceCall() throws Exception {
        MessageCenterController controller = new MessageCenterController();
        FakeMessageCenterService service = new FakeMessageCenterService();
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/message-center/read", Map.of(),
                Map.of(), "{\"taskKey\":\"\"}"));
        setField(controller, MessageCenterController.class, "messageCenterService", service);

        assertThrows(ArgsException.class, controller::read);
        assertEquals(null, service.taskKey);
    }

    @Test
    public void shouldRejectPersonalDataPreviewWhenMethodIsNotPost() throws Exception {
        PersonalDataController controller = new PersonalDataController();
        FakePersonalDataService service = new FakePersonalDataService();
        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/personal-data/preview", Map.of(),
                Map.of(), "{\"query\":\"user@example.com\"}"));
        setField(controller, PersonalDataController.class, "personalDataService", service);

        assertThrows(ArgsException.class, controller::preview);
        assertEquals(null, service.query);
    }

    @Test
    public void shouldCleanAndDelegatePersonalDataPreviewRequest() throws Exception {
        PersonalDataController controller = new PersonalDataController();
        FakePersonalDataService service = new FakePersonalDataService();
        PersonalDataPreviewResponse preview = new PersonalDataPreviewResponse();
        preview.setQuery("user@example.com");
        service.response = preview;
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/personal-data/preview", Map.of(),
                Map.of(), "{\"query\":\" user@example.com \"}"));
        setField(controller, PersonalDataController.class, "personalDataService", service);

        ApiStandardResponse<PersonalDataPreviewResponse> response = controller.preview();

        assertSame(preview, response.getData());
        assertEquals("user@example.com", service.query);
        assertEquals(-1, service.userId);
    }

    @Test
    public void shouldPreviewAndExportPersonalDataThroughRealCommentTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            insertPersonalComment(db, 1, "export me", "export@example.com", "https://export.example",
                    "192.168.0.1", "Exporter", "2024-03-01 08:30:00", 1);
            PersonalDataController controller = new PersonalDataController();
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/personal-data", Map.of(), Map.of(),
                    null));

            AdminPageDataResponse<PersonalDataPreviewResponse> page = controller.index();

            assertEquals(0L, page.getData().getCommentCount());

            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/personal-data/preview", Map.of(),
                    Map.of(), "{\"query\":\" admin@example.com \"}"));
            ApiStandardResponse<PersonalDataPreviewResponse> preview = controller.preview();

            assertEquals("admin@example.com", preview.getData().getQuery());
            assertTrue(preview.getData().isAdminEmailMatched());

            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/personal-data/export-comments",
                    Map.of(), Map.of(), "{\"query\":\"export@example.com\"}"));
            ApiStandardResponse<PersonalDataCommentExportResponse> export = controller.exportComments();

            assertEquals(1L, export.getData().getCommentCount());
            assertEquals("export me", export.getData().getComments().get(0).getUserComment());
            assertEquals("2024-03-01 08:30:00", export.getData().getComments().get(0).getCommTime());
            assertWebsiteValueContains(db, "admin_audit_log", "EXPORT_PERSONAL_DATA");
        }
    }

    @Test
    public void shouldDelegateCommentDeleteAndReadRequests() throws Exception {
        CommentController controller = new CommentController();
        FakeAdminCommentService service = new FakeAdminCommentService();
        service.deleteResponse = new DeleteResponse(true);
        service.readResponse = new UpdateRecordResponse(true);
        setField(controller, CommentController.class, "commentService", service);

        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/comment/delete",
                Map.of("id", "1,2, 3"), Map.of(), null));
        assertSame(service.deleteResponse, controller.delete());
        assertEquals(List.of("1", "2", " 3"), List.of(service.deletedIds));

        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/comment/read", Map.of(), Map.of(),
                "{\"id\":7}"));
        assertSame(service.readResponse, controller.read());
        assertEquals(Long.valueOf(7), service.readRequest.getId());
    }

    @Test
    public void shouldDelegateCommentReadAllRequest() throws Exception {
        CommentController controller = new CommentController();
        FakeAdminCommentService service = new FakeAdminCommentService();
        setField(controller, CommentController.class, "commentService", service);
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/comment/readAll", Map.of(), Map.of(),
                null));

        UpdateRecordResponse response = controller.readAll();

        assertEquals(1, service.readAllCount);
        assertEquals(0, response.getError());
    }

    @Test
    public void shouldValidateAndDelegateTagPreviewRequest() throws Exception {
        AdminTagController controller = new AdminTagController();
        FakeTagManagementService service = new FakeTagManagementService();
        TagManagementPreviewResponse preview = new TagManagementPreviewResponse();
        preview.setOperation("merge");
        service.previewResponse = preview;
        setField(controller, AdminTagController.class, "tagManagementService", service);
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/tag/preview",
                Map.of("operation", "merge"), Map.of(),
                "{\"sourceTag\":\" <b>old</b> \",\"targetTag\":\" new \"}"));

        ApiStandardResponse<TagManagementPreviewResponse> response = controller.preview();

        assertSame(preview, response.getData());
        assertEquals("old", service.previewRequest.getSourceTag());
        assertEquals("new", service.previewRequest.getTargetTag());
        assertEquals("merge", service.previewOperation);
    }

    @Test
    public void shouldRejectInvalidTagPreviewOperationBeforeServiceCall() throws Exception {
        AdminTagController controller = new AdminTagController();
        FakeTagManagementService service = new FakeTagManagementService();
        setField(controller, AdminTagController.class, "tagManagementService", service);
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/tag/preview",
                Map.of("operation", "copy"), Map.of(),
                "{\"sourceTag\":\"old\",\"targetTag\":\"new\"}"));

        assertThrows(ArgsException.class, controller::preview);
        assertEquals(null, service.previewOperation);
    }

    @Test
    public void shouldRequireDistinctTargetTagForRenameAndMerge() throws Exception {
        AdminTagController missingTarget = new AdminTagController();
        setField(missingTarget, AdminTagController.class, "tagManagementService", new FakeTagManagementService());
        setControllerRequest(missingTarget, request(HttpMethod.POST, "/api/admin/tag/rename", Map.of(), Map.of(),
                "{\"sourceTag\":\"old\"}"));
        assertThrows(ArgsException.class, missingTarget::rename);

        AdminTagController sameTarget = new AdminTagController();
        setField(sameTarget, AdminTagController.class, "tagManagementService", new FakeTagManagementService());
        setControllerRequest(sameTarget, request(HttpMethod.POST, "/api/admin/tag/merge", Map.of(), Map.of(),
                "{\"sourceTag\":\"old\",\"targetTag\":\"old\"}"));
        assertThrows(ArgsException.class, sameTarget::merge);
    }

    @Test
    public void shouldDelegateTagDeleteWithoutTargetTag() throws Exception {
        AdminTagController controller = new AdminTagController();
        FakeTagManagementService service = new FakeTagManagementService();
        TagManagementPreviewResponse deleted = new TagManagementPreviewResponse();
        deleted.setOperation("delete");
        service.executeResponse = deleted;
        setField(controller, AdminTagController.class, "tagManagementService", service);
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/tag/delete", Map.of(), Map.of(),
                "{\"sourceTag\":\"old\"}"));

        ApiStandardResponse<TagManagementPreviewResponse> response = controller.delete();

        assertSame(deleted, response.getData());
        assertEquals("old", service.executeRequest.getSourceTag());
        assertEquals("delete", service.executeOperation);
    }

    @Test
    public void shouldReturnPublicVersionWhenBuildIdMatches() throws Exception {
        AdminPublicController controller = new AdminPublicController();
        ResponseRecorder responseRecorder = new ResponseRecorder();
        setControllerRequest(controller, request(HttpMethod.GET, "/api/public/version",
                Map.of("buildId", BlogBuildInfoUtil.getBuildId()), Map.of("Origin", "https://admin.example"),
                null));
        setControllerResponse(controller, responseRecorder.response());

        ApiStandardResponse<PublicVersionResponse> response = controller.version();

        assertEquals(BlogBuildInfoUtil.getBuildId(), response.getData().getBuildId());
        assertEquals("https://admin.example", responseRecorder.headers.get("Access-Control-Allow-Origin"));
        assertEquals("true", responseRecorder.headers.get("Access-Control-Allow-Credentials"));
    }

    @Test
    public void shouldReturnEmptyPublicVersionWhenBuildIdDoesNotMatch() throws Exception {
        AdminPublicController controller = new AdminPublicController();
        setControllerRequest(controller, request(HttpMethod.GET, "/api/public/version",
                Map.of("buildId", "old-build"), Map.of("Origin", "https://admin.example"), null));
        setControllerResponse(controller, new ResponseRecorder().response());

        ApiStandardResponse<PublicVersionResponse> response = controller.version();

        assertEquals(null, response.getData().getBuildId());
    }

    @Test
    public void shouldToggleDevModeWithServerConfigMappers() throws Exception {
        ServerConfig serverConfig = new ServerConfig();
        AdminDevController controller = new AdminDevController();
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/dev/mode",
                Map.of(), Map.of(), null, serverConfig));
        Method method = AdminDevController.class.getDeclaredMethod("setDevMode", boolean.class);
        method.setAccessible(true);

        method.invoke(controller, true);

        assertEquals("dev", System.getProperty("sws.run.mode"));
        assertEquals(true, serverConfig.getStaticResourceMapper().containsKey("/admin/dev/file/"));
        assertEquals(true, serverConfig.getStaticResourceMapper().containsKey("/admin/dev/file/tmp/"));

        method.invoke(controller, false);

        assertEquals(false, serverConfig.getStaticResourceMapper().containsKey("/admin/dev/file/"));
        assertEquals(false, serverConfig.getStaticResourceMapper().containsKey("/admin/dev/file/tmp/"));
    }

    @Test
    public void shouldRejectUserInfoRequestsWithoutAdminToken() throws Exception {
        AdminUserController controller = new AdminUserController();
        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/user", Map.of(), Map.of(), null));

        assertThrows(AdminAuthException.class, controller::index);
        assertThrows(AdminAuthException.class, controller::info);
    }

    @Test
    public void shouldDelegateMfaStatusLookupUsingThreadLocalUserIdFallback() throws Exception {
        AdminUserController controller = new AdminUserController();
        FakeMfaService service = new FakeMfaService();
        MfaStatusResponse status = new MfaStatusResponse();
        status.setAccountName("admin");
        service.status = status;
        setField(controller, AdminUserController.class, "mfaService", service);
        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/user/mfa", Map.of(), Map.of(), null));

        AdminPageDataResponse<MfaStatusResponse> response = controller.mfa();

        assertSame(status, response.getData());
        assertEquals(-1, service.userId);
    }

    @Test
    public void shouldHandleUserProfilePasswordAndMfaThroughRealUserTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            AdminUserController controller = new AdminUserController();
            db.execute("update user set password=? where userId=?",
                    PasswordHashUtils.hash(SecurityUtils.md5("old-password")), 1);

            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/user", Map.of(), Map.of(), null));
            AdminPageDataResponse<UserBasicInfoResponse> basicInfo = controller.index();
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/user/info", Map.of(), Map.of(), null));
            AdminPageDataResponse<UserInfoResponse> userInfo = controller.info();

            assertEquals("admin", basicInfo.getData().getUserName());
            assertEquals("admin@example.com", basicInfo.getData().getEmail());
            assertEquals("session-1", userInfo.getData().getKey());
            assertFalse(userInfo.getData().isMfaEnabled());

            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/user", Map.of(), Map.of(),
                    "{\"userName\":\"root\",\"email\":\"root@example.com\",\"header\":\"/attached/root.png\"}"));
            UpdateRecordResponse updateProfile = controller.update();
            Map<String, Object> profile = db.queryOne("select userName,email,header from user where userId=?", 1);

            assertEquals(0, updateProfile.getError());
            assertEquals("root", profile.get("userName"));
            assertEquals("root@example.com", profile.get("email"));
            assertEquals("/attached/root.png", profile.get("header"));
            assertWebsiteValueContains(db, "admin_audit_log", "UPDATE_PROFILE");

            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/user/update-password", Map.of(),
                    Map.of(), "{\"oldPassword\":\"old-password\",\"newPassword\":\"new-password\"}"));
            UpdateRecordResponse updatePassword = controller.updatePassword();
            String storedPassword = String.valueOf(db.scalar("select password from user where userId=?", 1));

            assertEquals(0, updatePassword.getError());
            assertTrue(PasswordHashUtils.matches(SecurityUtils.md5("new-password"), storedPassword));
            assertWebsiteValueContains(db, "admin_audit_log", "UPDATE_PASSWORD");

            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/user/mfa", Map.of(), Map.of(), null));
            AdminPageDataResponse<MfaStatusResponse> mfaStatus = controller.mfa();
            String secret = mfaStatus.getData().getSecret();

            assertFalse(mfaStatus.getData().isEnabled());
            assertEquals("root", mfaStatus.getData().getAccountName());
            assertTrue(mfaStatus.getData().getOtpauthUrl().startsWith("otpauth://totp/"));

            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/user/mfa/enable", Map.of(),
                    Map.of(), "{\"code\":\"" + currentCode(secret) + "\"}"));
            UpdateRecordResponse enableMfa = controller.enableMfa();

            assertEquals(0, enableMfa.getError());
            assertEquals(true, db.queryOne("select mfaEnabled from user where userId=?", 1).get("mfaEnabled"));
            assertWebsiteValueContains(db, "admin_audit_log", "ENABLE_MFA");

            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/user/mfa/disable", Map.of(),
                    Map.of(), "{\"code\":\"" + currentCode(secret) + "\"}"));
            UpdateRecordResponse disableMfa = controller.disableMfa();
            Map<String, Object> mfa = db.queryOne("select mfaEnabled,mfaSecret from user where userId=?", 1);

            assertEquals(0, disableMfa.getError());
            assertEquals(false, mfa.get("mfaEnabled"));
            assertEquals(null, mfa.get("mfaSecret"));
            assertWebsiteValueContains(db, "admin_audit_log", "DISABLE_MFA");
        }
    }

    @Test
    public void shouldReturnSimpleAdminControllerPageResponses() throws Exception {
        AdminController controller = new AdminController();
        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/error",
                Map.of("message", "not found"), Map.of(), null));

        AdminPageDataResponse<ErrorPageResponse> error = controller.error();

        assertEquals("not found", error.getData().getMessage());

        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/plugin",
                Map.of("page", "comment/index"), Map.of(), null));
        AdminPageDataResponse<PluginInfoResponse> plugin = controller.plugin();

        assertEquals("admin/plugins/comment/index", plugin.getData().getIncludePagePath());
    }

    @Test
    public void shouldCheckAndExecuteNoChangeUpgradeThroughRealWebsiteState() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            UpgradeController controller = new UpgradeController();
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/upgrade", Map.of(), Map.of(), null));

            AdminPageDataResponse<PreCheckVersionResponse> index = controller.index();

            assertEquals(Boolean.FALSE, index.getData().getUpgrade());
            assertTrue(index.getMessage().length() > 0);
            assertEquals(true, index.getData().getOnlineUpgradable());

            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/upgrade/notice",
                    Map.of("fetch", "true"), Map.of(), null));
            ApiStandardResponse<CheckVersionResponse> notice = controller.notice();

            assertEquals(Boolean.FALSE, notice.getData().getUpgrade());
            assertEquals(null, notice.getData().getVersion());

            ResponseRecorder responseRecorder = new ResponseRecorder();
            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/upgrade/do", Map.of(),
                    Map.of("Accept", "application/json"), null));
            setControllerResponse(controller, responseRecorder.response());

            controller.doUpgrade();

            ApiStandardResponse<?> rendered = (ApiStandardResponse<?>) responseRecorder.renderedJson;
            UpgradeProcessResponse process = (UpgradeProcessResponse) rendered.getData();
            assertEquals(Boolean.TRUE, process.getFinish());
            assertTrue(process.getMessage().length() > 0);
            assertWebsiteValueContains(db, "admin_audit_log", "EXECUTE_UPGRADE");
        }
    }

    @Test
    public void shouldServeManifestPersistDashboardConfigAndRecordRefreshCacheThroughRealWebsiteTable()
            throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            AdminController controller = new AdminController();
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/manifest", Map.of(),
                    Map.of("User-Agent", "Browser"), null));

            AdminManifestResponse manifest = controller.manifest();

            assertEquals("ZrLog Test", manifest.getShort_name());
            assertTrue(manifest.getIcons().stream().allMatch(icon -> icon.getSrc().startsWith("/blog/admin/")));

            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/index-config", Map.of(), Map.of(),
                    null));
            ApiStandardResponse<AdminDashboardConfigResponse> initialConfig = controller.indexConfig();

            assertTrue(initialConfig.getData().getCards().stream()
                    .anyMatch(card -> "welcome".equals(card.getId())));

            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/index-config", Map.of(), Map.of(),
                    "{\"autoRefreshEnabled\":true,\"autoRefreshIntervalSeconds\":5,"
                            + "\"cards\":[{\"kind\":\"card\",\"id\":\"activity\",\"enabled\":false,\"sort\":1}]}"));
            ApiStandardResponse<AdminDashboardConfigResponse> savedConfig = controller.indexConfig();
            AdminDashboardCardResponse activity = findCard(savedConfig.getData(), "activity");
            String storedConfig = String.valueOf(db.queryOne(
                    "select value from website where name=?", "admin_dashboard_config").get("value"));

            assertEquals(false, activity.getEnabled());
            assertEquals(60, savedConfig.getData().getAutoRefreshIntervalSeconds().intValue());
            assertTrue(savedConfig.getMessage().length() > 0);
            assertTrue(storedConfig.contains("\"id\":\"activity\""));
            assertTrue(storedConfig.contains("\"enabled\":false"));

            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/refresh-cache", Map.of(),
                    Map.of(), null));
            controller.refreshCache();
            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("REFRESH_CACHE"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldLoadAdminIndexDashboardThroughRealTables() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            AdminController controller = new AdminController();
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin", Map.of(), Map.of(), null));

            AdminPageDataResponse<IndexResponse> response = controller.index();
            AdminDashboardConfigResponse config = response.getData().getDashboardConfig();
            AdminDashboardCardResponse welcome = findCard(config, "welcome");
            AdminDashboardCardResponse statistics = findCard(config, "statistics");
            AdminDashboardCardResponse auditTrail = findCard(config, "auditTrail");
            AdminDashboardCardResponse dataInsights = findCard(config, "dataInsights");

            assertTrue(((Map<String, Object>) welcome.getData()).containsKey("welcomeTip"));
            assertTrue(((Map<String, Object>) welcome.getData()).containsKey("versionInfo"));
            assertTrue(statistics.getData() instanceof StatisticsInfoResponse);
            assertTrue(((Map<String, Object>) auditTrail.getData()).containsKey("auditLogs"));
            assertTrue(((Map<String, Object>) dataInsights.getData()).containsKey("typeData"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldAttachDashboardCardDataByCardId() throws Exception {
        AdminController controller = new AdminController();
        AdminDashboardConfigResponse config = new AdminDashboardConfigResponse();
        AdminDashboardCardResponse welcome = card("welcome");
        AdminDashboardCardResponse quickAction = card("quickAction");
        AdminDashboardCardResponse statistics = card("statistics");
        AdminDashboardCardResponse activity = card("activity");
        AdminDashboardCardResponse auditTrail = card("auditTrail");
        AdminDashboardCardResponse dataInsights = card("dataInsights");
        AdminDashboardCardResponse pluginSurface = new AdminDashboardCardResponse();
        pluginSurface.setKind("plugin");
        pluginSurface.setId("plugin-card");
        config.setCards(List.of(welcome, quickAction, statistics, activity, auditTrail, dataInsights, pluginSurface));
        StatisticsInfoResponse statisticsInfo = new StatisticsInfoResponse();
        statisticsInfo.setDraftCount(4L);
        statisticsInfo.setAuditLogs(List.of(Map.of("action", "login")));
        ArticleActivityData activityData = new ArticleActivityData("2026-06-29", 3L);

        Method method = AdminController.class.getDeclaredMethod("attachDashboardCardData",
                AdminDashboardConfigResponse.class, StatisticsInfoResponse.class, String.class, List.class,
                String.class, List.class);
        method.setAccessible(true);
        method.invoke(controller, config, statisticsInfo, "Welcome", List.of("Tip"), "3.6.0",
                List.of(activityData));

        Map<String, Object> welcomeData = (Map<String, Object>) welcome.getData();
        assertEquals("Welcome", welcomeData.get("welcomeTip"));
        assertEquals(List.of("Tip"), welcomeData.get("tips"));
        assertEquals("3.6.0", welcomeData.get("versionInfo"));
        assertEquals(4L, ((Map<String, Object>) quickAction.getData()).get("draftCount"));
        assertSame(statisticsInfo, statistics.getData());
        assertEquals(List.of(activityData), activity.getData());
        assertEquals(List.of(Map.of("action", "login")), ((Map<String, Object>) auditTrail.getData()).get("auditLogs"));
        assertEquals(false, ((Map<String, Object>) auditTrail.getData()).get("loading"));
        assertEquals(null, pluginSurface.getData());
        assertEquals(null, ((Map<String, Object>) dataInsights.getData()).get("typeData"));
    }

    @Test
    public void shouldResolveDashboardCardEnabledState() throws Exception {
        AdminController controller = new AdminController();
        Method method = AdminController.class.getDeclaredMethod("isCardEnabled", AdminDashboardConfigResponse.class,
                String.class);
        method.setAccessible(true);

        assertEquals(true, method.invoke(controller, new Object[]{null, "activity"}));
        AdminDashboardConfigResponse noCards = new AdminDashboardConfigResponse();
        noCards.setCards(null);
        assertEquals(true, method.invoke(controller, noCards, "activity"));

        AdminDashboardConfigResponse config = new AdminDashboardConfigResponse();
        AdminDashboardCardResponse disabled = card("activity");
        disabled.setEnabled(false);
        AdminDashboardCardResponse enabled = card("auditTrail");
        enabled.setEnabled(true);
        config.setCards(List.of(disabled, enabled));

        assertEquals(false, method.invoke(controller, config, "activity"));
        assertEquals(true, method.invoke(controller, config, "auditTrail"));
        assertEquals(true, method.invoke(controller, config, "missing"));
    }

    private static void setControllerRequest(Controller controller, HttpRequest request) throws Exception {
        Field field = Controller.class.getDeclaredField("request");
        field.setAccessible(true);
        field.set(controller, request);
    }

    private static void setControllerResponse(Controller controller, HttpResponse response) throws Exception {
        Field field = Controller.class.getDeclaredField("response");
        field.setAccessible(true);
        field.set(controller, response);
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static AdminDashboardCardResponse card(String id) {
        AdminDashboardCardResponse card = new AdminDashboardCardResponse();
        card.setKind("card");
        card.setId(id);
        return card;
    }

    private static AdminDashboardCardResponse findCard(AdminDashboardConfigResponse config, String id) {
        return config.getCards().stream()
                .filter(card -> "card".equals(card.getKind()) && id.equals(card.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing card " + id));
    }

    private static void assertWebsiteValueContains(InMemoryZrLogDatabase db, String name, String expected)
            throws SQLException {
        assertTrue(String.valueOf(db.queryOne("select value from website where name=?", name).get("value"))
                .contains(expected));
    }

    private static String currentCode(String secret) throws Exception {
        return MfaTestCodeGenerator.currentCode(secret);
    }

    private static void insertPersonalComment(InMemoryZrLogDatabase db, int id, String comment, String mail,
                                              String home, String ip, String name, String time, int logId)
            throws SQLException {
        db.execute("insert into comment(commentId,commTime,hide,have_read,userComment,userMail,userHome,userIp,userName,"
                        + "logId) values(?,?,?,?,?,?,?,?,?,?)",
                id, Timestamp.valueOf(time), false, false, comment, mail, home, ip, name, logId);
    }

    private static HttpRequest request(HttpMethod method, String uri, Map<String, String> params,
                                       Map<String, String> headers, String body) {
        return request(method, uri, params, headers, body, new ServerConfig());
    }

    private static HttpRequest request(HttpMethod method, String uri, Map<String, String> params,
                                       Map<String, String> headers, String body, ServerConfig serverConfig) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminControllerDelegationTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, calledMethod, args) -> {
                    if ("getMethod".equals(calledMethod.getName())) {
                        return method;
                    }
                    if ("getUri".equals(calledMethod.getName())) {
                        return uri;
                    }
                    if ("getContextPath".equals(calledMethod.getName())) {
                        return "/blog";
                    }
                    if ("getScheme".equals(calledMethod.getName())) {
                        return "https";
                    }
                    if ("getHeader".equals(calledMethod.getName())) {
                        return headers.get(args[0].toString());
                    }
                    if ("getHeaderMap".equals(calledMethod.getName())) {
                        return headers;
                    }
                    if ("getRemoteHost".equals(calledMethod.getName())) {
                        return "127.0.0.1";
                    }
                    if ("getServerConfig".equals(calledMethod.getName())) {
                        return serverConfig;
                    }
                    if ("getParaToStr".equals(calledMethod.getName())) {
                        String key = args[0].toString();
                        if (args.length == 2) {
                            return params.getOrDefault(key, args[1].toString());
                        }
                        return params.get(key);
                    }
                    if ("getParaToBool".equals(calledMethod.getName())) {
                        String key = args[0].toString();
                        Boolean defaultValue = args.length == 2 ? (Boolean) args[1] : null;
                        String value = params.get(key);
                        return value == null ? defaultValue : Boolean.parseBoolean(value);
                    }
                    if ("getParamMap".equals(calledMethod.getName()) || "decodeParamMap".equals(calledMethod.getName())) {
                        Map<String, String[]> paramMap = new HashMap<>();
                        params.forEach((key, value) -> paramMap.put(key, new String[]{value}));
                        return paramMap;
                    }
                    if ("getInputStream".equals(calledMethod.getName())) {
                        if (body == null) {
                            return null;
                        }
                        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                    }
                    if ("toString".equals(calledMethod.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static void setAdminToken() throws Exception {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(1);
        token.setSessionId("session-1");
        token.setProtocol("http");
        Method method = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
        method.setAccessible(true);
        method.invoke(null, token);
    }

    private static class ResponseRecorder {

        private final Map<String, String> headers = new LinkedHashMap<>();
        private Object renderedJson;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminControllerDelegationTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("addHeader".equals(method.getName())) {
                            headers.put(args[0].toString(), args[1].toString());
                        }
                        if ("getHeader".equals(method.getName())) {
                            return headers;
                        }
                        if ("renderJson".equals(method.getName())) {
                            renderedJson = args[0];
                            return null;
                        }
                        return null;
                    });
        }
    }

    private static class FakeLinkPreviewService extends LinkPreviewService {

        private String url;
        private String userAgent;
        private LinkPreviewResponse response;

        @Override
        public LinkPreviewResponse fetch(String url, String userAgent) {
            this.url = url;
            this.userAgent = userAgent;
            return response;
        }
    }

    private static class FakeMessageCenterService extends MessageCenterService {

        private List<MessageCenterNoticeResponse> notices = List.of();
        private boolean markReadResult;
        private String taskKey;

        @Override
        public List<MessageCenterNoticeResponse> listNotices() throws SQLException {
            return notices;
        }

        @Override
        public boolean markRead(String taskKey) {
            this.taskKey = taskKey;
            return markReadResult;
        }
    }

    private static class FakePersonalDataService extends PersonalDataService {

        private PersonalDataPreviewResponse response;
        private String query;
        private int userId;

        @Override
        public PersonalDataPreviewResponse preview(PersonalDataPreviewRequest request, int currentUserId) {
            query = request.getQuery();
            userId = currentUserId;
            return response;
        }
    }

    private static class FakeAdminCommentService extends AdminCommentService {

        private DeleteResponse deleteResponse;
        private UpdateRecordResponse readResponse;
        private String[] deletedIds = new String[0];
        private ReadCommentRequest readRequest;
        private int readAllCount;

        @Override
        public DeleteResponse delete(String[] ids) {
            deletedIds = ids;
            return deleteResponse;
        }

        @Override
        public UpdateRecordResponse read(ReadCommentRequest commentRequest) {
            readRequest = commentRequest;
            return readResponse;
        }

        @Override
        public void readAll() {
            readAllCount++;
        }

        @Override
        public PageData<CommentDTO> page(PageRequest pageable) {
            return new PageData<>();
        }
    }

    private static class FakeTagManagementService extends TagManagementService {

        private TagManagementPreviewResponse previewResponse;
        private TagManagementPreviewResponse executeResponse;
        private TagManageRequest previewRequest;
        private String previewOperation;
        private TagManageRequest executeRequest;
        private String executeOperation;

        @Override
        public PageData<TagManagementEntryResponse> find(String homeUrl, PageRequest pageRequest, String key) {
            return new PageData<>();
        }

        @Override
        public TagManagementPreviewResponse preview(TagManageRequest request, String operation) {
            previewRequest = request;
            previewOperation = operation;
            return previewResponse;
        }

        @Override
        public TagManagementPreviewResponse execute(TagManageRequest request, String operation) {
            executeRequest = request;
            executeOperation = operation;
            return executeResponse;
        }
    }

    private static class FakeMfaService extends MfaService {

        private MfaStatusResponse status;
        private int userId;

        @Override
        public MfaStatusResponse getMfaStatus(int userId) {
            this.userId = userId;
            return status;
        }
    }
}
