package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.zrlog.admin.business.rest.request.TagManageRequest;
import com.zrlog.admin.business.rest.response.TagManagementArticleImpactResponse;
import com.zrlog.admin.business.rest.response.TagManagementEntryResponse;
import com.zrlog.admin.business.rest.response.TagManagementPreviewResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TagManagementServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldParseTagsWithTrimAndStableDeduplication() throws Exception {
        TagManagementService service = new TagManagementService();
        Method method = method("parseTags", String.class);

        assertEquals(List.of(), method.invoke(service, new Object[]{null}));
        assertEquals(List.of("java", "zrlog", "blog"), method.invoke(service, " java, zrlog,java,, blog "));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReplaceMergeOrRemoveTagsWithoutDuplicates() throws Exception {
        TagManagementService service = new TagManagementService();
        Method replace = method("replaceTag", List.class, String.class, String.class, boolean.class);
        Method join = method("joinTags", List.class);

        List<String> merged = (List<String>) replace.invoke(service,
                List.of("java", "blog", "zrlog"), "java", "blog", false);
        List<String> renamed = (List<String>) replace.invoke(service,
                List.of("java", "blog", "zrlog"), "java", "server", false);
        List<String> removed = (List<String>) replace.invoke(service,
                List.of("java", "blog", "zrlog"), "blog", "", true);

        assertEquals(List.of("blog", "zrlog"), merged);
        assertEquals(List.of("server", "blog", "zrlog"), renamed);
        assertEquals(List.of("java", "zrlog"), removed);
        assertEquals("server,blog,zrlog", join.invoke(service, renamed));
    }

    @Test
    public void shouldBuildArticleImpactResponseFromRawRow() throws Exception {
        TagManagementService service = new TagManagementService();
        Method method = method("toImpact", Map.class, String.class, String.class);

        TagManagementArticleImpactResponse response = (TagManagementArticleImpactResponse) method.invoke(service,
                Map.of("logId", 7L, "title", "Article"), "java,zrlog", "blog,zrlog");
        TagManagementArticleImpactResponse missing = (TagManagementArticleImpactResponse) method.invoke(service,
                Map.of(), "java", "");

        assertEquals(Long.valueOf(7L), response.getId());
        assertEquals("Article", response.getTitle());
        assertEquals("java,zrlog", response.getBeforeKeywords());
        assertEquals("blog,zrlog", response.getAfterKeywords());
        assertEquals(null, missing.getId());
        assertEquals("", missing.getTitle());
    }

    @Test
    public void shouldNormalizeTagValues() throws Exception {
        TagManagementService service = new TagManagementService();
        Method method = method("normalizeTag", String.class);

        assertEquals("", method.invoke(service, new Object[]{null}));
        assertEquals("java", method.invoke(service, " java "));
    }

    @Test
    public void shouldPageTagsFromRealTagTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("insert into tag(tagId,text,count) values(?,?,?)", 1, "blog", 1);
            db.execute("insert into tag(tagId,text,count) values(?,?,?)", 2, "java", 3);
            db.execute("insert into tag(tagId,text,count) values(?,?,?)", 3, "zrlog", 2);

            PageData<TagManagementEntryResponse> page =
                    new TagManagementService().find("//localhost/", new PageRequestImpl(1L, 2L), "");
            PageData<TagManagementEntryResponse> filtered =
                    new TagManagementService().find("//localhost/", new PageRequestImpl(1L, 10L), "zr");

            assertEquals(3, page.getTotalElements());
            assertEquals(2, page.getRows().size());
            assertEquals("java", page.getRows().get(0).getText());
            assertEquals("zrlog", page.getRows().get(1).getText());
            assertEquals("//localhost/tag/java", page.getRows().get(0).getUrl());
            assertEquals(1, filtered.getTotalElements());
            assertEquals("zrlog", filtered.getRows().get(0).getText());
        }
    }

    @Test
    public void shouldPreviewAndExecuteTagReplacementAgainstRealLogTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            insertArticle(db, 1, "First", "java,zrlog");
            insertArticle(db, 2, "Second", "java,blog");
            insertArticle(db, 3, "Third", "other");
            TagManagementService service = new TagManagementService();
            TagManageRequest request = new TagManageRequest();
            request.setSourceTag("java");
            request.setTargetTag("server");

            TagManagementPreviewResponse preview = service.preview(request, "rename");
            TagManagementPreviewResponse executed = service.execute(request, "rename");
            Map<String, Object> first = db.queryOne("select keywords from log where logId=?", 1);
            Map<String, Object> second = db.queryOne("select keywords from log where logId=?", 2);

            assertEquals(2, preview.getAffectedArticleCount());
            assertEquals(0, preview.getUpdatedArticleCount());
            assertEquals("java,zrlog", preview.getArticles().get(0).getBeforeKeywords());
            assertEquals("server,zrlog", preview.getArticles().get(0).getAfterKeywords());
            assertEquals(2, executed.getAffectedArticleCount());
            assertEquals(2, executed.getUpdatedArticleCount());
            assertEquals("server,zrlog", first.get("keywords"));
            assertEquals("server,blog", second.get("keywords"));
            assertEquals(4L, ((Number) db.scalar("select count(1) from tag")).longValue());
            assertEquals(2, ((Number) db.scalar("select count from tag where text=?", "server")).intValue());
        }
    }

    private static void insertArticle(InMemoryZrLogDatabase db, int id, String title, String keywords) throws Exception {
        db.execute("insert into log(logId,alias,title,content,plain_content,markdown,keywords,typeId,userId,"
                        + "rubbish,privacy,canComment,recommended,releaseTime,last_update_date,version) "
                        + "values(?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
                id, "article-" + id, title, title + " content", title + " content", title + " markdown",
                keywords, 1, 1, false, false, true, false, 0);
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = TagManagementService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
