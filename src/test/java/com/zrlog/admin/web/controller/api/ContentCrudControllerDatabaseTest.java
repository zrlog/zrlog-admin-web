package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ContentCrudControllerDatabaseTest {

    @Test
    public void shouldManageTypesThroughRealDatabase() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            typeController(Map.of(), "{\"typeName\":\"News\",\"alias\":\"news\",\"remark\":\"<b>Remark</b>\"}")
                    .add();
            Number typeId = (Number) db.queryOne("select typeId from type where alias=?", "news").get("typeId");

            typeController(Map.of(),
                    "{\"id\":" + typeId + ",\"typeName\":\"Updates\",\"alias\":\"updates\"}")
                    .update();
            AdminPageDataResponse<PageData<TypeDTO>> page = typeController(Map.of(), null).index();

            assertFalse(page.getData().getRows().isEmpty());
            assertEquals("Updates", db.queryOne("select typeName from type where typeId=?", typeId).get("typeName"));
            assertEquals("", db.queryOne("select remark from type where typeId=?", typeId).get("remark"));
            DeleteResponse deleted = typeController(Map.of("id", typeId.toString()), null).delete();
            assertTrue(deleted.getData().getDelete());
            assertNull(db.queryOne("select typeId from type where typeId=?", typeId));
        }
    }

    @Test
    public void shouldManageBlogNavigationThroughRealDatabase() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            blogNavController(Map.of(),
                    "{\"navName\":\"Docs\",\"url\":\"https://example.com/docs\",\"icon\":\"book\",\"sort\":3}")
                    .add();
            Number navId = (Number) db.queryOne("select navId from lognav where navName=?", "Docs").get("navId");

            blogNavController(Map.of(),
                    "{\"id\":" + navId + ",\"navName\":\"Docs Updated\","
                            + "\"url\":\"https://example.com/new-docs\",\"icon\":\"file\"}")
                    .update();
            AdminPageDataResponse<PageData<LogNavDTO>> page = blogNavController(Map.of(), null).index();
            DeleteResponse deleted =
                    blogNavController(Map.of("id", " , " + navId + "," + navId + " , "), null).delete();

            LogNavDTO row = page.getData().getRows().stream()
                    .filter(e -> e.getId().equals(navId.longValue()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("Docs Updated", row.getNavName());
            assertEquals("https://example.com/new-docs", row.getJumpUrl());
            assertEquals(0L, row.getSort().longValue());
            assertTrue(deleted.getData().getDelete());
            assertNull(db.queryOne("select navId from lognav where navId=?", navId));
        }
    }

    @Test
    public void shouldManageLinksThroughRealDatabase() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            linkController(Map.of(),
                    "{\"linkName\":\"ZrLog\",\"url\":\"https://www.zrlog.com\","
                            + "\"alt\":\"<b>Blog</b>\",\"icon\":\"link\",\"sort\":5}")
                    .add();
            Number linkId = (Number) db.queryOne("select linkId from link where linkName=?", "ZrLog")
                    .get("linkId");

            linkController(Map.of(),
                    "{\"id\":" + linkId + ",\"linkName\":\"ZrLog Home\","
                            + "\"url\":\"https://www.zrlog.com/home\",\"icon\":\"home\",\"sort\":1}")
                    .update();
            AdminPageDataResponse<PageData<LinkDTO>> page = linkController(Map.of(), null).index();

            LinkDTO row = page.getData().getRows().stream()
                    .filter(e -> e.getId().equals(linkId.longValue()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("ZrLog Home", row.getLinkName());
            assertEquals("https://www.zrlog.com/home", row.getUrl());
            assertEquals("", db.queryOne("select alt from link where linkId=?", linkId).get("alt"));
            DeleteResponse deleted = linkController(Map.of("id", linkId.toString()), null).delete();
            assertTrue(deleted.getData().getDelete());
            assertNull(db.queryOne("select linkId from link where linkId=?", linkId));
        }
    }

    @Test
    public void shouldRejectInvalidLinkDeleteIdBeforeTouchingDatabase() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            LinkController controller = linkController(Map.of("id", "0"), null);

            assertThrows(ArgsException.class, controller::delete);
        }
    }

    private static TypeController typeController(Map<String, String> params, String body) throws Exception {
        TypeController controller = new TypeController();
        setControllerFields(controller, params, body, "/api/admin/type");
        return controller;
    }

    private static BlogNavController blogNavController(Map<String, String> params, String body) throws Exception {
        BlogNavController controller = new BlogNavController();
        setControllerFields(controller, params, body, "/api/admin/nav");
        return controller;
    }

    private static LinkController linkController(Map<String, String> params, String body) throws Exception {
        LinkController controller = new LinkController();
        setControllerFields(controller, params, body, "/api/admin/link");
        return controller;
    }

    private static void setControllerFields(Controller controller, Map<String, String> params, String body,
                                            String uri) throws Exception {
        Field requestField = Controller.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(controller, request(params, body, uri));
        Field responseField = Controller.class.getDeclaredField("response");
        responseField.setAccessible(true);
        responseField.set(controller, response());
    }

    private static HttpRequest request(Map<String, String> params, String body, String uri) {
        return (HttpRequest) Proxy.newProxyInstance(
                ContentCrudControllerDatabaseTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getParaToStr":
                            if (args.length == 2) {
                                return params.getOrDefault(args[0].toString(), args[1].toString());
                            }
                            return params.get(args[0].toString());
                        case "getParaToInt":
                            String value = params.get(args[0].toString());
                            if (value == null && args.length == 2) {
                                return args[1];
                            }
                            return value == null ? null : Integer.parseInt(value);
                        case "getInputStream":
                            return body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                        case "getUri":
                            return uri;
                        case "getHeader":
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
                        case "getScheme":
                            return "http";
                        case "decodeParamMap":
                            return Map.of();
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
                ContentCrudControllerDatabaseTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }
}
