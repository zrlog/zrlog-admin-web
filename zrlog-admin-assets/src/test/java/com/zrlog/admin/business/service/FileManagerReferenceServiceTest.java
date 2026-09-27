package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.rest.response.FileReferenceIndexCacheVO;
import com.zrlog.admin.business.rest.response.FileReferenceVO;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.support.TestLogCapture;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FileManagerReferenceServiceTest {

    @Test
    public void shouldReturnFalseWhenReferenceIndexCacheWriteFails() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService() {
            @Override
            public boolean putJson(String key, Object value) {
                return false;
            }
        });

        try (TestLogCapture logs = TestLogCapture.forClass(FileManagerReferenceService.class)) {
            assertFalse(service.writeReferenceIndexCache(new FileReferenceIndexCacheVO()));
            assertTrue(logs.contains(Level.WARNING, "Write file-manager reference index cache failed"));
        }
    }

    @Test
    public void shouldReturnFalseWhenReferenceIndexCacheClearFails() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService() {
            @Override
            public boolean remove(String key) {
                return false;
            }
        });

        try (TestLogCapture logs = TestLogCapture.forClass(FileManagerReferenceService.class)) {
            assertFalse(service.clearReferenceIndexCache());
            assertTrue(logs.contains(Level.WARNING, "Clear file-manager reference index cache failed"));
        }
    }

    @Test
    public void shouldReturnTrueWhenReferenceIndexCacheWriteSucceeds() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService() {
            @Override
            public boolean putJson(String key, Object value) {
                return true;
            }
        });

        assertTrue(service.writeReferenceIndexCache(new FileReferenceIndexCacheVO()));
    }

    @Test
    public void shouldNormalizeContextPathLocalResource() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");

        assertEquals("/attached/image/a.png", service.normalizeLocalResourcePath("/blog/attached/image/a.png?v=1"));
        assertNull(service.normalizeLocalResourcePath("https://cdn.example.com/attached/image/a.png"));
    }

    @Test
    public void shouldReplaceContextPathLocalResourceReference() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("markdown", "![cover](/blog/attached/image/a.png?v=1)");

        Map<String, Object> updates = service.buildArticleResourceUrlUpdates(
                article, "/attached/image/a.png", "/attached/image/b.png", false);

        assertEquals("![cover](/blog/attached/image/b.png?v=1)", updates.get("markdown"));
    }

    @Test
    public void shouldNormalizePathsByRemovingQueryAndHash() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");

        assertEquals("/attached/a.png", service.normalizePath("/attached/a.png?x=1#top"));
        assertEquals("/attached/a.png", service.normalizePath("/attached/a.png#top?x=1"));
    }

    @Test
    public void shouldApplyReferenceInfoToFilesAndDirectories() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");
        FileReferenceVO first = reference(1, true, false);
        FileReferenceVO second = reference(2, false, true);
        Map<String, List<FileReferenceVO>> references = new LinkedHashMap<>();
        references.put("/attached/images/a.png", List.of(first));
        references.put("/attached/images/nested/b.png", List.of(second));
        List<FileEntryVO> entries = List.of(
                new FileEntryVO("images", "/attached/images", "directory", 0, "", 0),
                new FileEntryVO("a.png", "/attached/images/a.png?size=small", "file", 1, "image/png", 0),
                new FileEntryVO("readme.txt", "/attached/readme.txt", "file", 1, "text/plain", 0),
                new FileEntryVO("external", "/external", "directory", 0, "", 0)
        );

        List<FileEntryVO> result = service.applyReferenceInfo(entries, references);

        assertEquals(2, result.get(0).getReferenceCount());
        assertTrue(result.get(0).isReferenced());
        assertEquals(1, result.get(1).getReferenceCount());
        assertTrue(result.get(1).isReferenced());
        assertEquals(0, result.get(2).getReferenceCount());
        assertFalse(result.get(2).isReferenced());
        assertEquals(0, result.get(3).getReferenceCount());
    }

    @Test
    public void shouldListExternalResourceDomainsAndDomainFilesFromRequestIndex() throws Exception {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");
        FileReferenceIndexCacheVO index = new FileReferenceIndexCacheVO();
        Map<String, List<FileReferenceVO>> external = new LinkedHashMap<>();
        external.put("https://cdn.example.com/a.png", List.of(reference(1, true, false)));
        external.put("https://cdn.example.com/b.css", List.of(reference(1, false, true), reference(2, false, true)));
        external.put("//static.example.com/c.js", List.of(reference(3, false, true)));
        index.setExternalReferences(external);
        index.setLocalReferences(Map.of());
        setRequestReferenceIndex(service, index);

        List<FileEntryVO> disabled = service.getExternalResources(FileManagerService.EXTERNAL_ROOT, false, entry -> entry);
        List<FileEntryVO> domains = service.getExternalResources(FileManagerService.EXTERNAL_ROOT, true, entry -> entry);
        List<FileEntryVO> files = service.getExternalResources(FileManagerService.EXTERNAL_ROOT + "/cdn.example.com",
                true, entry -> entry);

        assertEquals(List.of(), disabled);
        assertEquals(2, domains.size());
        assertEquals("cdn.example.com", domains.get(0).getName());
        assertEquals(FileManagerService.EXTERNAL_ROOT + "/cdn.example.com", domains.get(0).getPath());
        assertEquals("directory", domains.get(0).getType());
        assertEquals(2, domains.get(0).getReferenceCount());
        assertEquals(2, files.size());
        assertEquals("https://cdn.example.com/a.png", files.get(0).getPath());
        assertEquals("file", files.get(0).getType());
        assertEquals("image/png", files.get(0).getMimeType());
        assertEquals(1, files.get(0).getReferenceCount());
        assertEquals(2, files.get(1).getReferenceCount());
    }

    @Test
    public void shouldReturnMissingLocalResourceEntriesFromRequestIndex() throws Exception {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");
        FileReferenceIndexCacheVO index = new FileReferenceIndexCacheVO();
        Map<String, List<FileReferenceVO>> local = new LinkedHashMap<>();
        local.put("/attached/__missing__/asset.png", List.of(reference(5, true, true)));
        local.put("/templates/default/style.css", List.of(reference(6, false, true)));
        index.setLocalReferences(local);
        index.setExternalReferences(Map.of());
        setRequestReferenceIndex(service, index);

        List<FileEntryVO> entries = service.getMissingLocalResources(entry -> {
            entry.setIconType("decorated");
            return entry;
        });

        assertEquals(1, entries.size());
        FileEntryVO entry = entries.get(0);
        assertEquals("asset.png", entry.getName());
        assertEquals("/attached/__missing__/asset.png", entry.getPath());
        assertEquals("file", entry.getType());
        assertTrue(entry.isVirtual());
        assertTrue(entry.isMissing());
        assertEquals("targetMissing", entry.getMissingReason());
        assertEquals("decorated", entry.getIconType());
        assertEquals(1, entry.getReferenceCount());
    }

    @Test
    public void shouldBuildAndCacheReferenceIndexFromRealArticleTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            insertArticle(db, 11, "Assets Article", "assets-article",
                    "/blog/attached/thumbs/cover.png?size=1",
                    "<p><img src=\"/blog/attached/images/a.png?x=1\">"
                            + "<a download href=\"/blog/attached/docs/a.pdf\">doc</a>"
                            + "<img src=\"https://cdn.example.com/images/a.png\"></p>",
                    "![diagram](/blog/attached/md/diagram.png) "
                            + "[manual](https://cdn.example.com/files/manual.pdf)");
            FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");

            Map<String, List<FileReferenceVO>> local = service.buildLocalReferenceMap();
            Map<String, List<FileReferenceVO>> external = service.buildExternalReferenceMap();
            Map<String, Object> cacheRow = db.queryOne(
                    "select value from website where name=?", "admin_cache:file_manager_reference_index");

            assertTrue(local.containsKey("/attached/thumbs/cover.png"));
            assertTrue(local.get("/attached/thumbs/cover.png").get(0).isThumbnail());
            assertTrue(local.containsKey("/attached/images/a.png"));
            assertTrue(local.get("/attached/images/a.png").get(0).isContent());
            assertTrue(local.containsKey("/attached/docs/a.pdf"));
            assertTrue(local.containsKey("/attached/md/diagram.png"));
            assertEquals(11, local.get("/attached/md/diagram.png").get(0).getLogId());
            assertTrue(external.containsKey("https://cdn.example.com/images/a.png"));
            assertTrue(external.containsKey("https://cdn.example.com/files/manual.pdf"));
            assertNotNull(cacheRow);
            assertTrue(String.valueOf(cacheRow.get("value")).contains("assets-article"));

            FileManagerReferenceService cachedService = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");
            assertEquals(local.keySet(), cachedService.buildLocalReferenceMap().keySet());
        }
    }

    @Test
    public void shouldReplaceArticleResourceReferencesThroughRealDaoAndRecordVersionPatch() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            insertArticle(db, 12, "Replace Article", "replace-article",
                    "/attached/images/cover.png",
                    "<p>Hero <img src=\"/attached/images/a.png\"><a download href=\"/attached/images/doc.pdf\">Doc</a></p>",
                    "![asset](/attached/images/b.png)");
            db.putWebsite("admin_cache:file_manager_reference_index", "{\"stale\":true}");
            FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "");

            ReplaceArticleResourceUrlResponse response = service.replaceArticleResourceReferences(
                    token(), "/attached/images", "/attached/media", true);
            Map<String, Object> row = db.queryOne(
                    "select thumbnail, content, markdown, plain_content, version from log where logId=?", 12);
            Map<String, Object> versionRow = db.queryOne(
                    "select article_version, from_version, user_id, title, patch_json from log_version where log_id=?", 12);
            Map<String, Object> cacheRow = db.queryOne(
                    "select value from website where name=?", "admin_cache:file_manager_reference_index");

            assertEquals(1, response.getScannedArticles());
            assertEquals(1, response.getUpdatedArticles());
            assertEquals(3, response.getUpdatedFields());
            assertEquals("/attached/media/cover.png", row.get("thumbnail"));
            assertTrue(String.valueOf(row.get("content")).contains("/attached/media/a.png"));
            assertTrue(String.valueOf(row.get("content")).contains("/attached/media/doc.pdf"));
            assertEquals("![asset](/attached/media/b.png)", row.get("markdown"));
            assertTrue(String.valueOf(row.get("plain_content")).contains("Hero"));
            assertEquals(1, ((Number) row.get("version")).intValue());
            assertEquals(1, ((Number) versionRow.get("article_version")).intValue());
            assertEquals(0, ((Number) versionRow.get("from_version")).intValue());
            assertEquals(7, ((Number) versionRow.get("user_id")).intValue());
            assertEquals("Replace Article", versionRow.get("title"));
            assertTrue(String.valueOf(versionRow.get("patch_json")).contains("/attached/images"));
            assertNull(cacheRow.get("value"));
        }
    }

    @Test
    public void shouldReplaceLocalResourceReferencesByPrefixAcrossArticleFields() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("thumbnail", "/blog/attached/images/a.png?x=1");
        article.put("content", "<img src=\"/blog/attached/images/a.png\"><a download href=\"/blog/attached/images/doc.pdf\">doc</a>");
        article.put("markdown", "![cover](/blog/attached/images/b.png)");
        article.put("digest", "<img src=\"/blog/attached/images/c.png\">");

        Map<String, Object> updates = service.buildArticleResourceUrlUpdates(
                article, "/attached/images", "/attached/media", true);

        assertEquals("/blog/attached/media/a.png?x=1", updates.get("thumbnail"));
        assertEquals("<img src=\"/blog/attached/media/a.png\"><a download href=\"/blog/attached/media/doc.pdf\">doc</a>",
                updates.get("content"));
        assertEquals("![cover](/blog/attached/media/b.png)", updates.get("markdown"));
        assertEquals("<img src=\"/blog/attached/media/c.png\">", updates.get("digest"));
    }

    @Test
    public void shouldReplaceExternalResourceReferencesByPrefixAcrossArticleFields() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "");
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("thumbnail", "https://cdn.example.com/assets/a.png?x=1#top");
        article.put("content", "<img src=\"https://cdn.example.com/assets/a.png\"><img src=\"https://cdn.example.com/other/a.png\">");
        article.put("markdown", "![cover](https://cdn.example.com/assets/b.png)");
        article.put("digest", "<img src=\"https://cdn.example.com/assets/c.png\">");

        Map<String, Object> updates = service.buildArticleResourceUrlUpdates(
                article, "https://cdn.example.com/assets", "https://static.example.net/media", true);

        assertEquals("https://static.example.net/media/a.png?x=1#top", updates.get("thumbnail"));
        assertEquals("<img src=\"https://static.example.net/media/a.png\"><img src=\"https://cdn.example.com/other/a.png\">",
                updates.get("content"));
        assertEquals("![cover](https://static.example.net/media/b.png)", updates.get("markdown"));
        assertEquals("<img src=\"https://static.example.net/media/c.png\">", updates.get("digest"));
    }

    @Test
    public void shouldSkipResourceReplacementWhenMatchKeyIsInvalidOrUnchanged() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "");
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("thumbnail", "/attached/images/a.png");
        article.put("content", "<img src=\"/attached/images/a.png\">");

        assertTrue(service.buildArticleResourceUrlUpdates(article, "", "/attached/b.png", false).isEmpty());
        assertTrue(service.buildArticleResourceUrlUpdates(article, "/attached/missing.png",
                "/attached/b.png", false).isEmpty());
    }

    private static FileReferenceVO reference(int logId, boolean thumbnail, boolean content) {
        FileReferenceVO reference = new FileReferenceVO(logId, "Article " + logId, "alias-" + logId);
        reference.setThumbnail(thumbnail);
        reference.setContent(content);
        return reference;
    }

    private static void insertArticle(InMemoryZrLogDatabase db, int id, String title, String alias, String thumbnail,
                                      String content, String markdown) throws Exception {
        db.execute("insert into log(logId, alias, canComment, click, version, content, plain_content, markdown, digest,"
                        + " keywords, thumbnail, recommended, releaseTime, last_update_date, title, typeId, userId,"
                        + " hot, rubbish, privacy, editor_type) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(),"
                        + " now(), ?, ?, ?, ?, ?, ?, ?)",
                id, alias, true, 0, 0, content, "plain " + title, markdown, "Digest " + title,
                "zrlog,asset", thumbnail, false, title, 1, 1, false, false, false, "markdown");
    }

    private static AdminTokenVO token() {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(7);
        token.setSessionId("session-7");
        token.setProtocol("http");
        return token;
    }

    private static void setRequestReferenceIndex(FileManagerReferenceService service,
                                                 FileReferenceIndexCacheVO index) throws Exception {
        Field field = FileManagerReferenceService.class.getDeclaredField("requestReferenceIndex");
        field.setAccessible(true);
        field.set(service, index);
    }
}
