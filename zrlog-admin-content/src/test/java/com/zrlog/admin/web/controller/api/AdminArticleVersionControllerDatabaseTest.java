package com.zrlog.admin.web.controller.api;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.ArticleVersionCompareResponse;
import com.zrlog.admin.business.rest.response.ArticleVersionResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.model.LogVersion;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminArticleVersionControllerDatabaseTest {

    @Test
    public void shouldListAndCompareArticleVersionsThroughRealDatabase() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            insertArticle(db);
            new LogVersion().savePatch(10, 2,
                    "{\"title\":{\"type\":\"value\",\"old\":\"Old Title\"},"
                            + "\"content\":{\"type\":\"value\",\"old\":\"Old content\"}}",
                    1, "Old Title", new Date(1000), 1);

            ApiStandardResponse<List<ArticleVersionResponse>> versions =
                    controller(Map.of("id", "10")).index();
            ApiStandardResponse<ArticleVersionCompareResponse> compare =
                    controller(Map.of("id", "10", "fromVersion", "1", "toVersion", "2")).compare();

            assertEquals(2, versions.getData().size());
            assertEquals(Integer.valueOf(2), versions.getData().get(0).getVersion());
            assertTrue(versions.getData().get(0).getCurrent());
            assertEquals(Integer.valueOf(1), versions.getData().get(1).getVersion());
            assertFalse(versions.getData().get(1).getCurrent());
            assertEquals(Integer.valueOf(1), compare.getData().getFromVersion());
            assertEquals(Integer.valueOf(2), compare.getData().getToVersion());
            assertEquals("Old Title", compare.getData().getFromArticle().getTitle());
            assertEquals("New Title", compare.getData().getToArticle().getTitle());
            assertTrue(compare.getData().getChangedFields().contains("title"));
            assertTrue(compare.getData().getChangedFields().contains("content"));
        }
    }

    private static void insertArticle(InMemoryZrLogDatabase db) throws Exception {
        db.execute("insert into log(logId, alias, canComment, version, content, plain_content, markdown, digest,"
                        + " keywords, recommended, releaseTime, last_update_date, title, typeId, userId, hot,"
                        + " rubbish, privacy, editor_type) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(),"
                        + " ?, ?, ?, ?, ?, ?, ?)",
                10,
                "new-title",
                true,
                2,
                "New content",
                "New content",
                "New markdown",
                "Digest",
                "zrlog,java",
                false,
                "New Title",
                1,
                1,
                false,
                false,
                false,
                "markdown");
    }

    private static AdminArticleVersionController controller(Map<String, String> params) throws Exception {
        AdminArticleVersionController controller = new AdminArticleVersionController();
        Field requestField = Controller.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(controller, request(params));
        Field responseField = Controller.class.getDeclaredField("response");
        responseField.setAccessible(true);
        responseField.set(controller, response());
        return controller;
    }

    private static HttpRequest request(Map<String, String> params) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminArticleVersionControllerDatabaseTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getParaToStr":
                            if (args.length == 2) {
                                return params.getOrDefault(args[0].toString(), args[1].toString());
                            }
                            return params.get(args[0].toString());
                        case "getUri":
                            return "/api/admin/article/version";
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
                AdminArticleVersionControllerDatabaseTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }
}
