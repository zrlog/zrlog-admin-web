package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.WebhookConfigRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.WebhookConfigResponse;
import com.zrlog.admin.business.rest.response.WebhookMessageNoticeCreateResponse;
import com.zrlog.admin.business.rest.response.WebhookTokenResponse;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.OAuthModels;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.FeatureLabService;
import com.zrlog.admin.business.service.WebhookService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.rest.response.StandardResponse;
import com.zrlog.model.WebSite;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;

public class WebhookControllerDatabaseTest {

    private ZrLogConfig applicationConfig;

    private static HttpRequest request(HttpMethod httpMethod, Map<String, String> headers,
                                       Map<String, String> params, String body) {
        return (HttpRequest) Proxy.newProxyInstance(
                WebhookControllerDatabaseTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getMethod":
                            return httpMethod;
                        case "getInputStream":
                            return body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                        case "getParaToStr":
                            if (args.length == 2) {
                                return params.getOrDefault(args[0].toString(), args[1].toString());
                            }
                            return params.get(args[0].toString());
                        case "getHeader":
                            if (headers.containsKey(args[0].toString())) {
                                return headers.get(args[0].toString());
                            }
                            if ("Host".equals(args[0])) {
                                return "localhost:18080";
                            }
                            return "User-Agent".equals(args[0]) ? "JUnit" : null;
                        case "getHeaderMap":
                            return Map.of("X-Real-IP", "127.0.0.1");
                        case "getRemoteHost":
                            return "127.0.0.1";
                        case "getContextPath":
                            return "/";
                        case "getUri":
                            return "/api/admin/webhook";
                        case "toString":
                            return "HttpRequestProxy";
                        default:
                            if (method.getReturnType().isPrimitive()) {
                                return 0;
                            }
                            return null;
                    }
                });
    }

    private static HttpResponse response() {
        return (HttpResponse) Proxy.newProxyInstance(
                WebhookControllerDatabaseTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReadAndUpdateWebhookConfigThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            AdminPageDataResponse<WebhookConfigResponse> initial =
                    (AdminPageDataResponse<WebhookConfigResponse>) controller(HttpMethod.GET, Map.of(), Map.of(), null, db.dataSource())
                            .config();
            StandardResponse updatedResponse =
                    controller(HttpMethod.POST, Map.of(), Map.of(), "{\"enabled\":true}", db.dataSource()).config();
            ApiStandardResponse<WebhookConfigResponse> updated =
                    (ApiStandardResponse<WebhookConfigResponse>) updatedResponse;

            assertFalse(initial.getData().getEnabled());
            assertFalse(initial.getData().getHasToken());
            assertTrue(updated.getData().getEnabled());
            assertFalse(updated.getData().getHasToken());
            assertTrue(String.valueOf(db.queryOne("select value from website where name=?",
                            "admin_cache:webhook_config")
                    .get("value")).contains("\"enabled\":true"));
            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("UPDATE_WEBHOOK_CONFIG"));
        }
    }

    @Test
    public void shouldRotateAcceptAndRevokeWebhookTokenThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            ApiStandardResponse<WebhookTokenResponse> tokenResponse =
                    controller(HttpMethod.POST, Map.of(), Map.of(), null, db.dataSource()).token();
            String token = tokenResponse.getData().getToken();

            ApiStandardResponse<WebhookMessageNoticeCreateResponse> created =
                    controller(HttpMethod.POST, Map.of("Authorization", "Bearer " + token), Map.of(),
                            "{\"taskKey\":\"deploy\",\"title\":\"Deploy <b>Done</b>\","
                                    + "\"description\":\"Finished\",\"updatedAt\":99,"
                                    + "\"payload\":{\"env\":\"test\"}}", db.dataSource())
                            .messageCenterNotice();
            ApiStandardResponse<WebhookConfigResponse> revoked =
                    controller(HttpMethod.POST, Map.of(), Map.of(), null, db.dataSource()).revokeToken();

            assertEquals(43, token.length());
            assertTrue(tokenResponse.getData().getConfig().getHasToken());
            assertEquals("server.webhook.message.deploy", created.getData().getTaskKey());
            assertEquals(Long.valueOf(99L), created.getData().getUpdatedAt());
            assertFalse(revoked.getData().getEnabled());
            assertFalse(revoked.getData().getHasToken());
            String notices = String.valueOf(db.queryOne("select value from website where name=?",
                    "admin_cache:webhook_message_center_notices").get("value"));
            assertTrue(notices.contains("Deploy Done"));
            assertTrue(notices.contains("\"env\":\"test\""));
            assertTrue(String.valueOf(db.queryOne("select value from website where name=?",
                    "admin_cache:message_center_status").get("value")).contains("\"hasUnread\":true"));
        }
    }

    @Test
    public void shouldRejectWebhookActionsWithWrongMethodOrToken() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            assertThrows(ArgsException.class,
                    () -> controller(HttpMethod.GET, Map.of(), Map.of(), null, ignored.dataSource()).token());
            assertThrows(PermissionErrorException.class,
                    () -> controller(HttpMethod.POST, Map.of(WebhookService.TOKEN_HEADER, "bad-token"), Map.of(),
                            "{\"title\":\"Deploy\"}", ignored.dataSource()).messageCenterNotice());
        }
    }

    @Test
    public void personalNotificationsRequireTheSharedPermissionAndTheActualReceivingSwitch() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            var config = new WebhookConfigRequest();
            config.setEnabled(true);
            new WebhookService(
                    ApplicationServicesFixture.messages(db.dataSource()),
                    ApplicationServicesFixture.websiteCache(db.dataSource()))
                    .updateConfig(config);
            var identity = new OAuthModels.Identity();
            identity.permissionMode = "custom";
            identity.permissions = java.util.List.of("notification.create");
            DelegatedAccess.withPermissions(
                    java.util.Set.copyOf(identity.permissions),
                    "inherit".equals(identity.permissionMode),
                    () -> {
                        assertEquals(
                                "server.webhook.message.deployed",
                                controller(
                                        HttpMethod.POST,
                                        Map.of(),
                                        Map.of(),
                                        "{\"title\":\"Deployed\",\"taskKey\":\"deployed\"}", db.dataSource())
                                        .messageCenterNotice()
                                        .getData()
                                        .getTaskKey());
                        config.setEnabled(false);
                        new WebhookService(
                                ApplicationServicesFixture.messages(db.dataSource()),
                                ApplicationServicesFixture.websiteCache(db.dataSource()))
                                .updateConfig(config);
                        assertThrows(
                                PermissionErrorException.class,
                                () ->
                                        controller(
                                                HttpMethod.POST,
                                                Map.of(),
                                                Map.of(),
                                                "{\"title\":\"Blocked\"}", db.dataSource())
                                                .messageCenterNotice());
                        return null;
                    });
            assertFalse(
                    new FeatureLabService(
                            () -> true,
                            new WebhookService(
                                    ApplicationServicesFixture.messages(db.dataSource()),
                                    ApplicationServicesFixture.websiteCache(db.dataSource())),
                            () -> new WebSite(db.dataSource()))
                            .featureLab()
                            .getFeature_webhook_enabled());
            config.setEnabled(true);
            new WebhookService(
                    ApplicationServicesFixture.messages(db.dataSource()),
                    ApplicationServicesFixture.websiteCache(db.dataSource()))
                    .updateConfig(config);
            identity.permissions = java.util.List.of("article.read");
            DelegatedAccess.withPermissions(
                    java.util.Set.copyOf(identity.permissions),
                    "inherit".equals(identity.permissionMode),
                    () -> {
                        assertThrows(
                                PermissionErrorException.class,
                                () ->
                                        controller(
                                                HttpMethod.POST,
                                                Map.of(),
                                                Map.of(),
                                                "{\"title\":\"Blocked\"}", db.dataSource())
                                                .messageCenterNotice());
                        return null;
                    });
        }
    }

    private WebhookController controller(HttpMethod method, Map<String, String> headers,
                                         Map<String, String> params, String body, DataSourceWrapper testDataSource) throws Exception {
        WebhookController controller =
                new WebhookController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(testDataSource)),
                        AccountServicesFixture.component(testDataSource).permissions(), AccountServicesFixture.component(testDataSource).requestIdentity(),
                        new WebhookService(
                                ApplicationServicesFixture.messages(testDataSource),
                                ApplicationServicesFixture.websiteCache(testDataSource)),
                        ApplicationServicesFixture.audit(testDataSource));
        Field requestField = Controller.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(controller, request(method, headers, params, body));
        Field responseField = Controller.class.getDeclaredField("response");
        responseField.setAccessible(true);
        responseField.set(controller, response());
        return controller;
    }
}
