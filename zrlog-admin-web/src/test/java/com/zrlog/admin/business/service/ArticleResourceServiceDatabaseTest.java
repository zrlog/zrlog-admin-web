package com.zrlog.admin.business.service;

import com.zrlog.admin.business.content.ArticleResourceReferences;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAction;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ArticleResourceServiceDatabaseTest {
    private static final ArticleResourceReferences REFERENCES =
            new ArticleResourceReferences("/blog");

    private static InMemoryZrLogDatabase open(String backend) throws Exception {
        return "webapi".equals(backend)
                ? InMemoryZrLogDatabase.openWebApi()
                : "sqlite".equals(backend)
                ? InMemoryZrLogDatabase.openSqlite()
                : InMemoryZrLogDatabase.open();
    }

    private static void await(CountDownLatch latch) throws SQLException {
        try {
            if (!latch.await(15, TimeUnit.SECONDS))
                throw new SQLException("Timed out waiting for concurrent writer");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException(e);
        }
    }

    private static CreateArticleRequest article() {
        CreateArticleRequest body = new CreateArticleRequest();
        fields(body);
        body.setTitle("Original title");
        body.setContent("<p>Original body<img src=\"/blog/attached/old/body.png\"></p>");
        return body;
    }

    private static UpdateArticleRequest edit(int id) {
        UpdateArticleRequest body = new UpdateArticleRequest();
        fields(body);
        body.setTitle("Edited title");
        body.setContent("<p>Edited body<img src=\"/blog/attached/old/edited.png\"></p>");
        body.setLogId(id);
        body.setVersion(0);
        return body;
    }

    private static void fields(CreateArticleRequest body) {
        body.setTypeId(1L);
        body.setAlias("article-resources");
        body.setPreserveDraftAiMessages(true);
        body.setArrange_plugin("article-arranger");
        body.setPrivacy(false);
        body.setRubbish(false);
        body.setThumbnail("/blog/attached/old/cover.png?v=1#top");
        body.setMarkdown("![image](/blog/attached/old/body.png)");
        body.setDigest("<img src=\"/blog/attached/old/digest.png\">");
    }

    private static AdminTokenVO token() {
        AdminTokenVO user = new AdminTokenVO();
        user.setUserId(1);
        return user;
    }

    @Test
    public void replacementsPreserveArticleStateAndRecordExactlyOneVersionAcrossDatabases()
            throws Exception {
        for (String backend : List.of("h2", "sqlite", "webapi")) {
            try (var db = open(backend)) {
                var graph = db.component(ContentComponent.class);
                int id = graph.articles().create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token()), article()).getLogId().intValue();
                db.execute(
                        "update log set sticky=1,digest=? where logId=?",
                        "<img src=\"/blog/attached/old/digest.png\">",
                        id);
                Map<String, Object> before = db.queryOne("select * from log where logId=?", id);
                var result =
                        graph.resources()
                                .replaceReferences(
                                        AccountServicesFixture.component(db.dataSource()).identities().account(token()),
                                        "/attached/old",
                                        "/attached/new",
                                        true,
                                        REFERENCES);
                assertEquals(1, result.getScannedArticles());
                assertEquals(1, result.getUpdatedArticles());
                assertEquals(4, result.getUpdatedFields());
                Map<String, Object> after = db.queryOne("select * from log where logId=?", id);
                for (String field :
                        List.of(
                                "title",
                                "userId",
                                "typeId",
                                "privacy",
                                "rubbish",
                                "sticky",
                                "arrange_plugin",
                                "alias",
                                "releaseTime")) {
                    assertEquals(field, before.get(field), after.get(field));
                }
                assertEquals("/blog/attached/new/cover.png?v=1#top", after.get("thumbnail"));
                assertTrue(after.get("content").toString().contains("/blog/attached/new/body.png"));
                assertEquals("Original body", after.get("plain_content"));
                assertEquals(1, ((Number) after.get("version")).intValue());
                assertEquals(
                        1,
                        ((Number) db.scalar("select count(*) from log_version where log_id=?", id))
                                .intValue());
                var current = new com.zrlog.admin.business.rest.response.LoadEditArticleResponse();
                current.setLogId(id);
                current.setVersion(1);
                current.setThumbnail(after.get("thumbnail").toString());
                current.setContent(after.get("content").toString());
                current.setMarkdown(after.get("markdown").toString());
                current.setDigest(after.get("digest").toString());
                var historical = graph.versions().loadVersion(id, 0, current);
                assertEquals(before.get("thumbnail"), historical.getThumbnail());
                assertEquals(before.get("content"), historical.getContent());
                assertEquals(before.get("markdown"), historical.getMarkdown());
                assertEquals(before.get("digest"), historical.getDigest());
                var unchanged =
                        graph.resources()
                                .replaceReferences(
                                        AccountServicesFixture.component(db.dataSource()).identities().account(token()),
                                        "/attached/old",
                                        "/attached/new",
                                        true,
                                        REFERENCES);
                assertEquals(0, unchanged.getUpdatedArticles());
                assertEquals(0, unchanged.getUpdatedFields());
                assertThrows(
                        UpdateArticleExpireException.class,
                        () -> graph.articles().update(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token()), edit(id)));
                assertEquals(
                        1,
                        ((Number) db.scalar("select count(*) from log_version where log_id=?", id))
                                .intValue());
            }
        }
    }

    @Test
    public void resourceReplacementWaitsForEditorAndRecomputesFromItsCommittedContent()
            throws Exception {
        try (var db = InMemoryZrLogDatabase.openWebApi()) {
            var graph = db.component(ContentComponent.class);
            int id = graph.articles().create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token()), article()).getLogId().intValue();
            CountDownLatch editorHasLock = new CountDownLatch(1);
            CountDownLatch resourceScanned = new CountDownLatch(1);
            CountDownLatch releaseEditor = new CountDownLatch(1);
            AtomicBoolean paused = new AtomicBoolean();
            db.beforeWebApiUpdate(
                    (sql, args) -> {
                        if (sql.startsWith("update log ") && paused.compareAndSet(false, true)) {
                            editorHasLock.countDown();
                            await(releaseEditor);
                        }
                    });
            db.afterWebApiQuery(
                    (sql, args) -> {
                        if (sql.contains("thumbnail, content, markdown, digest"))
                            resourceScanned.countDown();
                    });
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> editor = pool.submit(() -> graph.articles().update(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token()), edit(id)));
                assertTrue(editorHasLock.await(10, TimeUnit.SECONDS));
                Future<?> replacement =
                        pool.submit(
                                () ->
                                        graph.resources()
                                                .replaceReferences(
                                                        AccountServicesFixture.component(db.dataSource()).identities().account(token()),
                                                        "/attached/old",
                                                        "/attached/new",
                                                        true,
                                                        REFERENCES));
                assertTrue(resourceScanned.await(10, TimeUnit.SECONDS));
                releaseEditor.countDown();
                editor.get(30, TimeUnit.SECONDS);
                replacement.get(30, TimeUnit.SECONDS);
                Map<String, Object> saved = db.queryOne("select * from log where logId=?", id);
                assertEquals("Edited title", saved.get("title"));
                assertEquals(
                        "<p>Edited body<img src=\"/blog/attached/new/edited.png\"></p>",
                        saved.get("content"));
                assertEquals("Edited body", saved.get("plain_content"));
                assertEquals(2, ((Number) saved.get("version")).intValue());
                assertEquals(
                        2,
                        ((Number) db.scalar("select count(*) from log_version where log_id=?", id))
                                .intValue());
            } finally {
                releaseEditor.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void resourceCasFailureDoesNotOverwriteAnotherWriterOrCreateHistory() throws Exception {
        try (var db = InMemoryZrLogDatabase.openWebApi()) {
            var graph = db.component(ContentComponent.class);
            int id = graph.articles().create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token()), article()).getLogId().intValue();
            AtomicBoolean raced = new AtomicBoolean();
            db.beforeWebApiUpdate(
                    (sql, args) -> {
                        if (sql.startsWith("update log ") && raced.compareAndSet(false, true)) {
                            db.execute(
                                    "update log set content=?,version=version+1 where logId=?",
                                    "Other process",
                                    id);
                        }
                    });
            assertThrows(
                    UpdateArticleExpireException.class,
                    () ->
                            graph.resources()
                                    .replaceReferences(
                                            AccountServicesFixture.component(db.dataSource()).identities().account(token()),
                                            "/attached/old",
                                            "/attached/new",
                                            true,
                                            REFERENCES));
            assertTrue(raced.get());
            assertEquals("Other process", db.scalar("select content from log where logId=?", id));
            assertEquals(
                    1,
                    ((Number) db.scalar("select version from log where logId=?", id)).intValue());
            assertEquals(
                    0,
                    ((Number) db.scalar("select count(*) from log_version where log_id=?", id))
                            .intValue());
        }
    }

    @Test
    public void resourceOperationRequiresFileManagementPermissionEvenWithoutController()
            throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var graph = db.component(ContentComponent.class);
            int id = graph.articles().create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token()), article()).getLogId().intValue();
            db.execute("update user set role='author' where userId=1");
            assertThrows(
                    PermissionErrorException.class,
                    () ->
                            graph.resources()
                                    .replaceReferences(
                                            AccountServicesFixture.component(db.dataSource()).identities().account(token()),
                                            "/attached/old",
                                            "/attached/new",
                                            true,
                                            REFERENCES));
            assertEquals(
                    0,
                    ((Number) db.scalar("select version from log where logId=?", id)).intValue());
        }
    }

    @Test
    public void explicitResourceActorOwnsHistoryWithoutBorrowingThreadIdentity() throws Exception {
        for (String backend : List.of("h2", "sqlite")) {
            try (var db = open(backend)) {
                db.execute("insert into user(userId,userName,role) values(2,'resource-manager','admin')");
                var content = db.component(ContentComponent.class);
                var identities = db.component(AccountComponent.class).identities();
                var managerToken = new AdminTokenVO();
                managerToken.setUserId(2);
                var manager = identities.account(managerToken);
                long id = content.articles().create(manager, article()).getLogId();
                assertEquals(1, AdminTokenThreadLocal.getUserId());
                var replaced = content.resources().replaceReferences(
                        manager, "/attached/old", "/attached/new", true, REFERENCES);
                assertEquals(1, replaced.getUpdatedArticles());
                assertEquals(2, ((Number) db.scalar(
                        "select user_id from log_version where log_id=?", id)).intValue());
                AdminTokenThreadLocal.remove();
                var restored = content.resources().replaceReferences(
                        manager, "/attached/new", "/attached/old", true, REFERENCES);
                assertEquals(1, restored.getUpdatedArticles());
                assertEquals(2, ((Number) db.scalar(
                        "select count(*) from log_version where log_id=? and user_id=2", id)).intValue());
                var readOnly = manager.restrictActions(Set.of(AccountAction.ARTICLE_READ.getId()));
                assertThrows(PermissionErrorException.class, () -> content.resources().replaceReferences(
                        readOnly, "/attached/old", "/attached/new", true, REFERENCES));
                db.execute("update user set authVersion=authVersion+1 where userId=2");
                assertThrows(PermissionErrorException.class, () -> content.resources().replaceReferences(
                        manager, "/attached/old", "/attached/new", true, REFERENCES));
                assertEquals(2, ((Number) db.scalar("select version from log where logId=?", id)).intValue());
                assertEquals(2, ((Number) db.scalar("select count(*) from log_version where log_id=?", id)).intValue());
                assertNull(AdminTokenThreadLocal.getUser());
            }
        }
    }

    @Test
    public void revocationDuringResourceWriteLockLeavesContentAndHistoryUnchanged() throws Exception {
        try (var db = InMemoryZrLogDatabase.openWebApi()) {
            var content = db.component(ContentComponent.class);
            var actor = db.component(AccountComponent.class).identities().account(token());
            long id = content.articles().create(actor, article()).getLogId();
            var before = db.queryOne("select thumbnail,content,markdown,digest from log where logId=?", id);
            AtomicBoolean revoked = new AtomicBoolean();
            db.beforeWebApiUpdate((sql, params) -> {
                if (sql.startsWith("insert into website")
                        && Arrays.asList(params).contains("distributed_lock_write_article_" + id)
                        && revoked.compareAndSet(false, true)) {
                    db.execute("update user set authVersion=authVersion+1 where userId=1");
                }
            });
            assertThrows(PermissionErrorException.class, () -> content.resources().replaceReferences(
                    actor, "/attached/old", "/attached/new", true, REFERENCES));
            assertTrue(revoked.get());
            assertEquals(before, db.queryOne("select thumbnail,content,markdown,digest from log where logId=?", id));
            assertEquals(0, ((Number) db.scalar("select version from log where logId=?", id)).intValue());
            assertEquals(0, ((Number) db.scalar("select count(*) from log_version where log_id=?", id)).intValue());
        }
    }
}
