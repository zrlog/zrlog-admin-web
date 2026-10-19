package com.zrlog.admin.business.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class ArticlePublishingServiceDatabaseTest {
    @Parameterized.Parameters(name = "database={0}")
    public static String[] databases() { return new String[]{"h2", "sqlite"}; }

    private final String database;

    public ArticlePublishingServiceDatabaseTest(String database) { this.database = database; }

    private InMemoryZrLogDatabase open() throws Exception {
        return database.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
    }

    @Test
    public void recordsCreationAndPublicationButNotDraftOrPrivateUpdatesOrVersionConflicts() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            ArticlePublishingService publishing = new ArticlePublishingService(new AdminArticleService(db.cacheService(), com.zrlog.admin.support.ApplicationServicesFixture.capability(com.zrlog.common.Constants.zrLogConfig, com.zrlog.admin.business.content.ArticleAssistant.class), new com.zrlog.admin.business.service.AdminPageDataFactory(com.zrlog.admin.support.ApplicationServicesFixture.site(com.zrlog.common.Constants.zrLogConfig), com.zrlog.admin.support.ApplicationServicesFixture.buildId(com.zrlog.admin.business.AdminConstants.adminResource))), com.zrlog.admin.support.ApplicationServicesFixture.cacheRefresh(db.cacheService(), com.zrlog.common.Constants.zrLogConfig));
            CreateArticleRequest create = new CreateArticleRequest();
            create.setTitle("Original");
            create.setTypeId(1L);
            create.setContent("Original content");
            create.setRubbish(true);
            create.setPreserveDraftAiMessages(true);
            int id = publishing.create(AdminTokenThreadLocal.getUser(), create, request()).getLogId().intValue();
            JsonArray creationAudit = audit(db);
            assertEquals(1, creationAudit.size());
            assertEquals("CREATE_ARTICLE", creationAudit.get(0).getAsJsonObject().get("action").getAsString());
            int publications = 0;
            String[] states = {"draft", "private", "published", "published", "draft"};
            for (int version = 0; version < states.length; version++) {
                UpdateArticleRequest body = new UpdateArticleRequest();
                body.setLogId(id);
                body.setVersion(version);
                body.setTypeId(1L);
                body.setTitle("Saved " + states[version]);
                body.setContent("Saved content " + version);
                body.setRubbish(states[version].equals("draft"));
                body.setPrivacy(states[version].equals("private"));
                String before = audit(db).toString();
                publishing.update(AdminTokenThreadLocal.getUser(), body, request());

                JsonArray audit = audit(db);
                if (states[version].equals("published")) {
                    publications++;
                    JsonObject latest = audit.get(0).getAsJsonObject();
                    assertEquals("PUBLISH_ARTICLE", latest.get("action").getAsString());
                    assertEquals("article", latest.get("type").getAsString());
                    assertEquals(body.getTitle(), latest.get("content").getAsString());
                    assertEquals(1, latest.get("actorUserId").getAsInt());
                    assertEquals("127.0.0.1", latest.get("ip").getAsString());
                } else {
                    assertEquals(before, audit.toString());
                }
                assertEquals(publications + 1, audit.size());
                assertEquals(version + 1, ((Number) db.scalar("select version from log where logId=?", id)).intValue());

                assertThrows(UpdateArticleExpireException.class,
                        () -> publishing.update(AdminTokenThreadLocal.getUser(), body, request()));
                assertEquals(audit.toString(), audit(db).toString());
            }
        }
    }

    @Test
    public void directPublicationRecordsBothCreationAndPublication() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            CreateArticleRequest body = new CreateArticleRequest();
            body.setTitle("Direct publication");
            body.setTypeId(1L);
            body.setContent("Published content");
            body.setPreserveDraftAiMessages(true);
            new ArticlePublishingService(new AdminArticleService(db.cacheService(), com.zrlog.admin.support.ApplicationServicesFixture.capability(com.zrlog.common.Constants.zrLogConfig, com.zrlog.admin.business.content.ArticleAssistant.class), new com.zrlog.admin.business.service.AdminPageDataFactory(com.zrlog.admin.support.ApplicationServicesFixture.site(com.zrlog.common.Constants.zrLogConfig), com.zrlog.admin.support.ApplicationServicesFixture.buildId(com.zrlog.admin.business.AdminConstants.adminResource))), com.zrlog.admin.support.ApplicationServicesFixture.cacheRefresh(db.cacheService(), com.zrlog.common.Constants.zrLogConfig)).create(AdminTokenThreadLocal.getUser(), body, request());
            JsonArray audit = audit(db);
            assertEquals(2, audit.size());
            assertEquals("PUBLISH_ARTICLE", audit.get(0).getAsJsonObject().get("action").getAsString());
            assertEquals("CREATE_ARTICLE", audit.get(1).getAsJsonObject().get("action").getAsString());
            assertEquals("Direct publication", audit.get(0).getAsJsonObject().get("content").getAsString());
        }
    }

    @Test
    public void recordsEachSuccessfulDeletionEvenWhenALaterArticleIsDenied() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            db.execute("insert into user(userId,userName,role) values(2,'other','author')");
            db.execute("update user set role='author' where userId=1");
            for (int id : new int[]{10, 11, 12}) {
                db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy,version) values(?, ?,1,'Article',true,false,0)",
                        id, id == 12 ? 2 : 1);
            }
            ArticlePublishingService publishing = new ArticlePublishingService(new AdminArticleService(db.cacheService(), com.zrlog.admin.support.ApplicationServicesFixture.capability(com.zrlog.common.Constants.zrLogConfig, com.zrlog.admin.business.content.ArticleAssistant.class), new com.zrlog.admin.business.service.AdminPageDataFactory(com.zrlog.admin.support.ApplicationServicesFixture.site(com.zrlog.common.Constants.zrLogConfig), com.zrlog.admin.support.ApplicationServicesFixture.buildId(com.zrlog.admin.business.AdminConstants.adminResource))), com.zrlog.admin.support.ApplicationServicesFixture.cacheRefresh(db.cacheService(), com.zrlog.common.Constants.zrLogConfig));
            assertTrue(publishing.deleteArticles("10", request()).getData().getDelete());
            assertThrows(PermissionErrorException.class, () -> publishing.deleteArticles("11,12", request()));
            assertEquals(1, ((Number) db.scalar("select count(*) from log")).intValue());
            assertEquals(2, ((Number) db.scalar("select userId from log where logId=12")).intValue());
            JsonArray audit = audit(db);
            assertEquals(2, audit.size());
            for (int index = 0; index < audit.size(); index++) {
                JsonObject entry = audit.get(index).getAsJsonObject();
                assertEquals("DELETE_ARTICLE", entry.get("action").getAsString());
                assertEquals(index == 0 ? "11" : "10", entry.get("content").getAsString());
                assertEquals(1, entry.get("actorUserId").getAsInt());
            }
            assertThrows(PermissionErrorException.class, () -> publishing.deleteArticles("12", request()));
            assertEquals(audit.toString(), audit(db).toString());
        }
    }

    private static JsonArray audit(InMemoryZrLogDatabase db) throws Exception {
        return JsonParser.parseString(db.scalar("select value from website where name='admin_audit_log'").toString()).getAsJsonArray();
    }

    private static HttpRequest request() {
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUri": return "/api/admin/article/update";
                        case "getRemoteHost": return "127.0.0.1";
                        case "getHeaderMap": return Map.of();
                        case "getHeader": return null;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
