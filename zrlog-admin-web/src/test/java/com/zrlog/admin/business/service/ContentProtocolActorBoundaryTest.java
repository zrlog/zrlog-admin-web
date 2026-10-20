package com.zrlog.admin.business.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.ai.model.AIChatModels;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.knowledge.ContentToolModels;
import com.zrlog.admin.business.knowledge.KnowledgeModels;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.ai.AIComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Real generated graphs must preserve the caller's identity outside request threads.
 */
@RunWith(Parameterized.class)
public class ContentProtocolActorBoundaryTest {
    private final boolean sqlite;
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    public ContentProtocolActorBoundaryTest(boolean sqlite) {
        this.sqlite = sqlite;
    }

    @Parameterized.Parameters(name = "sqlite={0}")
    public static Boolean[] databases() {
        return new Boolean[]{false, true};
    }

    private static AdminTokenVO credential(int userId) {
        var token = new AdminTokenVO();
        token.setUserId(userId);
        return token;
    }

    private static HttpRequest request() {
        return (HttpRequest) Proxy.newProxyInstance(ContentProtocolActorBoundaryTest.class.getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getContextPath")) return "/sub";
                    if (method.getName().equals("getUri")) return "/mcp";
                    if (method.getName().equals("getRemoteHost")) return "192.0.2.10";
                    return null;
                });
    }

    @Test
    public void toolsAndKnowledgeKeepExplicitOwnershipAuditAndDelegationAcrossThreads() throws Exception {
        try (var db = database()) {
            var actor = AccessActorBoundaryTest.actor(db, 2);
            var factory = db.component(ContentComponent.class).tools();
            var source = new AdminAuditContext(2, "192.0.2.10", "/mcp", "client/1");
            var tools = factory.create(actor, actor.scopes(), request(), "en_US", source, credential(2));
            var knowledge = factory.knowledge(actor, actor.scopes(), "en_US");
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            var other = new JsonObject();
            other.addProperty("id", 10);
            assertThrows(PermissionErrorException.class, () -> tools.call("get_article", other));
            AdminTokenThreadLocal.remove();
            var write = JsonParser.parseString("{\"title\":\"Explicit writer\",\"typeId\":1,\"status\":\"draft\",\"content\":\"<p>Draft</p>\"}").getAsJsonObject();
            // A different thread and ambient delegation cannot replace the explicit actor.
            var saved = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return DelegatedAccess.withPermissions(Set.of("article.read"), false,
                            () -> (ContentToolModels.SavedArticle) tools.call("create_article", write));
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }).get();
            assertEquals(2, ((Number) db.scalar("select userId from log where logId=?", saved.id)).intValue());
            var hits = (KnowledgeModels.SearchResult) knowledge.call("search_articles", new JsonObject());
            assertEquals(2, hits.articles.size());
            assertTrue(hits.articles.stream().noneMatch(hit -> hit.id == 10));
            var audit = new AdminAuditService(db.component(com.zrlog.business.di.ServiceComponent.class).websiteKv()).getRecentLogs();
            assertEquals(2, audit.get(0).getActorUserId().intValue());
            assertEquals("MCP", audit.get(0).getBrowser());
            assertEquals("192.0.2.10", audit.get(0).getIp());
            var readOnly = factory.create(actor.restrictActions(Set.of("article.read")), actor.scopes(), request(), "en_US", source, credential(2));
            assertThrows(PermissionErrorException.class, () -> readOnly.call("create_article", write));
            db.execute("update user set authVersion=1 where userId=2");
            assertThrows(PermissionErrorException.class, () -> tools.definitions("en_US"));
            assertThrows(PermissionErrorException.class, () -> tools.call("create_article", write));
            assertThrows(PermissionErrorException.class, () -> knowledge.call("search_articles", new JsonObject()));
            assertEquals(2, ((Number) db.scalar("select count(*) from log where userId=2")).intValue());
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void attachmentToolUsesExplicitAccountDirectoryAndRejectsMismatchedTransport() throws Exception {
        String previous = System.getProperty("sws.root.path");
        try {
            System.setProperty("sws.root.path", temporary.newFolder("application").getAbsolutePath());
            try (var db = database()) {
                var actor = AccessActorBoundaryTest.actor(db, 2);
                var factory = db.component(ContentComponent.class).tools();
                var args = JsonParser.parseString("{\"filename\":\"note.txt\",\"data\":\"aGVsbG8=\"}").getAsJsonObject();
                var wrong = factory.create(actor, actor.scopes(), request(), "en_US", null, credential(1));
                assertThrows(PermissionErrorException.class, () -> wrong.call("upload_attachment", args));
                var transport = credential(2);
                var tools = factory.create(actor, actor.scopes(), request(), "en_US", null, transport);
                transport.setUserId(1);
                transport.setAuthVersion(99);
                AdminTokenThreadLocal.remove();
                var saved = (ContentToolModels.Attachment) tools.call("upload_attachment", args);
                assertTrue(saved.url, saved.url.startsWith("/sub/attached/users/2/"));
                assertEquals("hello", Files.readString(PathUtil.getStaticFile(saved.url.substring(4)).toPath(), StandardCharsets.UTF_8));
                var limited = factory.create(actor.restrictActions(Set.of("article.read")), actor.scopes(), request(), "en_US", null, credential(2));
                assertThrows(PermissionErrorException.class, () -> limited.call("upload_attachment", args));
                db.execute("update user set enabled=false where userId=2");
                assertThrows(PermissionErrorException.class, () -> tools.call("upload_attachment", args));
                assertNull(AdminTokenThreadLocal.getUser());
            }
        } finally {
            if (previous == null) System.clearProperty("sws.root.path");
            else System.setProperty("sws.root.path", previous);
        }
    }

    @Test
    public void referencesUseExplicitPreferencesAndRejectRevokedOrRestrictedActors() throws Exception {
        try (var db = database()) {
            var actor = AccessActorBoundaryTest.actor(db, 2);
            var references = db.component(AIComponent.class).references();
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            var result = references.search(actor, "");
            assertEquals(1, result.articles.size());
            assertEquals(20, result.articles.get(0).id);
            AdminTokenThreadLocal.remove();
            var settings = db.component(AccountComponent.class).preferences().assistant(actor);
            var input = new AIChatModels.ChatRequest();
            input.input = "Read my article";
            var reference = new AIChatModels.Reference();
            reference.type = "article";
            reference.articleId = 20L;
            input.references = List.of(reference);
            assertEquals(20, references.resolve(actor, input, settings).get(0).article.source.id);
            reference.articleId = 10L;
            assertThrows(IllegalArgumentException.class, () -> references.resolve(actor, input, settings));
            var assistOnly = actor.restrictActions(Set.of("article.assist"));
            assertTrue(references.search(assistOnly, "").articles.isEmpty());
            var readOnly = actor.restrictActions(Set.of("article.read"));
            assertThrows(PermissionErrorException.class, () -> references.search(readOnly, ""));
            db.execute("update user set role='editor' where userId=2");
            assertThrows(PermissionErrorException.class, () -> references.search(actor, ""));
            input.references = List.of();
            assertThrows(PermissionErrorException.class, () -> references.resolve(actor, input, settings));
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    private InMemoryZrLogDatabase database() throws Exception {
        var db = sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
        try {
            db.execute("insert into user(userId,userName,role,preferences) values(2,'writer','author',?)", "{\"assistant\":{\"knowledgeScope\":\"own_all\"}}");
            db.execute("insert into log(logId,userId,typeId,title,markdown,privacy,rubbish) values(10,1,1,'Private','Owner secret',true,true),(20,2,1,'Author draft','Writer text',false,true)");
            db.loadAdminWebModules();
            return db;
        } catch (Exception | Error error) {
            db.close();
            throw error;
        }
    }
}
