package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.request.ArticleVersionRollbackRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.ArticleVersionCompareResponse;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.admin.business.rest.response.LoadEditArticleResponse;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ArticleVersionServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldBuildReversePatchForChangedSnapshotFields() throws Exception {
        ArticleVersionService service = newService();
        Method method = method("buildReversePatch", Map.class, Map.class);
        Map<String, Object> oldLog = new LinkedHashMap<>();
        oldLog.put("title", "hello old world");
        oldLog.put("content", "same");
        oldLog.put("typeId", 2);
        oldLog.put("editor_type", "markdown");
        Map<String, Object> newLog = new LinkedHashMap<>();
        newLog.put("title", "hello new world");
        newLog.put("content", "same");
        newLog.put("typeId", 3);
        newLog.put("editor_type", "rich");

        Map<String, Object> patch = (Map<String, Object>) method.invoke(service, oldLog, newLog);

        assertEquals(3, patch.size());
        assertTrue(patch.containsKey("title"));
        assertTrue(patch.containsKey("typeId"));
        assertTrue(patch.containsKey("editorType"));
        assertFalse(patch.containsKey("content"));
        Map<String, Object> titlePatch = (Map<String, Object>) patch.get("title");
        assertEquals("text", titlePatch.get("type"));
        assertEquals("old", titlePatch.get("oldMiddle"));
        assertEquals(6, titlePatch.get("prefixLength"));
        assertEquals(6, titlePatch.get("suffixLength"));
        assertEquals(Map.of("type", "value", "old", 2), patch.get("typeId"));
        Map<String, Object> editorTypePatch = (Map<String, Object>) patch.get("editorType");
        assertEquals("text", editorTypePatch.get("type"));
        assertEquals("markdown", editorTypePatch.get("oldMiddle"));
    }

    @Test
    public void shouldApplyReversePatchToArticleSnapshot() throws Exception {
        ArticleVersionService service = newService();
        LoadEditArticleResponse article = article(9, 4, "hello new world");
        article.setContent("new content");
        article.setTypeId(3L);
        article.setCanComment(false);
        article.setRecommended(false);
        article.setPrivacy(true);
        article.setRubbish(true);
        article.setEditorType("rich");
        Method method = method("applyReversePatch", LoadEditArticleResponse.class, String.class);

        method.invoke(service, article, "{"
                + "\"title\":{\"type\":\"text\",\"prefixLength\":6,\"suffixLength\":6,\"oldMiddle\":\"old\"},"
                + "\"content\":{\"type\":\"value\",\"old\":\"old content\"},"
                + "\"typeId\":{\"type\":\"value\",\"old\":2},"
                + "\"canComment\":{\"type\":\"value\",\"old\":true},"
                + "\"recommended\":{\"type\":\"value\",\"old\":true},"
                + "\"privacy\":{\"type\":\"value\",\"old\":false},"
                + "\"rubbish\":{\"type\":\"value\",\"old\":false},"
                + "\"editorType\":{\"type\":\"value\",\"old\":\"markdown\"}"
                + "}");

        assertEquals("hello old world", article.getTitle());
        assertEquals("old content", article.getContent());
        assertEquals(Long.valueOf(2), article.getTypeId());
        assertTrue(article.isCanComment());
        assertTrue(article.isRecommended());
        assertFalse(article.isPrivacy());
        assertFalse(article.isRubbish());
        assertEquals("markdown", article.getEditorType());
    }

    @Test
    public void shouldHandlePrivateValueConversionHelpers() throws Exception {
        Method applyTextReversePatch = method("applyTextReversePatch", String.class, Map.class);
        Method toInteger = method("toInteger", Object.class);
        Method toBoolean = method("toBoolean", Object.class);
        Method toStringValue = method("toStringValue", Object.class);
        Method getIgnoreCase = method("getIgnoreCase", Map.class, String.class);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("LASTUPDATEDATE", 123L);

        assertEquals("hello old world", applyTextReversePatch.invoke(null, "hello new world",
                Map.of("prefixLength", 6, "suffixLength", 6, "oldMiddle", "old")));
        assertEquals("short", applyTextReversePatch.invoke(null, "short",
                Map.of("prefixLength", 10, "suffixLength", 1, "oldMiddle", "old")));
        assertEquals("old", applyTextReversePatch.invoke(null, null,
                Map.of("oldMiddle", "old")));
        assertEquals(7, toInteger.invoke(null, 7L));
        assertEquals(8, toInteger.invoke(null, "8"));
        assertNull(toInteger.invoke(null, new Object[]{null}));
        assertEquals(true, toBoolean.invoke(null, "true"));
        assertEquals(false, toBoolean.invoke(null, false));
        assertNull(toBoolean.invoke(null, new Object[]{null}));
        assertEquals("text", toStringValue.invoke(null, "text"));
        assertNull(toStringValue.invoke(null, new Object[]{null}));
        assertEquals(123L, getIgnoreCase.invoke(null, row, "lastUpdateDate"));
        assertNull(getIgnoreCase.invoke(null, row, "missing"));
    }

    @Test
    public void shouldCompareCurrentVersionWithoutLoadingReversePatches() throws Exception {
        FakeAdminArticleService fake = fakeArticleService(article(5, 3, "Current"));
        ArticleVersionService service = newService(fake);

        ArticleVersionCompareResponse response = service.compare(5, 3, 3, null);

        assertEquals(Integer.valueOf(3), response.getFromVersion());
        assertEquals(Integer.valueOf(3), response.getToVersion());
        assertSame(fake.current, response.getFromArticle());
        assertSame(fake.current, response.getToArticle());
        assertEquals(List.of(), response.getChangedFields());
        assertEquals("5", fake.loadedId);
    }

    @Test
    public void shouldRollbackToCurrentVersionThroughInjectedArticleService() throws Exception {
        FakeAdminArticleService fake = fakeArticleService(article(6, 4, "Rollback"));
        ArticleVersionService service = newService(fake);
        ArticleVersionRollbackRequest request = new ArticleVersionRollbackRequest();
        request.setLogId(6);
        request.setVersion(5);
        request.setTargetVersion(4);

        CreateOrUpdateArticleResponse response = service.rollback(null, request, null);

        assertEquals(Long.valueOf(6), response.getLogId());
        assertEquals(Integer.valueOf(6), fake.updatedRequest.getLogId());
        assertEquals(Integer.valueOf(5), fake.updatedRequest.getVersion());
        assertEquals("Rollback", fake.updatedRequest.getTitle());
        assertEquals(Long.valueOf(2), fake.updatedRequest.getTypeId());
    }

    @Test
    public void shouldRejectRollbackMissingVersionInputs() throws Exception {
        ArticleVersionService service = newService();
        ArticleVersionRollbackRequest request = new ArticleVersionRollbackRequest();
        request.setLogId(1);
        request.setVersion(1);

        assertThrows(ArgsException.class, () -> service.rollback(null, request, null));
    }

    private static LoadEditArticleResponse article(Integer logId, Integer version, String title) {
        LoadEditArticleResponse article = new LoadEditArticleResponse();
        article.setLogId(logId);
        article.setVersion(version);
        article.setTitle(title);
        article.setContent("content");
        article.setMarkdown("markdown");
        article.setDigest("digest");
        article.setKeywords("keywords");
        article.setAlias("alias");
        article.setThumbnail("thumbnail");
        article.setTypeId(2L);
        article.setCanComment(true);
        article.setRecommended(false);
        article.setPrivacy(false);
        article.setRubbish(false);
        article.setEditorType("markdown");
        article.setPreviewUrl("/preview/" + logId);
        article.setLastUpdateDate(10L);
        return article;
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = ArticleVersionService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static ArticleVersionService newService() throws Exception {
        return newService(null);
    }

    private static ArticleVersionService newService(AdminArticleService adminArticleService) throws Exception {
        ArticleVersionService service = allocate(ArticleVersionService.class);
        if (adminArticleService != null) {
            setField(service, "adminArticleService", adminArticleService);
        }
        return service;
    }

    private static FakeAdminArticleService fakeArticleService(LoadEditArticleResponse current) throws Exception {
        FakeAdminArticleService fake = allocate(FakeAdminArticleService.class);
        setField(fake, "current", current);
        return fake;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        return (T) unsafe().allocateInstance(type);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static class FakeAdminArticleService extends AdminArticleService {

        private LoadEditArticleResponse current;
        private String loadedId;
        private UpdateArticleRequest updatedRequest;

        @Override
        public LoadEditArticleResponse loadDetail(String id, HttpRequest request) {
            loadedId = id;
            return current;
        }

        @Override
        public CreateOrUpdateArticleResponse update(AdminTokenVO adminTokenVO, UpdateArticleRequest updateArticleRequest)
                throws SQLException {
            updatedRequest = updateArticleRequest;
            CreateOrUpdateArticleResponse response = new CreateOrUpdateArticleResponse();
            response.setLogId(updateArticleRequest.getLogId().longValue());
            response.setPrivacy(updateArticleRequest.isPrivacy());
            response.setRubbish(updateArticleRequest.isRubbish());
            return response;
        }
    }
}
