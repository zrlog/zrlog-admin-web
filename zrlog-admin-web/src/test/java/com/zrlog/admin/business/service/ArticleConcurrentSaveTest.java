package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ArticleConcurrentSaveTest {
    @Test
    public void competingAccountsAllocateDistinctArticleIds() throws Exception {
        for (String backend : List.of("h2", "sqlite", "webapi")) {
            try (InMemoryZrLogDatabase db = open(backend)) {
                db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "other-admin", "admin");
                CyclicBarrier start = new CyclicBarrier(2);
                ExecutorService pool = Executors.newFixedThreadPool(2);
                try {
                    Future<Long> first = pool.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        return new AdminArticleService().create(token(1), article("First")).getLogId();
                    });
                    Future<Long> second = pool.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        return new AdminArticleService().create(token(2), article("Second")).getLogId();
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
                db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "other-admin", "admin");
                AdminArticleService service = new AdminArticleService();
                int id = service.create(token(1), article("Original")).getLogId().intValue();
                CyclicBarrier start = new CyclicBarrier(2);
                ExecutorService pool = Executors.newFixedThreadPool(2);
                try {
                    Future<Boolean> first = pool.submit(() -> save(start, token(1), update(id, 0, "First")));
                    Future<Boolean> second = pool.submit(() -> save(start, token(2), update(id, 0, "Second")));
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
                    service.update(token(2), update(id, 1, "Next"));
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
            AdminArticleService service = new AdminArticleService();
            int id = service.create(token(1), article("Original")).getLogId().intValue();
            AtomicBoolean raced = new AtomicBoolean();
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("update log ") && raced.compareAndSet(false, true)) {
                    // A separate writer commits after authorization/read but before this UPDATE.
                    db.execute("update log set title=?,content=?,version=version+1 where logId=?",
                            "Other writer", "Other body", id);
                }
            });
            assertThrows(UpdateArticleExpireException.class, () -> service.update(token(1), update(id, 0, "Stale")));
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
                AdminArticleService service = new AdminArticleService();
                int id = service.create(token(1), article("Original")).getLogId().intValue();
                for (Integer version : Arrays.asList(null, -1, 1, Integer.MAX_VALUE)) {
                    assertThrows(UpdateArticleExpireException.class, () -> service.update(token(1), update(id, version, "Invalid")));
                }
                assertEquals("Original", db.scalar("select title from log where logId=?", id));
                assertEquals(0, ((Number) db.scalar("select version from log where logId=?", id)).intValue());
                assertEquals(0, ((Number) db.scalar("select count(*) from log_version where log_id=?", id)).intValue());
            }
        }
    }

    private static boolean save(CyclicBarrier start, AdminTokenVO token, UpdateArticleRequest request) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try { new AdminArticleService().update(token, request); return true; }
        catch (UpdateArticleExpireException expected) { return false; }
    }

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
}
