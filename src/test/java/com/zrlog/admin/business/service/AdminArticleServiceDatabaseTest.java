package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageRequestImpl;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.ArticleMissingTitleException;
import com.zrlog.admin.business.exception.ArticleMissingTypeException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.ArticleActivityData;
import com.zrlog.admin.business.rest.response.ArticlePageData;
import com.zrlog.admin.business.rest.response.ArticleStatusCountResponse;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.admin.business.rest.response.LoadEditArticleResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AdminArticleServiceDatabaseTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    public void shouldCreateAndLoadArticleThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AdminArticleService service = new AdminArticleService();
            WebSiteService webSiteService = new WebSiteService();
            assertTrue(webSiteService.saveAIMessage(List.of(new AIResponseEntry.AIContentEntry("user", "draft")), 0L));

            CreateOrUpdateArticleResponse response = service.create(token(), article("Hello Article", "hello.article"));
            Map<String, Object> row = db.queryOne("select * from log where logId=?", response.getLogId());
            LoadEditArticleResponse loaded = service.loadDetail("hello-article", request());

            assertEquals(Long.valueOf(1), response.getLogId());
            assertEquals("hello-article", row.get("alias"));
            assertEquals("Hello Article", row.get("title"));
            assertEquals("<p>Hello Article content</p>", row.get("content"));
            assertEquals(1, ((Number) row.get("userId")).intValue());
            assertEquals(1, ((Number) row.get("typeId")).intValue());
            assertEquals(Boolean.TRUE, row.get("canComment"));
            assertEquals("Hello Article", loaded.getTitle());
            assertEquals("hello-article", loaded.getAlias());
            assertEquals(Long.valueOf(1), loaded.getTypeId());
            assertTrue(loaded.getPreviewUrl().contains("/hello-article"));
            assertNotNull(loaded.getSocialPreview());
            assertEquals(1, webSiteService.getAiMessageInfoByArticleId(response.getLogId()).getAiMessages().size());
            assertNull(db.queryOne("select value from website where name=?", "ai_chat_message_0").get("value"));
        }
    }

    @Test
    public void shouldPreserveUnsavedDraftAiMessagesForIndependentCreate() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AdminArticleService service = new AdminArticleService();
            WebSiteService webSiteService = new WebSiteService();
            assertTrue(webSiteService.saveAIMessage(List.of(new AIResponseEntry.AIContentEntry("user", "draft")), 0L));
            CreateArticleRequest request = article("Imported draft", "imported-draft");
            request.setPreserveDraftAiMessages(true);

            CreateOrUpdateArticleResponse response = service.create(token(), request);

            assertTrue(webSiteService.getAiMessageInfoByArticleId(response.getLogId()).getAiMessages().isEmpty());
            assertEquals(1, webSiteService.getAiMessageInfoByArticleId(0L).getAiMessages().size());
        }
    }

    @Test
    public void shouldUpdateArticleAndRecordReversePatchThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AdminArticleService service = new AdminArticleService();
            CreateOrUpdateArticleResponse created = service.create(token(), article("First Title", "first"));
            UpdateArticleRequest update = updateArticle(created.getLogId().intValue(), 0, "Second Title", "second");

            CreateOrUpdateArticleResponse updated = service.update(token(), update);
            Map<String, Object> row = db.queryOne("select title, alias, version, privacy from log where logId=?",
                    created.getLogId());
            Map<String, Object> patch = db.queryOne(
                    "select article_version, from_version, title, patch_json from log_version where log_id=?",
                    created.getLogId());

            assertEquals(created.getLogId(), updated.getLogId());
            assertEquals(Boolean.TRUE, updated.getPrivacy());
            assertEquals("Second Title", row.get("title"));
            assertEquals("second", row.get("alias"));
            assertEquals(1, ((Number) row.get("version")).intValue());
            assertEquals(Boolean.TRUE, row.get("privacy"));
            assertEquals(1, ((Number) patch.get("article_version")).intValue());
            assertEquals(0, ((Number) patch.get("from_version")).intValue());
            assertEquals("First Title", patch.get("title"));
            assertTrue(String.valueOf(patch.get("patch_json")).contains("First Title"));
        }
    }

    @Test
    public void shouldClearStickyWhenPublishedArticleBecomesPrivate() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AdminArticleService service = new AdminArticleService();
            CreateOrUpdateArticleResponse created = service.create(token(), article("Pinned", "pinned"));
            CreateOrUpdateArticleResponse other =
                    service.create(token(), article("Pinned Other", "pinned-other"));
            new ArticlePinningService().pin(created.getLogId());
            new ArticlePinningService().pin(other.getLogId());
            UpdateArticleRequest update = updateArticle(created.getLogId().intValue(), 0,
                    "Pinned Private", "pinned-private");

            CreateOrUpdateArticleResponse updated = service.update(token(), update);

            assertEquals(0, ((Number) db.scalar("select sticky from log where logId=?",
                    created.getLogId())).intValue());
            assertEquals(1L, new ArticlePinningService().list().getItems().get(0).getSticky().longValue());
            assertEquals(other.getLogId(), new ArticlePinningService().list().getItems().get(0).getLogId());
            assertTrue(updated.isPublicCacheRefreshRequired());
        }
    }

    @Test
    public void shouldCountAndPageArticleStatusesThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AdminArticleService service = new AdminArticleService();
            service.create(token(), article("Published", "published"));
            service.create(token(), article("Private", "private", true, false));
            service.create(token(), article("Draft", "draft", false, true));

            ArticleStatusCountResponse counts = service.getStatusCounts();
            ArticlePageData privatePage = service.adminPage(new PageRequestImpl(1L, 10L), "", "", "private", request());
            ArticlePageData publishedPage = service.adminPage(new PageRequestImpl(1L, 10L), "Published", "", "published", request());

            assertEquals(3, counts.getTotal());
            assertEquals(1, counts.getPublished());
            assertEquals(1, counts.getPrivateCount());
            assertEquals(1, counts.getDraft());
            assertEquals(1, privatePage.getRows().size());
            assertEquals("Private", privatePage.getRows().get(0).getTitle());
            assertEquals(1, publishedPage.getRows().size());
            assertFalse(publishedPage.getTypes().isEmpty());
            assertEquals(3, publishedPage.getStatusCounts().getTotal());
        }
    }

    @Test
    public void shouldDeleteArticleAndRemovePersistedAiMessageThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AdminArticleService service = new AdminArticleService();
            WebSiteService webSiteService = new WebSiteService();
            CreateOrUpdateArticleResponse created = service.create(token(), article("Delete Me", "delete-me"));
            assertTrue(webSiteService.saveAIMessage(List.of(new AIResponseEntry.AIContentEntry("user", "keep")), created.getLogId()));

            assertTrue(service.delete(created.getLogId()));

            assertNull(db.queryOne("select logId from log where logId=?", created.getLogId()));
            assertNull(db.queryOne("select value from website where name=?", "ai_chat_message_" + created.getLogId()).get("value"));
        }
    }

    @Test
    public void shouldRejectExpiredArticleVersionAgainstRealDao() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AdminArticleService service = new AdminArticleService();
            CreateOrUpdateArticleResponse created = service.create(token(), article("Versioned", "versioned"));
            UpdateArticleRequest staleUpdate = updateArticle(created.getLogId().intValue(), -1, "Stale", "stale");

            assertThrows(UpdateArticleExpireException.class, () -> service.update(token(), staleUpdate));
        }
    }

    @Test
    public void shouldRejectMissingRequiredArticleFieldsBeforeWritingDao() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AdminArticleService service = new AdminArticleService();
            CreateArticleRequest missingTitle = article("", "missing-title");
            CreateArticleRequest missingType = article("Missing Type", "missing-type");
            missingType.setTypeId(0L);

            assertThrows(ArticleMissingTitleException.class, () -> service.create(token(), missingTitle));
            assertThrows(ArticleMissingTypeException.class, () -> service.create(token(), missingType));
        }
    }

    @Test
    public void shouldBuildRecentArticleActivityDataFromRealReleaseTimes() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AdminArticleService service = new AdminArticleService();
            service.create(token(), article("Activity One", "activity-one"));
            service.create(token(), article("Activity Two", "activity-two"));

            List<ArticleActivityData> activityData = service.activityDataList(DIRECT_EXECUTOR).get();

            assertEquals(1, activityData.size());
            assertEquals(Long.valueOf(2L), activityData.get(0).getCount());
            assertNotNull(activityData.get(0).getDate());
        }
    }

    private static CreateArticleRequest article(String title, String alias) {
        return article(title, alias, false, false);
    }

    private static CreateArticleRequest article(String title, String alias, boolean privacy, boolean rubbish) {
        CreateArticleRequest request = new CreateArticleRequest();
        request.setTitle(title);
        request.setAlias(alias);
        request.setContent("<p>" + title + " content</p>");
        request.setMarkdown(title + " markdown");
        request.setKeywords("java,zrlog");
        request.setTypeId(1L);
        request.setCanComment(true);
        request.setRecommended(false);
        request.setPrivacy(privacy);
        request.setRubbish(rubbish);
        request.setEditorType("markdown");
        return request;
    }

    private static UpdateArticleRequest updateArticle(int logId, int version, String title, String alias) {
        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setLogId(logId);
        request.setVersion(version);
        request.setTitle(title);
        request.setAlias(alias);
        request.setContent("<p>" + title + " updated</p>");
        request.setMarkdown(title + " updated markdown");
        request.setDigest("Updated digest");
        request.setKeywords("updated,zrlog");
        request.setTypeId(1L);
        request.setCanComment(false);
        request.setRecommended(true);
        request.setPrivacy(true);
        request.setRubbish(false);
        request.setEditorType("markdown");
        return request;
    }

    private static AdminTokenVO token() {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(1);
        token.setSessionId("session-1");
        token.setProtocol("http");
        return token;
    }

    private static HttpRequest request() {
        Map<String, Object> attrs = new HashMap<>();
        return (HttpRequest) Proxy.newProxyInstance(
                AdminArticleServiceDatabaseTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getHeader":
                            return "Host".equals(args[0]) ? "localhost:18080" : null;
                        case "getUri":
                            return "/admin/article-edit";
                        case "getContextPath":
                            return "/";
                        case "getScheme":
                            return "http";
                        case "getAttr":
                            return attrs;
                        case "getHeaderMap":
                        case "getParamMap":
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
}
