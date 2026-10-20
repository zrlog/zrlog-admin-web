package com.zrlog.admin.business.service;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ArticleConcurrentSaveTest {

    private ZrLogConfig applicationConfig;

    private static InMemoryZrLogDatabase open(String backend) throws Exception {
        return "webapi".equals(backend) ? InMemoryZrLogDatabase.openWebApi()
                : "sqlite".equals(backend) ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
    }

    private static CreateArticleRequest article(String title) {
        CreateArticleRequest request = new CreateArticleRequest();
        fields(request, title);
        request.setPreserveDraftAiMessages(true);
        return request;
    }

    private static UpdateArticleRequest update(int id, Integer version, String title) {
        UpdateArticleRequest request = new UpdateArticleRequest();
        fields(request, title);
        request.setLogId(id);
        request.setVersion(version);
        return request;
    }

    private static void fields(CreateArticleRequest request, String title) {
        request.setTitle(title);
        request.setContent(title + " body");
        request.setTypeId(1L);
        request.setPrivacy(true);
        request.setRubbish(true);
    }

    private static AdminTokenVO token(int userId) {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(userId);
        token.setSessionId("session-" + userId);
        return token;
    }

    @Test
    public void revocationWhileAcquiringTheWriteLockStopsEveryArticleMutation() throws Exception {
        for (String operation : List.of("create", "update", "delete")) {
            try (var db = InMemoryZrLogDatabase.openWebApi()) {
                db.loadAdminWebModules();
                var actor = db.component(AccountComponent.class).identities().account(token(1));
                var writer = db.component(ContentComponent.class).articles();
                long id = writer.create(actor, article("Original")).getLogId();
                String lockKey = "distributed_lock_write_article_"
                        + (operation.equals("create") ? "create" : id);
                AtomicBoolean revoked = new AtomicBoolean();
                db.beforeWebApiUpdate((sql, params) -> {
                    if (sql.startsWith("insert into website") && Arrays.asList(params).contains(lockKey)
                            && revoked.compareAndSet(false, true)) {
                        db.execute("update user set authVersion=authVersion+1 where userId=1");
                    }
                });
                assertThrows(operation, PermissionErrorException.class, () -> {
                    switch (operation) {
                        case "create":
                            writer.create(actor, article("Too late"));
                            break;
                        case "update":
                            writer.update(actor, update((int) id, 0, "Too late"));
                            break;
                        case "delete":
                            writer.delete(actor, id);
                            break;
                        default:
                            throw new AssertionError(operation);
                    }
                });
                assertTrue("The revocation must happen during lock acquisition", revoked.get());
                assertEquals(1, ((Number) db.scalar("select count(*) from log")).intValue());
                assertEquals("Original", db.scalar("select title from log where logId=?", id));
                assertEquals(0, ((Number) db.scalar("select version from log where logId=?", id)).intValue());
                assertEquals(0, ((Number) db.scalar("select count(*) from log_version")).intValue());
            }
        }
    }

    @Test
    public void competingAccountsAllocateDistinctArticleIds() throws Exception {
        for (String backend : List.of("h2", "sqlite", "webapi")) {
            try (InMemoryZrLogDatabase db = open(backend)) {
                db.loadAdminWebModules();
                applicationConfig = db.config();
                db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "other-admin", "admin");
                CyclicBarrier start = new CyclicBarrier(2);
                ExecutorService pool = Executors.newFixedThreadPool(2);
                try {
                    Future<Long> first =
                            pool.submit(
                                    () -> {
                                        start.await(10, TimeUnit.SECONDS);
                                        return db.component(ContentComponent.class).articles()
                                                .create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token(1)), article("First"))
                                                .getLogId();
                                    });
                    Future<Long> second =
                            pool.submit(
                                    () -> {
                                        start.await(10, TimeUnit.SECONDS);
                                        return db.component(ContentComponent.class).articles()
                                                .create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token(2)), article("Second"))
                                                .getLogId();
                                    });
                    Long firstId = first.get(30, TimeUnit.SECONDS);
                    Long secondId = second.get(30, TimeUnit.SECONDS);
                    assertNotEquals(backend, firstId, secondId);
                    assertEquals(1, ((Number) db.scalar("select userId from log where logId=?", firstId)).intValue());
                    assertEquals(2, ((Number) db.scalar("select userId from log where logId=?", secondId)).intValue());
                } finally {
                    pool.shutdownNow();
                    assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
                }
            }
        }
    }

    @Test
    public void competingAccountsSaveOnlyOneSnapshotAndOneHistoryEntry() throws Exception {
        for (String backend : List.of("h2", "sqlite", "webapi")) {
            try (InMemoryZrLogDatabase db = open(backend)) {
                db.loadAdminWebModules();
                applicationConfig = db.config();
                db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "other-admin", "admin");
                AdminArticleService service =
                        db.component(ContentComponent.class).articles();
                int id = service.create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token(1)), article("Original")).getLogId().intValue();
                CyclicBarrier start = new CyclicBarrier(2);
                ExecutorService pool = Executors.newFixedThreadPool(2);
                try {
                    Future<Boolean> first = pool.submit(() -> save(start, token(1), update(id, 0, "First"), db.dataSource()));
                    Future<Boolean> second = pool.submit(() -> save(start, token(2), update(id, 0, "Second"), db.dataSource()));
                    boolean firstWon = first.get(30, TimeUnit.SECONDS);
                    boolean secondWon = second.get(30, TimeUnit.SECONDS);
                    assertNotEquals(backend, firstWon, secondWon);
                    Map<String, Object> saved = db.queryOne("select title,content,version,userId from log where logId=?", id);
                    assertEquals(firstWon ? "First" : "Second", saved.get("title"));
                    assertEquals(firstWon ? "First body" : "Second body", saved.get("content"));
                    assertEquals(1, ((Number) saved.get("version")).intValue());
                    assertEquals(1, ((Number) saved.get("userId")).intValue());
                    assertEquals(1, ((Number) db.scalar("select count(*) from log_version where log_id=?", id)).intValue());
                    Map<String, Object> patch = db.queryOne("select * from log_version where log_id=?", id);
                    assertEquals(firstWon ? 1 : 2, ((Number) patch.get("user_id")).intValue());
                    assertEquals(0, ((Number) patch.get("from_version")).intValue());
                    assertEquals(1, ((Number) patch.get("article_version")).intValue());
                    assertTrue(patch.get("patch_json").toString().contains("Original"));
                    service.update(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token(2)), update(id, 1, "Next"));
                    assertEquals(2, ((Number) db.scalar("select version from log where logId=?", id)).intValue());
                    assertEquals(2, ((Number) db.scalar("select count(*) from log_version where log_id=?", id)).intValue());
                } finally {
                    pool.shutdownNow();
                    assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
                }
            }
        }
    }

    @Test
    public void versionChangedAfterReadCannotBeOverwrittenOrRecordedInHistory() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            db.loadAdminWebModules();
            applicationConfig = db.config();
            AdminArticleService service =
                    db.component(ContentComponent.class).articles();
            int id = service.create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token(1)), article("Original")).getLogId().intValue();
            AtomicBoolean raced = new AtomicBoolean();
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("update log ") && raced.compareAndSet(false, true)) {
                    // A separate writer commits after authorization/read but before this UPDATE.
                    db.execute("update log set title=?,content=?,version=version+1 where logId=?",
                            "Other writer", "Other body", id);
                }
            });
            assertThrows(UpdateArticleExpireException.class, () -> service.update(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token(1)), update(id, 0, "Stale")));
            assertTrue(raced.get());
            assertEquals("Other writer", db.scalar("select title from log where logId=?", id));
            assertEquals("Other body", db.scalar("select content from log where logId=?", id));
            assertEquals(1, ((Number) db.scalar("select version from log where logId=?", id)).intValue());
            assertEquals(0, ((Number) db.scalar("select count(*) from log_version where log_id=?", id)).intValue());
        }
    }

    @Test
    public void missingStaleAndFutureVersionsCannotChangeContentOrHistory() throws Exception {
        for (String backend : List.of("h2", "sqlite", "webapi")) {
            try (InMemoryZrLogDatabase db = open(backend)) {
                db.loadAdminWebModules();
                applicationConfig = db.config();
                AdminArticleService service =
                        db.component(ContentComponent.class).articles();
                int id = service.create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token(1)), article("Original")).getLogId().intValue();
                for (Integer version : Arrays.asList(null, -1, 1, Integer.MAX_VALUE)) {
                    assertThrows(UpdateArticleExpireException.class, () -> service.update(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(token(1)), update(id, version, "Invalid")));
                }
                assertEquals("Original", db.scalar("select title from log where logId=?", id));
                assertEquals(0, ((Number) db.scalar("select version from log where logId=?", id)).intValue());
                assertEquals(0, ((Number) db.scalar("select count(*) from log_version where log_id=?", id)).intValue());
            }
        }
    }

    private boolean save(CyclicBarrier start, AdminTokenVO token, UpdateArticleRequest request, DataSourceWrapper testDataSource) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            applicationConfig.getServerConfig().getBindings().build().get(ContentComponent.class).articles()
                    .update(AccountServicesFixture.component(testDataSource).requestIdentity().account(token), request);
            return true;
        } catch (UpdateArticleExpireException expected) {
            return false;
        }
    }
}
