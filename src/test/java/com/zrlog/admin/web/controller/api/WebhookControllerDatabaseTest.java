package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.WebhookConfigResponse;
import com.zrlog.admin.business.rest.response.WebhookMessageNoticeCreateResponse;
import com.zrlog.admin.business.rest.response.WebhookTokenResponse;
import com.zrlog.admin.business.service.WebhookService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.rest.response.StandardResponse;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WebhookControllerDatabaseTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReadAndUpdateWebhookConfigThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AdminPageDataResponse<WebhookConfigResponse> initial =
                    (AdminPageDataResponse<WebhookConfigResponse>) controller(HttpMethod.GET, Map.of(), Map.of(), null)
                            .config();
            StandardResponse updatedResponse =
                    controller(HttpMethod.POST, Map.of(), Map.of(), "{\"enabled\":true}").config();
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
            ApiStandardResponse<WebhookTokenResponse> tokenResponse =
                    controller(HttpMethod.POST, Map.of(), Map.of(), null).token();
            String token = tokenResponse.getData().getToken();

            ApiStandardResponse<WebhookMessageNoticeCreateResponse> created =
                    controller(HttpMethod.POST, Map.of("Authorization", "Bearer " + token), Map.of(),
                            "{\"taskKey\":\"deploy\",\"title\":\"Deploy <b>Done</b>\","
                                    + "\"description\":\"Finished\",\"updatedAt\":99,"
                                    + "\"payload\":{\"env\":\"test\"}}")
                            .messageCenterNotice();
            ApiStandardResponse<WebhookConfigResponse> revoked =
                    controller(HttpMethod.POST, Map.of(), Map.of(), null).revokeToken();

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
            assertThrows(ArgsException.class,
                    () -> controller(HttpMethod.GET, Map.of(), Map.of(), null).token());
            assertThrows(PermissionErrorException.class,
                    () -> controller(HttpMethod.POST, Map.of(WebhookService.TOKEN_HEADER, "bad-token"), Map.of(),
                            "{\"title\":\"Deploy\"}").messageCenterNotice());
        }
    }

    private static WebhookController controller(HttpMethod method, Map<String, String> headers,
                                                Map<String, String> params, String body) throws Exception {
        WebhookController controller = new WebhookController();
        Field requestField = Controller.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(controller, request(method, headers, params, body));
        Field responseField = Controller.class.getDeclaredField("response");
        responseField.setAccessible(true);
        responseField.set(controller, response());
        return controller;
    }

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
}
