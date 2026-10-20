package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.account.AvatarStorage;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.exception.AdminAuthException;
import com.zrlog.admin.business.rest.request.PersonalDataPreviewRequest;
import com.zrlog.admin.business.rest.request.TagManageRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.support.*;
import com.zrlog.admin.test.support.DockerControlStub;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.controller.page.AdminManifestController;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.exception.AbstractBusinessException;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.Version;
import com.zrlog.common.web.ZrLogErrorHandle;
import com.zrlog.data.util.DistributedLockManager;
import com.zrlog.model.*;
import com.zrlog.plugin.IPlugin;
import com.zrlog.updater.spi.DockerControl;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AdminControllerDelegationTest {

    private ZrLogConfig applicationConfig;
    private String previousRunMode;

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

    @Before
    public void setUp() {
        previousRunMode = System.getProperty("sws.run.mode");
        applicationConfig = new UploadFallbackZrLogConfig();
    }

    @After
    public void tearDown() {
        AdminTokenThreadLocal.remove();
        restoreProperty("sws.run.mode", previousRunMode);
    }

    @Test
    public void shouldDelegateLinkPreviewRequestToService() throws Exception {
        LinkPreviewController controller =
                new LinkPreviewController(
                        new LinkPreviewService(ApplicationServicesFixture.websiteCache(null)));
        FakeLinkPreviewService service = new FakeLinkPreviewService(null);
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
        FakeMessageCenterService service = new FakeMessageCenterService(applicationConfig);
        MessageCenterController controller =
                new MessageCenterController(service, ApplicationServicesFixture.operations(null));
        MessageCenterNoticeResponse notice = MessageCenterNoticeResponse.of(
                "task", "type", "notice", 1L, MessageCenterNoticeResponse.unreadCommentPayload(2));
        service.notices = List.of(notice);
        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/message-center", Map.of(), Map.of(),
                null));

        ApiStandardResponse<List<MessageCenterNoticeResponse>> response = controller.index();

        assertEquals(1, response.getData().size());
        assertSame(notice, response.getData().get(0));
    }

    @Test
    public void shouldMarkMessageCenterNoticeReadFromRequestBody() throws Exception {
        FakeMessageCenterService service = new FakeMessageCenterService(applicationConfig);
        MessageCenterController controller =
                new MessageCenterController(service, ApplicationServicesFixture.operations(null));
        service.markReadResult = true;
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/message-center/read", Map.of(),
                Map.of(), "{\"taskKey\":\"server.comment.unread\"}"));

        UpdateRecordResponse response = controller.read();

        assertEquals("server.comment.unread", service.taskKey);
        assertEquals(0, response.getError());
    }

    @Test
    public void shouldRejectInvalidMessageCenterReadBodyBeforeServiceCall() throws Exception {
        FakeMessageCenterService service = new FakeMessageCenterService(applicationConfig);
        MessageCenterController controller =
                new MessageCenterController(service, ApplicationServicesFixture.operations(null));
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/message-center/read", Map.of(),
                Map.of(), "{\"taskKey\":\"\"}"));

        assertThrows(ArgsException.class, controller::read);
        assertEquals(null, service.taskKey);
    }

    @Test
    public void shouldRejectPersonalDataPreviewWhenMethodIsNotPost() throws Exception {
        PersonalDataController controller =
                new PersonalDataController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        ApplicationServicesFixture.audit(null),
                        new PersonalDataService(() -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }, () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }),
                        AccountServicesFixture.component(null).requestIdentity());
        FakePersonalDataService service = new FakePersonalDataService(null);
        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/personal-data/preview", Map.of(),
                Map.of(), "{\"query\":\"user@example.com\"}"));
        setField(controller, PersonalDataController.class, "personalDataService", service);

        assertThrows(ArgsException.class, controller::preview);
        assertEquals(null, service.query);
    }

    @Test
    public void shouldCleanAndDelegatePersonalDataPreviewRequest() throws Exception {
        PersonalDataController controller =
                new PersonalDataController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        ApplicationServicesFixture.audit(null),
                        new PersonalDataService(() -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }, () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }),
                        AccountServicesFixture.component(null).requestIdentity());
        FakePersonalDataService service = new FakePersonalDataService(null);
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
            applicationConfig = db.config();
            setAdminToken();
            insertPersonalComment(db, 1, "export me", "export@example.com", "https://export.example",
                    "192.168.0.1", "Exporter", "2024-03-01 08:30:00", 1);
            PersonalDataController controller =
                    new PersonalDataController(
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(db.config()),
                                    ApplicationServicesFixture.buildId(db.resources()),
                                    ApplicationServicesFixture.messages(db.dataSource())),
                            ApplicationServicesFixture.audit(db.dataSource()),
                            new PersonalDataService(() -> new User(db.dataSource()), () -> new Comment(db.dataSource())),
                            AccountServicesFixture.component(db.dataSource()).requestIdentity());
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
    public void shouldDeleteAndReadCommentsThroughTheProductionControllerGraph() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var controller = db.component(com.zrlog.admin.web.di.content.ContentComponent.class).commentController();
            for (int id : List.of(1, 2, 3, 7)) {
                db.execute("insert into comment(commentId,userComment,have_read,logId) values(?,?,?,?)",
                        id, "Comment " + id, false, 1);
            }
            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/comment/delete",
                    Map.of("id", "1,2, 3"), Map.of(), null));
            assertTrue(controller.delete().getData().getDelete());
            assertEquals(1, ((Number) db.scalar("select count(*) from comment")).intValue());
            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/comment/read", Map.of(), Map.of(),
                    "{\"id\":7}"));
            assertEquals(0, controller.read().getError());
            assertTrue(com.zrlog.data.security.AccountAccess.truth(
                    db.scalar("select have_read from comment where commentId=7")));
        }
    }

    @Test
    public void shouldReadAllCommentsThroughTheProductionControllerGraph() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            db.execute("insert into comment(commentId,userComment,have_read,logId) values(1,'Unread',false,1)");
            var controller = db.component(com.zrlog.admin.web.di.content.ContentComponent.class).commentController();
            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/comment/readAll", Map.of(), Map.of(), null));
            assertEquals(0, controller.readAll().getError());
            assertEquals(0, ((Number) db.scalar("select count(*) from comment where have_read=false")).intValue());
        }
    }

    @Test
    public void shouldValidateAndDelegateTagPreviewRequest() throws Exception {
        AdminTagController controller =
                new AdminTagController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        ApplicationServicesFixture.site(applicationConfig),
                        new TagManagementService(() -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }, () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }),
                        AccountServicesFixture.component(null).requestIdentity());
        FakeTagManagementService service = new FakeTagManagementService(null);
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
        AdminTagController controller =
                new AdminTagController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        ApplicationServicesFixture.site(applicationConfig),
                        new TagManagementService(() -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }, () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }),
                        AccountServicesFixture.component(null).requestIdentity());
        FakeTagManagementService service = new FakeTagManagementService(null);
        setField(controller, AdminTagController.class, "tagManagementService", service);
        setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/tag/preview",
                Map.of("operation", "copy"), Map.of(),
                "{\"sourceTag\":\"old\",\"targetTag\":\"new\"}"));

        assertThrows(ArgsException.class, controller::preview);
        assertEquals(null, service.previewOperation);
    }

    @Test
    public void shouldRequireDistinctTargetTagForRenameAndMerge() throws Exception {
        AdminTagController missingTarget =
                new AdminTagController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        ApplicationServicesFixture.site(applicationConfig),
                        new TagManagementService(() -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }, () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }),
                        AccountServicesFixture.component(null).requestIdentity());
        setField(missingTarget, AdminTagController.class, "tagManagementService", new FakeTagManagementService(null));
        setControllerRequest(missingTarget, request(HttpMethod.POST, "/api/admin/tag/rename", Map.of(), Map.of(),
                "{\"sourceTag\":\"old\"}"));
        assertThrows(ArgsException.class, missingTarget::rename);

        AdminTagController sameTarget =
                new AdminTagController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        ApplicationServicesFixture.site(applicationConfig),
                        new TagManagementService(() -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }, () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }),
                        AccountServicesFixture.component(null).requestIdentity());
        setField(sameTarget, AdminTagController.class, "tagManagementService", new FakeTagManagementService(null));
        setControllerRequest(sameTarget, request(HttpMethod.POST, "/api/admin/tag/merge", Map.of(), Map.of(),
                "{\"sourceTag\":\"old\",\"targetTag\":\"old\"}"));
        assertThrows(ArgsException.class, sameTarget::merge);
    }

    @Test
    public void shouldDelegateTagDeleteWithoutTargetTag() throws Exception {
        AdminTagController controller =
                new AdminTagController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        ApplicationServicesFixture.site(applicationConfig),
                        new TagManagementService(() -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }, () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        }),
                        AccountServicesFixture.component(null).requestIdentity());
        FakeTagManagementService service = new FakeTagManagementService(null);
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
        AdminPublicController controller =
                new AdminPublicController(
                        new AdminResourceService(
                                AdminResourceInfoResponse.Capabilities::new,
                                new FeatureLabService(
                                        () -> true,
                                        new WebhookService(
                                                ApplicationServicesFixture.messages(null),
                                                ApplicationServicesFixture.websiteCache(null)),
                                        () -> {
                                            throw new IllegalStateException("Database is unavailable in this test");
                                        }),
                                ApplicationServicesFixture.site(applicationConfig),
                                () ->
                                        new WebSite(null)
                                                .getStringValueByName(
                                                        "admin_static_resource_base_url"),
                                () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                }),
                        () -> false);
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
        AdminPublicController controller =
                new AdminPublicController(
                        new AdminResourceService(
                                AdminResourceInfoResponse.Capabilities::new,
                                new FeatureLabService(
                                        () -> true,
                                        new WebhookService(
                                                ApplicationServicesFixture.messages(null),
                                                ApplicationServicesFixture.websiteCache(null)),
                                        () -> {
                                            throw new IllegalStateException("Database is unavailable in this test");
                                        }),
                                ApplicationServicesFixture.site(applicationConfig),
                                () ->
                                        new WebSite(null)
                                                .getStringValueByName(
                                                        "admin_static_resource_base_url"),
                                () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                }),
                        () -> false);
        setControllerRequest(controller, request(HttpMethod.GET, "/api/public/version",
                Map.of("buildId", "old-build"), Map.of("Origin", "https://admin.example"), null));
        setControllerResponse(controller, new ResponseRecorder().response());

        ApiStandardResponse<PublicVersionResponse> response = controller.version();

        assertEquals(null, response.getData().getBuildId());
    }

    @Test
    public void shouldToggleDevModeWithServerConfigMappers() throws Exception {
        ServerConfig serverConfig = new ServerConfig();
        AdminDevController controller =
                new AdminDevController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        ApplicationServicesFixture.audit(null),
                        ApplicationServicesFixture.websiteCache(null),
                        new DistributedLockManager(
                                () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                }, ApplicationServicesFixture.locks(null)),
                        AccountServicesFixture.component(null).requestIdentity());
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
        AdminUserController controller =
                new AdminUserController(
                        AccountServicesFixture.component(null).users(),
                        AccountServicesFixture.component(null).profiles(),
                        new InMemoryZrLogDatabase.TestCacheService(),
                        ApplicationServicesFixture.capability(
                                applicationConfig, AvatarStorage.class),
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        new PasskeyService(
                                ApplicationServicesFixture.siteHost(applicationConfig),
                                new UserService(
                                        java.util.Collections::emptySet,
                                        AccountServicesFixture.component(null).mfa(),
                                        AccountServicesFixture.component(null).preferences(),
                                        () -> {
                                            throw new IllegalStateException("Database is unavailable in this test");
                                        }),
                                () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                },
                                () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                },
                                () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                },
                                ApplicationServicesFixture.locks(null)),
                        AccountServicesFixture.component(null).mfa(),
                        AccountServicesFixture.component(null).preferences(),
                        ApplicationServicesFixture.audit(null),
                        AccountServicesFixture.component(null).requestIdentity());
        setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/user", Map.of(), Map.of(), null));

        assertThrows(AdminAuthException.class, controller::index);
        assertThrows(AdminAuthException.class, controller::info);
    }

    @Test
    public void shouldRefreshAdminStaticPagesAfterPasskeyChanges() throws Exception {
        for (String methodName : List.of("passkeyRegistrationVerify", "passkeyRemove")) {
            RefreshCache refreshCache = AdminUserController.class.getDeclaredMethod(methodName)
                    .getAnnotation(RefreshCache.class);

            assertNotNull(refreshCache);
            assertFalse(refreshCache.async());
            assertEquals(List.of(StaticSiteType.ADMIN), List.of(refreshCache.updateStaticSites()));
        }
    }

    @Test
    public void shouldDelegateMfaStatusLookupUsingThreadLocalUserIdFallback() throws Exception {
        AdminUserController controller =
                new AdminUserController(
                        AccountServicesFixture.component(null).users(),
                        AccountServicesFixture.component(null).profiles(),
                        new InMemoryZrLogDatabase.TestCacheService(),
                        ApplicationServicesFixture.capability(
                                applicationConfig, AvatarStorage.class),
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        new PasskeyService(
                                ApplicationServicesFixture.siteHost(applicationConfig),
                                new UserService(
                                        java.util.Collections::emptySet,
                                        AccountServicesFixture.component(null).mfa(),
                                        AccountServicesFixture.component(null).preferences(),
                                        () -> {
                                            throw new IllegalStateException("Database is unavailable in this test");
                                        }),
                                () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                },
                                () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                },
                                () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                },
                                ApplicationServicesFixture.locks(null)),
                        AccountServicesFixture.component(null).mfa(),
                        AccountServicesFixture.component(null).preferences(),
                        ApplicationServicesFixture.audit(null),
                        AccountServicesFixture.component(null).requestIdentity());
        FakeMfaService service = new FakeMfaService(null);
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
            applicationConfig = db.config();
            setAdminToken();
            AdminUserController controller =
                    new AdminUserController(
                            AccountServicesFixture.component(db.dataSource()).users(),
                            AccountServicesFixture.component(db.dataSource()).profiles(),
                            db.unitCacheService(),
                            ApplicationServicesFixture.capability(db.config(), AvatarStorage.class),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(db.config()),
                                    ApplicationServicesFixture.buildId(db.resources()),
                                    ApplicationServicesFixture.messages(db.dataSource())),
                            new PasskeyService(
                                    ApplicationServicesFixture.siteHost(db.config()),
                                    new UserService(
                                            java.util.Collections::emptySet,
                                            AccountServicesFixture.component(db.dataSource()).mfa(),
                                            AccountServicesFixture.component(db.dataSource()).preferences(),
                                            () -> new User(db.dataSource())),
                                    () -> new User(db.dataSource()),
                                    () -> new UserPasskey(db.dataSource()),
                                    () -> new UserPasskeyChallenge(db.dataSource()),
                                    ApplicationServicesFixture.locks(db.dataSource())),
                            AccountServicesFixture.component(db.dataSource()).mfa(),
                            AccountServicesFixture.component(db.dataSource()).preferences(),
                            ApplicationServicesFixture.audit(db.dataSource()),
                            AccountServicesFixture.component(db.dataSource()).requestIdentity());
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
            AdminTokenThreadLocal.getUser().setAuthVersion(1); // A fresh login after changing the password.

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
        AdminController controller =
                new AdminController(
                        new AdminDashboardService(
                                new WebsiteKvService(
                                        () -> {
                                            throw new IllegalStateException(
                                                    "Database is unavailable in this test");
                                        }),
                                new AdminStatisticsService(
                                        new InMemoryZrLogDatabase.TestCacheService(),
                                        RuntimeInfoFixture.from(applicationConfig),
                                        ApplicationServicesFixture.audit(null),
                                        () -> {
                                            throw new IllegalStateException(
                                                    "Database is unavailable in this test");
                                        },
                                        () -> {
                                            throw new IllegalStateException(
                                                    "Database is unavailable in this test");
                                        }),
                                new ArticleQueryService(new InMemoryZrLogDatabase.TestCacheService(),
                                        ApplicationServicesFixture.capability(
                                                applicationConfig, ArticleAssistant.class),
                                        new AdminPageDataFactory(
                                                ApplicationServicesFixture.site(applicationConfig),
                                                () -> "test",
                                                ApplicationServicesFixture.messages(null)),
                                        AccountServicesFixture.component(null).permissions(),
                                        AccountServicesFixture.component(null).preferences(),
                                        () -> {
                                            throw new IllegalStateException(
                                                    "Database is unavailable in this test");
                                        }),
                                () -> applicationConfig.getPlugin(PluginCorePlugin.class),
                                AccountServicesFixture.component(null).preferences(),
                                AccountServicesFixture.component(null).identities()),
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(null)),
                        ApplicationServicesFixture.audit(null),
                        AccountServicesFixture.component(null).requestIdentity());
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
    public void shouldLoadUpgradePageWithoutMetadataButKeepExplicitCheckFailure() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        UpdateVersionInfoPlugin plugin =
                new UpdateVersionInfoPlugin(
                        ApplicationServicesFixture.upgradeNotices(applicationConfig),
                        () -> "zh_CN",
                        applicationConfig == null ? null : applicationConfig.getUpdater(), new WebsiteKvService(() -> {
                    throw new IllegalStateException("Database is unavailable in this test");
                })) {
                    @Override
                    public Version getLastVersion(boolean fetch) {
                        if (fetch) {
                            checks.incrementAndGet();
                        }
                        return null;
                    }

                    @Override
                    public boolean isLastCheckFailed() {
                        return true;
                    }
                };
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            applicationConfig =
                    new UploadFallbackZrLogConfig() {
                        @Override
                        public <T extends IPlugin> T getPlugin(Class<T> type) {
                            return type.isInstance(plugin)
                                    ? type.cast(plugin)
                                    : super.getPlugin(type);
                        }
                    };
            setAdminToken();
            UpgradeController controller =
                    new UpgradeController(
                            ApplicationServicesFixture.upgrades(applicationConfig),
                            () ->
                                    applicationConfig == null
                                            ? null
                                            : applicationConfig.getPlugin(
                                            UpdateVersionInfoPlugin.class),
                            () ->
                                    UpdaterHosts.dockerControl(
                                            applicationConfig == null
                                                    ? null
                                                    : applicationConfig.getUpdater()),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(applicationConfig),
                                    () -> "test",
                                    ApplicationServicesFixture.messages(db.dataSource())),
                            new DockerUpgradeProgressService(),
                            ApplicationServicesFixture.audit(db.dataSource()),
                            ApplicationServicesFixture.operations(db.dataSource()),
                            AccountServicesFixture.component(db.dataSource()).requestIdentity());
            for (String path : List.of("/admin/upgrade", "/admin/ssJson")) {
                setControllerRequest(controller, request(HttpMethod.GET, path, Map.of(), Map.of(), null));
                AdminPageDataResponse<PreCheckVersionResponse> page = controller.index();
                assertEquals(Boolean.FALSE, page.getData().getUpgrade());
                assertEquals(null, page.getData().getVersion());
            }
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/upgrade",
                    Map.of("fetch", "false"), Map.of(), null));
            assertEquals(Boolean.FALSE, controller.index().getData().getUpgrade());
            assertEquals(0, checks.get());
            for (String path : List.of("/api/admin/upgrade", "/sub/api/admin/upgrade")) {
                setControllerRequest(controller, request(HttpMethod.GET, path, Map.of(), Map.of(), null));
                assertThrows(AbstractBusinessException.class, controller::index);
            }
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/upgrade",
                    Map.of("fetch", "true"), Map.of(), null));
            AbstractBusinessException error =
                    assertThrows(AbstractBusinessException.class, controller::index);
            ResponseRecorder recorder = new ResponseRecorder();
            new ZrLogErrorHandle(500)
                    .doHandle(
                            request(HttpMethod.GET, "/api/admin/upgrade", Map.of(), Map.of(), null),
                            recorder.response(),
                            error);
            ApiStandardResponse<?> response = (ApiStandardResponse<?>) recorder.renderedJson;
            assertEquals(9038, response.getError());
            assertEquals(
                    I18nUtil.getBackendStringFromRes("upgrade.check.failed"),
                    response.getMessage());
            assertFalse(response.getMessage().isBlank());
            assertEquals(3, checks.get());
        }
    }

    @Test
    public void shouldCheckAndExecuteNoChangeUpgradeThroughRealWebsiteState() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            setAdminToken();
            UpgradeController controller =
                    new UpgradeController(
                            ApplicationServicesFixture.upgrades(db.config()),
                            () ->
                                    db.config() == null
                                            ? null
                                            : db.config().getPlugin(UpdateVersionInfoPlugin.class),
                            () ->
                                    UpdaterHosts.dockerControl(
                                            db.config() == null ? null : db.config().getUpdater()),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(db.config()),
                                    ApplicationServicesFixture.buildId(db.resources()),
                                    ApplicationServicesFixture.messages(db.dataSource())),
                            new DockerUpgradeProgressService(),
                            ApplicationServicesFixture.audit(db.dataSource()),
                            ApplicationServicesFixture.operations(db.dataSource()),
                            AccountServicesFixture.component(db.dataSource()).requestIdentity());
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

            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/upgrade/doUpgrade", Map.of(),
                    Map.of("Accept", "application/json"), "{\"upgradeRiskAccepted\":false}"));
            assertThrows(ArgsException.class, controller::doUpgrade);

            ResponseRecorder responseRecorder = new ResponseRecorder();
            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/upgrade/doUpgrade", Map.of(),
                    Map.of("Accept", "application/json"), "{\"upgradeRiskAccepted\":true}"));
            setControllerResponse(controller, responseRecorder.response());

            controller.doUpgrade();

            ApiStandardResponse<?> rendered = (ApiStandardResponse<?>) responseRecorder.renderedJson;
            UpgradeProcessResponse process = (UpgradeProcessResponse) rendered.getData();
            assertEquals(Boolean.TRUE, process.getFinish());
            assertTrue(process.getMessage().length() > 0);
            assertWebsiteValueContains(db, "admin_audit_log", "EXECUTE_UPGRADE");
            assertWebsiteValueContains(db, "admin_audit_log", "upgradeRiskAccepted");
        }
    }

    @Test
    public void shouldReturnDockerRepositoryFailureCodeWithoutServiceDetails() throws Exception {
        var client =
                new DockerControlStub() {
                    @Override
                    public DockerUpdaterProtocol.SettingsResponse settings(String channel) {
                        assertEquals("preview", channel);
                        throw new RequestException(502, "metadata_unavailable");
                    }
                };
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            WebSiteController controller =
                    new WebSiteController(
                            new WebSiteSettingsService(
                                    db.unitCacheService(),
                                    db.config() == null ? null : db.config().getTokenService(),
                                    () ->
                                            db.config() == null
                                                    ? null
                                                    : db.config()
                                                    .getPlugin(
                                                            UpdateVersionInfoPlugin.class),
                                    ApplicationServicesFixture.audit(db.dataSource()),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource())),
                            new UpgradeSettingsService(new WebSiteSettingsService(
                                    db.unitCacheService(),
                                    db.config() == null
                                            ? null
                                            : db.config().getTokenService(),
                                    () ->
                                            db.config() == null
                                                    ? null
                                                    : db.config()
                                                    .getPlugin(
                                                            UpdateVersionInfoPlugin
                                                                    .class),
                                    ApplicationServicesFixture.audit(db.dataSource()),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource())),
                                    () ->
                                            UpdaterHosts.dockerControl(
                                                    db.config() == null
                                                            ? null
                                                            : db.config().getUpdater()),
                                    AccountServicesFixture.component(db.dataSource()).permissions(),
                                    AccountServicesFixture.component(db.dataSource()).identities(),
                                    ApplicationServicesFixture.settings(db.dataSource())),
                            new FeatureLabService(
                                    () -> true,
                                    new WebhookService(
                                            ApplicationServicesFixture.messages(db.dataSource()),
                                            ApplicationServicesFixture.websiteCache(db.dataSource())),
                                    () -> new WebSite(db.dataSource())),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(db.config()),
                                    ApplicationServicesFixture.buildId(db.resources()),
                                    ApplicationServicesFixture.messages(db.dataSource())),
                            ApplicationServicesFixture.settings(db.dataSource()),
                            AccountServicesFixture.component(db.dataSource()).requestIdentity()) {
                        @Override
                        protected UpgradeSettingsService upgradeSettingsService() {
                            return new UpgradeSettingsService(new WebSiteSettingsService(
                                    db.unitCacheService(),
                                    db.config() == null
                                            ? null
                                            : db.config().getTokenService(),
                                    () ->
                                            db.config() == null
                                                    ? null
                                                    : db.config()
                                                    .getPlugin(
                                                            UpdateVersionInfoPlugin
                                                                    .class),
                                    ApplicationServicesFixture.audit(db.dataSource()),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource())),
                                    () ->
                                            UpdaterHosts.dockerControl(
                                                    db.config() == null
                                                            ? null
                                                            : db.config().getUpdater()),
                                    AccountServicesFixture.component(db.dataSource()).permissions(),
                                    AccountServicesFixture.component(db.dataSource()).identities(),
                                    ApplicationServicesFixture.settings(db.dataSource())) {
                                @Override
                                protected boolean isDockerMode() {
                                    return true;
                                }

                                @Override
                                protected DockerControl dockerClient() {
                                    return client;
                                }
                            };
                        }
                    };
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/website/upgrade",
                    Map.of("channel", "preview"), Map.of(), null));
            var response = controller.upgrade();
            assertEquals(0, response.getError());
            assertTrue(response.getData().isDockerMode());
            assertEquals("metadata_unavailable", response.getData().getDockerErrorCode());
            assertEquals("", response.getMessage());
            assertEquals(null, response.getData().getDockerSettings());
        }
    }

    @Test
    public void shouldSubmitDockerJobWithRiskConsentAndRecoverById() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            setAdminToken();
            var job = new DockerUpdaterProtocol.Job();
            job.id = "browser-request-1234";
            job.phase = "queued";
            var submissions = new AtomicInteger();
            var client =
                    new DockerControlStub() {
                        @Override
                        public DockerUpdaterProtocol.Job submit(
                                DockerUpdaterProtocol.UpgradeRequest request) {
                            submissions.incrementAndGet();
                            assertEquals(job.id, request.requestId);
                            assertEquals("4.0.0-SNAPSHOT", request.version);
                            assertEquals("preview", request.channel);
                            return job;
                        }

                        @Override
                        public DockerUpdaterProtocol.Job status(String id) {
                            assertEquals(job.id, id);
                            return job;
                        }
                    };
            UpgradeController controller =
                    new UpgradeController(
                            ApplicationServicesFixture.upgrades(db.config()),
                            () ->
                                    db.config() == null
                                            ? null
                                            : db.config().getPlugin(UpdateVersionInfoPlugin.class),
                            () ->
                                    UpdaterHosts.dockerControl(
                                            db.config() == null ? null : db.config().getUpdater()),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(db.config()),
                                    ApplicationServicesFixture.buildId(db.resources()),
                                    ApplicationServicesFixture.messages(db.dataSource())),
                            new DockerUpgradeProgressService(),
                            ApplicationServicesFixture.audit(db.dataSource()),
                            ApplicationServicesFixture.operations(db.dataSource()),
                            AccountServicesFixture.component(db.dataSource()).requestIdentity()) {
                        @Override
                        protected DockerControl dockerClient() {
                            return client;
                        }
                    };
            String docker = ",\"docker\":{\"requestId\":\"browser-request-1234\",\"version\":\"4.0.0-SNAPSHOT\","
                    + "\"buildId\":\"1234567\",\"channel\":\"preview\"}}";
            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/upgrade/doUpgrade", Map.of(),
                    Map.of(), "{\"upgradeRiskAccepted\":false" + docker));
            assertThrows(ArgsException.class, controller::doUpgrade);
            assertEquals(0, submissions.get());
            ResponseRecorder recorder = new ResponseRecorder();
            setControllerRequest(controller, request(HttpMethod.POST, "/api/admin/upgrade/doUpgrade", Map.of(),
                    Map.of(), "{\"upgradeRiskAccepted\":true" + docker));
            setControllerResponse(controller, recorder.response());
            controller.doUpgrade();
            assertEquals(1, submissions.get());
            assertSame(job, ((ApiStandardResponse<?>) recorder.renderedJson).getData());
            assertWebsiteValueContains(db, "admin_audit_log", "EXECUTE_UPGRADE");
            job.phase = "succeeded";
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/upgrade/docker-status",
                    Map.of("requestId", job.id), Map.of(), null));
            controller.dockerStatus();
            assertSame(job, ((ApiStandardResponse<?>) recorder.renderedJson).getData());
            for (String action : List.of("doUpgrade", "docker-status")) {
                ResponseRecorder stream = new ResponseRecorder();
                setControllerResponse(controller, stream.response());
                setControllerRequest(controller, request(action.equals("doUpgrade") ? HttpMethod.POST : HttpMethod.GET,
                        "/api/admin/upgrade/" + action, Map.of("requestId", job.id),
                        Map.of("Accept", "text/event-stream"), "{\"upgradeRiskAccepted\":true" + docker));
                if (action.equals("doUpgrade")) {
                    controller.doUpgrade();
                } else {
                    controller.dockerStatus();
                }
                assertEquals("text/event-stream;charset=UTF-8", stream.headers.get("Content-Type"));
                assertEquals("no", stream.headers.get("X-Accel-Buffering"));
                assertTrue(stream.stream.contains("event: upgrade-progress\n"));
                assertTrue(stream.stream.contains("\"stage\":\"complete\""));
                assertTrue(stream.stream.contains("event: response\n"));
                assertTrue(stream.stream.contains("\"finish\":true"));
                assertFalse(stream.stream.contains("\"message\":\"\""));
                assertTrue(
                        stream.stream.contains(
                                I18nUtil.getAdminBackendStringFromRes(
                                        "admin.upgrade.docker.succeeded")));
                assertEquals(null, stream.renderedJson);
            }
            assertEquals(2, submissions.get());
        }
    }

    @Test
    public void shouldServeManifestPersistDashboardConfigAndRecordRefreshCacheThroughRealWebsiteTable()
            throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            setAdminToken();
            AdminController controller =
                    new AdminController(
                            new AdminDashboardService(
                                    new WebsiteKvService(() -> new WebSite(db.dataSource())),
                                    new AdminStatisticsService(
                                            db.unitCacheService(),
                                            RuntimeInfoFixture.from(db.config()),
                                            ApplicationServicesFixture.audit(db.dataSource()),
                                            () -> new Log(db.dataSource()),
                                            () -> new Comment(db.dataSource())),
                                    new ArticleQueryService(db.unitCacheService(),
                                            ApplicationServicesFixture.capability(
                                                    db.config(), ArticleAssistant.class),
                                            new AdminPageDataFactory(
                                                    ApplicationServicesFixture.site(db.config()),
                                                    ApplicationServicesFixture.buildId(
                                                            db.resources()),
                                                    ApplicationServicesFixture.messages(
                                                            db.dataSource())),
                                            AccountServicesFixture.component(db.dataSource())
                                                    .permissions(),
                                            AccountServicesFixture.component(db.dataSource())
                                                    .preferences(),
                                            () -> new Log(db.dataSource())),
                                    () -> db.config().getPlugin(PluginCorePlugin.class),
                                    AccountServicesFixture.component(db.dataSource())
                                            .preferences(),
                                    AccountServicesFixture.component(db.dataSource())
                                            .identities()),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(db.config()),
                                    ApplicationServicesFixture.buildId(db.resources()),
                                    ApplicationServicesFixture.messages(db.dataSource())),
                            ApplicationServicesFixture.audit(db.dataSource()),
                            AccountServicesFixture.component(
                                    db.dataSource()).requestIdentity());
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin/manifest", Map.of(),
                    Map.of("User-Agent", "Browser"), null));

            var manifestController =
                    new AdminManifestController(db.unitCacheService()::getPublicWebSiteInfo);
            setControllerRequest(manifestController, request(HttpMethod.GET, "/api/admin/manifest", Map.of(),
                    Map.of("User-Agent", "Browser"), null));
            AdminManifestResponse manifest = manifestController.manifest();

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
                    "select preferences as value from user where userId=1").get("value"));

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
            applicationConfig = ignored.config();
            setAdminToken();
            AdminController controller =
                    new AdminController(
                            new AdminDashboardService(
                                    new WebsiteKvService(() -> new WebSite(ignored.dataSource())),
                                    new AdminStatisticsService(
                                            ignored.unitCacheService(),
                                            RuntimeInfoFixture.from(ignored.config()),
                                            ApplicationServicesFixture.audit(ignored.dataSource()),
                                            () -> new Log(ignored.dataSource()),
                                            () -> new Comment(ignored.dataSource())),
                                    new ArticleQueryService(ignored.unitCacheService(),
                                            ApplicationServicesFixture.capability(
                                                    ignored.config(), ArticleAssistant.class),
                                            new AdminPageDataFactory(
                                                    ApplicationServicesFixture.site(
                                                            ignored.config()),
                                                    ApplicationServicesFixture.buildId(
                                                            ignored.resources()),
                                                    ApplicationServicesFixture.messages(
                                                            ignored.dataSource())),
                                            AccountServicesFixture.component(ignored.dataSource())
                                                    .permissions(),
                                            AccountServicesFixture.component(ignored.dataSource())
                                                    .preferences(),
                                            () -> new Log(ignored.dataSource())),
                                    () -> ignored.config().getPlugin(PluginCorePlugin.class),
                                    AccountServicesFixture.component(ignored.dataSource())
                                            .preferences(),
                                    AccountServicesFixture.component(ignored.dataSource())
                                            .identities()),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(ignored.config()),
                                    ApplicationServicesFixture.buildId(ignored.resources()),
                                    ApplicationServicesFixture.messages(ignored.dataSource())),
                            ApplicationServicesFixture.audit(ignored.dataSource()),
                            AccountServicesFixture.component(
                                    ignored.dataSource()).requestIdentity());
            setControllerRequest(controller, request(HttpMethod.GET, "/api/admin", Map.of(), Map.of(), null));

            AdminPageDataResponse<IndexResponse> response = controller.index();
            AdminDashboardConfigResponse config = response.getData().getDashboardConfig();
            AdminDashboardCardResponse welcome = findCard(config, "welcome");
            AdminDashboardCardResponse statistics = findCard(config, "statistics");
            AdminDashboardCardResponse auditTrail = findCard(config, "auditTrail");
            AdminDashboardCardResponse dataInsights = findCard(config, "dataInsights");

            assertTrue(welcome.getData() instanceof AdminDashboardWelcomeDataResponse);
            assertTrue(((AdminDashboardWelcomeDataResponse) welcome.getData()).getWelcomeTip() != null);
            assertTrue(((AdminDashboardWelcomeDataResponse) welcome.getData()).getVersionInfo() != null);
            assertTrue(statistics.getData() instanceof StatisticsInfoResponse);
            assertTrue(auditTrail.getData() instanceof AdminDashboardAuditTrailDataResponse);
            assertTrue(((AdminDashboardAuditTrailDataResponse) auditTrail.getData()).getAuditLogs() != null);
            assertTrue(dataInsights.getData() instanceof AdminDashboardDataInsightsResponse);
        }
    }

    @Test
    public void shouldAttachDashboardCardDataByCardId() throws Exception {
        AdminDashboardService service =
                new AdminDashboardService(
                        new WebsiteKvService(
                                () -> {
                                    throw new IllegalStateException(
                                            "Database is unavailable in this test");
                                }),
                        new AdminStatisticsService(
                                new InMemoryZrLogDatabase.TestCacheService(),
                                RuntimeInfoFixture.from(applicationConfig),
                                ApplicationServicesFixture.audit(null),
                                () -> {
                                    throw new IllegalStateException(
                                            "Database is unavailable in this test");
                                },
                                () -> {
                                    throw new IllegalStateException(
                                            "Database is unavailable in this test");
                                }),
                        new ArticleQueryService(new InMemoryZrLogDatabase.TestCacheService(),
                                ApplicationServicesFixture.capability(
                                        applicationConfig, ArticleAssistant.class),
                                new AdminPageDataFactory(
                                        ApplicationServicesFixture.site(applicationConfig),
                                        () -> "test",
                                        ApplicationServicesFixture.messages(null)),
                                AccountServicesFixture.component(null).permissions(),
                                AccountServicesFixture.component(null).preferences(),
                                () -> {
                                    throw new IllegalStateException(
                                            "Database is unavailable in this test");
                                }),
                        () -> applicationConfig.getPlugin(PluginCorePlugin.class),
                        AccountServicesFixture.component(null).preferences(),
                        AccountServicesFixture.component(null).identities());
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
        AdminAuditLogEntryResponse auditLog = new AdminAuditLogEntryResponse();
        auditLog.setAction("login");
        statisticsInfo.setAuditLogs(List.of(auditLog));
        ArticleActivityData activityData = new ArticleActivityData("2026-06-29", 3L);

        Method method = AdminDashboardService.class.getDeclaredMethod("attachCardData",
                AdminDashboardConfigResponse.class, StatisticsInfoResponse.class, String.class, List.class,
                String.class, List.class);
        method.setAccessible(true);
        method.invoke(service, config, statisticsInfo, "Welcome", List.of("Tip"), "3.6.0",
                List.of(activityData));

        AdminDashboardWelcomeDataResponse welcomeData = (AdminDashboardWelcomeDataResponse) welcome.getData();
        assertEquals("Welcome", welcomeData.getWelcomeTip());
        assertEquals(List.of("Tip"), welcomeData.getTips());
        assertEquals("3.6.0", welcomeData.getVersionInfo());
        assertEquals(4L, ((AdminDashboardQuickActionDataResponse) quickAction.getData()).getDraftCount().longValue());
        assertSame(statisticsInfo, statistics.getData());
        assertEquals(List.of(activityData), activity.getData());
        AdminDashboardAuditTrailDataResponse auditTrailData = (AdminDashboardAuditTrailDataResponse) auditTrail.getData();
        assertEquals(List.of(auditLog), auditTrailData.getAuditLogs());
        assertEquals(false, auditTrailData.getLoading());
        assertEquals(null, pluginSurface.getData());
        assertEquals(null, ((AdminDashboardDataInsightsResponse) dataInsights.getData()).getTypeData());
    }

    @Test
    public void shouldResolveDashboardCardEnabledState() throws Exception {
        AdminDashboardService service =
                new AdminDashboardService(
                        new WebsiteKvService(
                                () -> {
                                    throw new IllegalStateException(
                                            "Database is unavailable in this test");
                                }),
                        new AdminStatisticsService(
                                new InMemoryZrLogDatabase.TestCacheService(),
                                RuntimeInfoFixture.from(applicationConfig),
                                ApplicationServicesFixture.audit(null),
                                () -> {
                                    throw new IllegalStateException(
                                            "Database is unavailable in this test");
                                },
                                () -> {
                                    throw new IllegalStateException(
                                            "Database is unavailable in this test");
                                }),
                        new ArticleQueryService(new InMemoryZrLogDatabase.TestCacheService(),
                                ApplicationServicesFixture.capability(
                                        applicationConfig, ArticleAssistant.class),
                                new AdminPageDataFactory(
                                        ApplicationServicesFixture.site(applicationConfig),
                                        () -> "test",
                                        ApplicationServicesFixture.messages(null)),
                                AccountServicesFixture.component(null).permissions(),
                                AccountServicesFixture.component(null).preferences(),
                                () -> {
                                    throw new IllegalStateException(
                                            "Database is unavailable in this test");
                                }),
                        () -> applicationConfig.getPlugin(PluginCorePlugin.class),
                        AccountServicesFixture.component(null).preferences(),
                        AccountServicesFixture.component(null).identities());
        Method method = AdminDashboardService.class.getDeclaredMethod("isCardEnabled", AdminDashboardConfigResponse.class,
                String.class);
        method.setAccessible(true);

        assertEquals(true, method.invoke(service, new Object[]{null, "activity"}));
        AdminDashboardConfigResponse noCards = new AdminDashboardConfigResponse();
        noCards.setCards(null);
        assertEquals(true, method.invoke(service, noCards, "activity"));

        AdminDashboardConfigResponse config = new AdminDashboardConfigResponse();
        AdminDashboardCardResponse disabled = card("activity");
        disabled.setEnabled(false);
        AdminDashboardCardResponse enabled = card("auditTrail");
        enabled.setEnabled(true);
        config.setCards(List.of(disabled, enabled));

        assertEquals(false, method.invoke(service, config, "activity"));
        assertEquals(true, method.invoke(service, config, "auditTrail"));
        assertEquals(true, method.invoke(service, config, "missing"));
    }

    private static class ResponseRecorder {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Object renderedJson;
        private String stream;

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
                        if ("write".equals(method.getName()) && args[0] instanceof java.io.InputStream) {
                            try (java.io.InputStream input = (java.io.InputStream) args[0]) {
                                stream = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                            }
                            return null;
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
        FakeLinkPreviewService(DataSourceWrapper testDataSource) {
            super(ApplicationServicesFixture.websiteCache(testDataSource));
        }

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
        FakeMessageCenterService(ZrLogConfig config) {
            super(
                    ApplicationServicesFixture.upgradeNotices(config),
                    new WebhookService(
                            ApplicationServicesFixture.messages((DataSourceWrapper) config.getDataSource()),
                            ApplicationServicesFixture.websiteCache((DataSourceWrapper) config.getDataSource())),
                    ApplicationServicesFixture.messages((DataSourceWrapper) config.getDataSource()),
                    ApplicationServicesFixture.operations((DataSourceWrapper) config.getDataSource()),
                    new AdminCommentService(AccountServicesFixture.component((DataSourceWrapper) config.getDataSource()).permissions(),
                            AccountServicesFixture.component((DataSourceWrapper) config.getDataSource()).identities(),
                            ApplicationServicesFixture.messages((DataSourceWrapper) config.getDataSource()),
                            () -> new Comment((DataSourceWrapper) config.getDataSource())));
        }

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
        FakePersonalDataService(DataSourceWrapper testDataSource) {
            super(() -> new User(testDataSource), () -> new Comment(testDataSource));
        }

        @Override
        public PersonalDataPreviewResponse preview(PersonalDataPreviewRequest request, int currentUserId) {
            query = request.getQuery();
            userId = currentUserId;
            return response;
        }
    }

    private static class FakeTagManagementService extends TagManagementService {
        private TagManagementPreviewResponse previewResponse;
        private TagManagementPreviewResponse executeResponse;
        private TagManageRequest previewRequest;
        private String previewOperation;
        private TagManageRequest executeRequest;
        private String executeOperation;
        FakeTagManagementService(DataSourceWrapper testDataSource) {
            super(() -> new Log(testDataSource), () -> new Tag(testDataSource));
        }

        @Override
        public PageData<TagManagementEntryResponse> find(String homeUrl, PageRequest pageRequest, String key, boolean staticHtml) {
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
        FakeMfaService(DataSourceWrapper testDataSource) {
            super(() -> new User(testDataSource));
        }

        @Override
        public MfaStatusResponse getMfaStatus(int userId, String siteTitle) {
            this.userId = userId;
            return status;
        }
    }
}
