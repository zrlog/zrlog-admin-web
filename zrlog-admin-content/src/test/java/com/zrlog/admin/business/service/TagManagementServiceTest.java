package com.zrlog.admin.business.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.zrlog.admin.business.rest.request.TagManageRequest;
import com.zrlog.admin.business.rest.response.TagManagementArticleImpactResponse;
import com.zrlog.admin.business.rest.response.TagManagementEntryResponse;
import com.zrlog.admin.business.rest.response.TagManagementPreviewResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.repository.LogRepository;
import com.zrlog.data.repository.TagRepository;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TagManagementServiceTest {
    private static ArticleVersionService versions(InMemoryZrLogDatabase db) {
        return new ArticleVersionService(
                new AccountPermissionService(() -> new LogRepository(db.dataSource())),
                () -> new LogRepository(db.dataSource()),
                () -> new com.zrlog.admin.data.repository.LogVersionRepository(db.dataSource()));
    }

    private static TagManagementService service(InMemoryZrLogDatabase db) {
        return new TagManagementService(
                () -> new LogRepository(db.dataSource()),
                () -> new TagRepository(db.dataSource()),
                versions(db));
    }

    private static void insertArticle(
            InMemoryZrLogDatabase db, int id, String title, String keywords) throws Exception {
        db.execute(
                "insert into log(logId,alias,title,content,plain_content,markdown,keywords,typeId,userId,"
                        + "rubbish,privacy,canComment,recommended,releaseTime,last_update_date,version) "
                        + "values(?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
                id,
                "article-" + id,
                title,
                title + " content",
                title + " content",
                title + " markdown",
                keywords,
                1,
                1,
                false,
                false,
                true,
                false,
                0);
    }

    @Test
    public void shouldParseTagsWithTrimAndStableDeduplication() throws Exception {
        TagManagementService service =
                new TagManagementService(
                        () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        },
                        () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        },
                        null);

        assertEquals(List.of(), service.parseTags(null));
        assertEquals(
                List.of("java", "zrlog", "blog"), service.parseTags(" java, zrlog,java,, blog "));
    }

    @Test
    public void shouldReplaceMergeOrRemoveTagsWithoutDuplicates() throws Exception {
        TagManagementService service =
                new TagManagementService(
                        () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        },
                        () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        },
                        null);

        List<String> merged =
                service.replaceTag(List.of("java", "blog", "zrlog"), "java", "blog", false);
        List<String> renamed =
                service.replaceTag(List.of("java", "blog", "zrlog"), "java", "server", false);
        List<String> removed =
                service.replaceTag(List.of("java", "blog", "zrlog"), "blog", "", true);

        assertEquals(List.of("blog", "zrlog"), merged);
        assertEquals(List.of("server", "blog", "zrlog"), renamed);
        assertEquals(List.of("java", "zrlog"), removed);
        assertEquals("server,blog,zrlog", service.joinTags(renamed));
    }

    @Test
    public void shouldBuildArticleImpactResponseFromProjection() throws Exception {
        TagManagementService service =
                new TagManagementService(
                        () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        },
                        () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        },
                        null);

        com.zrlog.data.dto.ArticleBasicDTO article = new com.zrlog.data.dto.ArticleBasicDTO();
        article.setLogId(7L);
        article.setTitle("Article");
        TagManagementArticleImpactResponse response =
                service.toImpact(article, "java,zrlog", "blog,zrlog");
        TagManagementArticleImpactResponse missing =
                service.toImpact(new com.zrlog.data.dto.ArticleBasicDTO(), "java", "");

        assertEquals(Long.valueOf(7L), response.getId());
        assertEquals("Article", response.getTitle());
        assertEquals("java,zrlog", response.getBeforeKeywords());
        assertEquals("blog,zrlog", response.getAfterKeywords());
        assertEquals(null, missing.getId());
        assertEquals("", missing.getTitle());
    }

    @Test
    public void shouldNormalizeTagValues() throws Exception {
        TagManagementService service =
                new TagManagementService(
                        () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        },
                        () -> {
                            throw new IllegalStateException("Database is unavailable in this test");
                        },
                        null);

        assertEquals("", service.normalizeTag(null));
        assertEquals("java", service.normalizeTag(" java "));
    }

    @Test
    public void shouldPageTagsFromRealTagTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("insert into tag(tagId,text,count) values(?,?,?)", 1, "blog", 1);
            db.execute("insert into tag(tagId,text,count) values(?,?,?)", 2, "java", 3);
            db.execute("insert into tag(tagId,text,count) values(?,?,?)", 3, "zrlog", 2);

            PageData<TagManagementEntryResponse> page =
                    service(db).find("//localhost/", new PageRequestImpl(1L, 2L), "", false);
            PageData<TagManagementEntryResponse> filtered =
                    service(db).find("//localhost/", new PageRequestImpl(1L, 10L), "zr", true);

            assertEquals(3, page.getTotalElements());
            assertEquals(2, page.getRows().size());
            assertEquals("java", page.getRows().get(0).getText());
            assertEquals("zrlog", page.getRows().get(1).getText());
            assertEquals("//localhost/tag/java", page.getRows().get(0).getUrl());
            assertEquals(1, filtered.getTotalElements());
            assertEquals("zrlog", filtered.getRows().get(0).getText());
            assertEquals("//localhost/tag/zrlog.html", filtered.getRows().get(0).getUrl());
        }
    }

    @Test
    public void shouldPreviewAndExecuteTagReplacementAgainstRealLogTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            insertArticle(db, 1, "First", "java,zrlog");
            insertArticle(db, 2, "Second", "java,blog");
            insertArticle(db, 3, "Third", "other");
            TagManagementService service = service(db);
            TagManageRequest request = new TagManageRequest();
            request.setSourceTag("java");
            request.setTargetTag("server");

            TagManagementPreviewResponse preview = service.preview(request, "rename");
            TagManagementPreviewResponse executed =
                    service.execute(
                            com.zrlog.data.security.AccountAccess.from(
                                    new com.zrlog.data.repository.UserRepository(db.dataSource())
                                            .loadById(1L)),
                            request,
                            "rename");
            Map<String, Object> first = db.queryOne("select keywords from log where logId=?", 1);
            Map<String, Object> second = db.queryOne("select keywords from log where logId=?", 2);

            assertEquals(2, preview.getAffectedArticleCount());
            assertEquals(0, preview.getUpdatedArticleCount());
            assertEquals("java,zrlog", preview.getArticles().get(0).getBeforeKeywords());
            assertEquals("server,zrlog", preview.getArticles().get(0).getAfterKeywords());
            assertEquals(2, executed.getAffectedArticleCount());
            assertEquals(2, executed.getUpdatedArticleCount());
            assertEquals("server,zrlog", first.get("keywords"));
            assertEquals(
                    1, ((Number) db.scalar("select version from log where logId=1")).intValue());
            assertEquals(2, ((Number) db.scalar("select count(*) from log_version")).intValue());
            var current = new com.zrlog.admin.business.rest.response.LoadEditArticleResponse();
            current.setLogId(1);
            current.setVersion(1);
            current.setKeywords("server,zrlog");
            assertEquals("java,zrlog", versions(db).loadVersion(1, 0, current).getKeywords());
            assertEquals("server,blog", second.get("keywords"));
            assertEquals(4L, ((Number) db.scalar("select count(1) from tag")).longValue());
            assertEquals(
                    2,
                    ((Number) db.scalar("select count from tag where text=?", "server"))
                            .intValue());
        }
    }

    @Test
    public void concurrentArticleChangesAndMissingAuthorityCannotCreateTagHistory()
            throws Exception {
        try (var db = InMemoryZrLogDatabase.openWebApi()) {
            insertArticle(db, 1, "First", "java,zrlog");
            var service = service(db);
            var request = new TagManageRequest();
            request.setSourceTag("java");
            request.setTargetTag("server");
            assertThrows(
                    com.zrlog.admin.business.exception.PermissionErrorException.class,
                    () -> service.execute(null, request, "rename"));
            var raced = new java.util.concurrent.atomic.AtomicBoolean();
            db.afterWebApiQuery(
                    (sql, args) -> {
                        if (sql.startsWith("select logId,title,keywords,version from log")
                                && raced.compareAndSet(false, true)) {
                            db.execute(
                                    "update log set title='Other writer',version=version+1 where logId=1");
                        }
                    });
            var actor =
                    com.zrlog.data.security.AccountAccess.from(
                            new com.zrlog.data.repository.UserRepository(db.dataSource())
                                    .loadById(1L));
            assertEquals(0, service.execute(actor, request, "rename").getUpdatedArticleCount());
            assertEquals("java,zrlog", db.scalar("select keywords from log where logId=1"));
            assertEquals("Other writer", db.scalar("select title from log where logId=1"));
            assertEquals(
                    1, ((Number) db.scalar("select version from log where logId=1")).intValue());
            assertEquals(0, ((Number) db.scalar("select count(*) from log_version")).intValue());
        }
    }
}
