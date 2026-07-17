package com.zrlog.admin.business;

import com.zrlog.admin.business.ai.model.AIModelCapability;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.ai.prompt.AIPromptVO;
import com.zrlog.admin.business.rest.base.AdminWebSiteInfo;
import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.admin.business.rest.base.BasicWebSiteInfo;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.CreateTypeRequest;
import com.zrlog.admin.business.rest.request.PersonalDataPreviewRequest;
import com.zrlog.admin.business.rest.request.ReplaceArticleResourceUrlRequest;
import com.zrlog.admin.business.rest.request.TagManageRequest;
import com.zrlog.admin.business.rest.request.UpdateAdminRequest;
import com.zrlog.admin.business.rest.request.UpdatePasswordRequest;
import com.zrlog.admin.business.rest.request.UpgradeRestartNoticeRequest;
import com.zrlog.admin.business.rest.request.WebhookMessageNoticeRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.AdminStaticSiteSyncResponse;
import com.zrlog.admin.business.rest.response.ErrorPageResponse;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.business.rest.response.MessageCenterOperationNoticeEntry;
import com.zrlog.admin.business.rest.response.PluginInfoResponse;
import com.zrlog.admin.business.rest.response.TemplateValuePreviewResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.rest.response.WebhookMessageNoticeEntry;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.Version;
import com.zrlog.data.util.WebSiteUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AdminDtoContractTest {

    //TODO: Add service/controller fixtures for admin workflows that require DAO records,
    // uploaded files, plugin runtime, SSE, or HTTP controller state.
    @Test
    public void shouldExerciseAdminBeanContracts() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        classes.addAll(classesUnder("src/main/java/com/zrlog/admin/business/dto",
                "com.zrlog.admin.business.dto"));
        classes.addAll(classesUnder("src/main/java/com/zrlog/admin/business/rest/base",
                "com.zrlog.admin.business.rest.base"));
        classes.addAll(classesUnder("src/main/java/com/zrlog/admin/business/rest/request",
                "com.zrlog.admin.business.rest.request"));
        classes.addAll(classesUnder("src/main/java/com/zrlog/admin/business/rest/response",
                "com.zrlog.admin.business.rest.response"));
        classes.addAll(classesUnder("src/main/java/com/zrlog/admin/business/ai/dto",
                "com.zrlog.admin.business.ai.dto"));
        classes.addAll(classesUnder("src/main/java/com/zrlog/admin/business/ai/prompt",
                "com.zrlog.admin.business.ai.prompt"));
        classes.addAll(classesUnder("src/main/java/com/zrlog/admin/business/ai/model",
                "com.zrlog.admin.business.ai.model"));

        int exercised = 0;
        for (Class<?> type : expandDeclaredClasses(classes)) {
            if (exerciseType(type)) {
                exercised++;
            }
        }

        assertTrue("Expected broad admin DTO coverage", exercised > 80);
    }

    @Test
    public void shouldExposeAiProviderAndPromptContracts() {
        assertTrue(new com.zrlog.admin.business.ai.model.AIModelEntry(
                "text-model", AIModelCapability.TEXT).supports(AIModelCapability.TEXT));
        assertFalse(new com.zrlog.admin.business.ai.model.AIModelEntry(
                "text-model", AIModelCapability.TEXT).supports(AIModelCapability.IMAGE_GENERATION));

        assertEquals("deepseek-v4-pro", AIProviderType.DEEP_SEEK.getModels().get(0));
        assertEquals("gpt-5.6", AIProviderType.OPEN_AI.getModels().get(0));
        assertTrue(AIProviderType.OPEN_AI.getModels().contains("gpt-5.6-sol"));
        assertTrue(AIProviderType.OPEN_AI.getModels().contains("gpt-5.6-terra"));
        assertTrue(AIProviderType.OPEN_AI.getModels().contains("gpt-5.6-luna"));
        assertEquals("qwen3.7-max", AIProviderType.QWEN.getModels().get(0));
        assertEquals("gemini-3.5-flash", AIProviderType.GOOGLE_GEMINI.getModels().get(0));
        assertFalse(AIProviderType.GOOGLE_GEMINI.getModels().contains("gemini-3.1-flash-lite-preview"));
        assertTrue(AIProviderType.OPEN_AI.getImageModels().contains("gpt-image-2"));
        assertEquals("gemini-3.1-flash-image", AIProviderType.GOOGLE_GEMINI.getImageModels().get(0));
        assertNotNull(AIProviderType.OPEN_AI.getBaseUrl());
        assertFalse(AIProviderType.DEEP_SEEK.getModels().isEmpty());
        assertTrue(AIProviderType.DEEP_SEEK.getImageModels().isEmpty());

        AIPromptVO prompt = AIPromptVO.getByToolKey("article-title-generate");
        AIPromptVO imagePrompt = AIPromptVO.getByToolKey("article-cover-generate");
        assertNotNull(prompt);
        assertEquals("article-title-generate", prompt.getToolKey());
        assertTrue(prompt.getPromptFallback().endsWith("zh_CN.md"));
        assertTrue(prompt.getInputFallback().endsWith("zh_CN.md"));
        assertEquals(null, imagePrompt.getPromptFallback());
        assertTrue(AIPromptVO.getAll().size() >= 10);
    }

    @Test
    public void shouldExerciseResponseConvenienceConstructorsAndFactories() {
        assertEquals(Boolean.TRUE, new AdminStaticSiteSyncResponse(true).getSynced());
        assertEquals("/plugin/page", new PluginInfoResponse("/plugin/page").getIncludePagePath());
        assertEquals("not found", new ErrorPageResponse("not found").getMessage());
        assertEquals("preview", new TemplateValuePreviewResponse("preview").getPreviewValue());

        UpdateRecordResponse success = new UpdateRecordResponse(true);
        UpdateRecordResponse failure = new UpdateRecordResponse(false);
        assertEquals(0, success.getError());
        assertEquals(1, failure.getError());

        Version version = new Version();
        version.setVersion("3.6.1");
        assertSame(version, MessageCenterNoticeResponse.versionUpdatePayload(version).getVersion());
        assertEquals(Integer.valueOf(4), MessageCenterNoticeResponse.unreadCommentPayload(4).getCount());

        Map<String, Object> noticePayload = Collections.singletonMap("id", 7L);
        WebhookMessageNoticeEntry webhookNotice = new WebhookMessageNoticeEntry();
        webhookNotice.setTitle("Webhook");
        webhookNotice.setDescription("payload arrived");
        webhookNotice.setActionLabel("Open");
        webhookNotice.setActionPath("/admin/message");
        webhookNotice.setSource("webhook");
        webhookNotice.setClosable(true);
        webhookNotice.setPayload(noticePayload);
        MessageCenterNoticeResponse.WebhookMessagePayload webhookPayload =
                MessageCenterNoticeResponse.webhookMessagePayload(webhookNotice);
        assertEquals("Webhook", webhookPayload.getTitle());
        assertEquals("payload arrived", webhookPayload.getDescription());
        assertEquals("Open", webhookPayload.getActionLabel());
        assertEquals("/admin/message", webhookPayload.getActionPath());
        assertEquals("webhook", webhookPayload.getSource());
        assertEquals(Boolean.TRUE, webhookPayload.getClosable());
        assertSame(noticePayload, webhookPayload.getPayload());

        MessageCenterOperationNoticeEntry operationNotice = new MessageCenterOperationNoticeEntry();
        operationNotice.setTitle("Operation");
        operationNotice.setDescription("task finished");
        operationNotice.setActionLabel("Review");
        operationNotice.setActionPath("/admin/tasks");
        operationNotice.setSource("system");
        operationNotice.setClosable(false);
        operationNotice.setPayload(noticePayload);
        MessageCenterNoticeResponse.OperationTaskPayload operationPayload =
                MessageCenterNoticeResponse.operationPayload(operationNotice);
        assertEquals("Operation", operationPayload.getTitle());
        assertEquals("task finished", operationPayload.getDescription());
        assertEquals("Review", operationPayload.getActionLabel());
        assertEquals("/admin/tasks", operationPayload.getActionPath());
        assertEquals("system", operationPayload.getSource());
        assertEquals(Boolean.FALSE, operationPayload.getClosable());
        assertSame(noticePayload, operationPayload.getPayload());

        MessageCenterNoticeResponse notice = MessageCenterNoticeResponse.of(
                "task", "webhook", "unread", 123L, webhookPayload);
        assertEquals("task", notice.getTaskKey());
        assertEquals("webhook", notice.getType());
        assertEquals("unread", notice.getStatus());
        assertEquals(Long.valueOf(123L), notice.getUpdatedAt());
        assertSame(webhookPayload, notice.getPayload());
    }

    @Test
    public void shouldExposeAiResponseContextMetaContract() {
        AIResponseEntry.AIContentEntry.ArticleContextMeta contextMeta =
                new AIResponseEntry.AIContentEntry.ArticleContextMeta();
        contextMeta.setTitle("Article");
        contextMeta.setArticleVersion(2);
        contextMeta.setMarkdownLength(512);
        contextMeta.setCreatedAt(123456L);

        AIResponseEntry.AIContentEntry content = new AIResponseEntry.AIContentEntry("assistant", "body");
        content.setContextMeta(contextMeta);

        assertEquals("assistant", content.getRole());
        assertEquals("body", content.getContent());
        assertSame(contextMeta, content.getContextMeta());
        assertEquals("Article", contextMeta.getTitle());
        assertEquals(Integer.valueOf(2), contextMeta.getArticleVersion());
        assertEquals(Integer.valueOf(512), contextMeta.getMarkdownLength());
        assertEquals(Long.valueOf(123456L), contextMeta.getCreatedAt());
    }

    @Test
    public void shouldValidateAndCleanWebsiteBaseDtos() {
        BasicWebSiteInfo basic = new BasicWebSiteInfo();
        assertThrows(ArgsException.class, basic::doValid);
        basic.setTitle("<b>Blog</b>");
        basic.setSecond_title("<i>Notes</i>");
        basic.setKeywords("<span>java,zrlog</span>");
        basic.setDescription("<script>alert(1)</script>fast blog");
        basic.setFavicon_ico_base64("<b>data:image/png;base64,abc</b>");
        basic.setAuthor("<em>author</em>");
        basic.doClean();
        basic.doValid();
        assertEquals("Blog", basic.getTitle());
        assertEquals("Notes", basic.getSecond_title());
        assertEquals("java,zrlog", basic.getKeywords());
        assertEquals("fast blog", basic.getDescription());
        assertEquals("data:image/png;base64,abc", basic.getFavicon_ico_base64());
        assertEquals("author", basic.getAuthor());

        AdminWebSiteInfo admin = new AdminWebSiteInfo();
        admin.doValid();
        assertEquals(Long.valueOf(WebSiteUtils.DEFAULT_SESSION_TIMEOUT), admin.getSession_timeout());
        admin.setSession_timeout(5L);
        assertThrows(ArgsException.class, admin::doValid);
        admin.setSession_timeout(30L);
        admin.setAdmin_static_resource_base_url("cdn.example.com/admin");
        assertThrows(ArgsException.class, admin::doClean);
        admin.setAdmin_static_resource_base_url("https://cdn.example.com/<b>admin</b>");
        admin.setLanguage("<b>zh_CN</b>");
        admin.setAdmin_color_primary("<span>#45a29e</span>");
        admin.setAdmin_theme("<i>system</i>");
        admin.setFavicon_png_pwa_192_base64("<b>icon192</b>");
        admin.setFavicon_png_pwa_512_base64("<b>icon512</b>");
        admin.doClean();
        assertEquals("https://cdn.example.com/admin", admin.getAdmin_static_resource_base_url());
        assertEquals("zh_CN", admin.getLanguage());
        assertEquals("#45a29e", admin.getAdmin_color_primary());
        assertEquals("system", admin.getAdmin_theme());
        assertEquals("icon192", admin.getFavicon_png_pwa_192_base64());
        assertEquals("icon512", admin.getFavicon_png_pwa_512_base64());

        ArticleEditWebSiteInfo articleEdit = new ArticleEditWebSiteInfo();
        articleEdit.setArticle_edit_auto_save_interval(3L);
        articleEdit.setArticle_editor_link_preview_enabled(null);
        articleEdit.setArticle_publish_check_enabled(null);
        articleEdit.setArticle_cover_aspect_ratio("5:4");
        articleEdit.doValid();
        assertEquals(Long.valueOf(WebSiteUtils.DEFAULT_ARTICLE_DIGEST_LENGTH),
                articleEdit.getArticle_auto_digest_length());
        assertEquals(ArticleEditWebSiteInfo.DEFAULT_ARTICLE_EDIT_AUTO_SAVE_INTERVAL,
                articleEdit.getArticle_edit_auto_save_interval());
        assertEquals(Boolean.FALSE, articleEdit.getArticle_editor_link_preview_enabled());
        assertEquals(Boolean.TRUE, articleEdit.getArticle_publish_check_enabled());
        assertEquals(ArticleEditWebSiteInfo.DEFAULT_ARTICLE_COVER_ASPECT_RATIO,
                articleEdit.getArticle_cover_aspect_ratio());
        articleEdit.setArticle_edit_auto_save_interval(10L);
        articleEdit.setArticle_cover_aspect_ratio("21:9");
        articleEdit.doValid();
        assertEquals(Long.valueOf(10L), articleEdit.getArticle_edit_auto_save_interval());
        assertEquals("21:9", articleEdit.getArticle_cover_aspect_ratio());
    }

    @Test
    public void shouldValidateAndCleanArticleAndWebhookRequests() {
        CreateArticleRequest article = new CreateArticleRequest();
        assertThrows(ArgsException.class, article::doValid);
        article.setTypeId(0L);
        assertThrows(ArgsException.class, article::doValid);
        article.setTypeId(7L);
        article.setAlias("<b>hello-world</b>");
        article.setTitle("<i>Title</i>");
        article.setThumbnail("<span>/attached/a.png</span>");
        article.setKeywords("<em>java,zrlog</em>");
        article.setDigest("<p>summary <strong>kept</strong><script>alert(1)</script></p>");
        article.doClean();
        article.doValid();
        assertEquals("hello-world", article.getAlias());
        assertEquals("Title", article.getTitle());
        assertEquals("/attached/a.png", article.getThumbnail());
        assertEquals("java,zrlog", article.getKeywords());
        assertEquals("<p>summary <strong>kept</strong></p>", article.getDigest());

        WebhookMessageNoticeRequest notice = new WebhookMessageNoticeRequest();
        assertThrows(ArgsException.class, notice::doValid);
        notice.setTaskKey("<b>" + repeat("task", 40) + "</b>");
        notice.setTitle("<i>" + repeat("title", 30) + "</i>");
        notice.setDescription("<p>" + repeat("description", 210) + "</p>");
        notice.setActionLabel("<em>" + repeat("label", 20) + "</em>");
        notice.setActionPath("<span>/admin/message</span>");
        notice.setSource("<strong>webhook</strong>");
        notice.doClean();
        notice.doValid();
        assertEquals(120, notice.getTaskKey().length());
        assertEquals(120, notice.getTitle().length());
        assertEquals(2000, notice.getDescription().length());
        assertEquals(80, notice.getActionLabel().length());
        assertEquals("/admin/message", notice.getActionPath());
        assertEquals("webhook", notice.getSource());
        assertEquals(Boolean.TRUE, notice.getClosable());
    }

    @Test
    public void shouldValidateAndCleanAdditionalAdminRequests() {
        CreateTypeRequest type = new CreateTypeRequest();
        assertThrows(ArgsException.class, type::doValid);
        type.setAlias("<b>tech</b>");
        assertThrows(ArgsException.class, type::doValid);
        type.setTypeName("<i>Technology</i>");
        type.setRemark("<p>Desc <strong>ok</strong><script>alert(1)</script></p>");
        type.doClean();
        type.doValid();
        assertEquals("tech", type.getAlias());
        assertEquals("Technology", type.getTypeName());
        assertFalse(type.getRemark().contains("script"));

        ReplaceArticleResourceUrlRequest replace = new ReplaceArticleResourceUrlRequest();
        assertThrows(ArgsException.class, replace::doValid);
        replace.setFromUrl(" /attached/a.png ");
        replace.setToUrl(" /attached/a.png ");
        assertThrows(ArgsException.class, replace::doValid);
        replace.setToUrl(" /attached/b.png ");
        replace.doClean();
        replace.doValid();
        assertEquals("/attached/a.png", replace.getFromUrl());
        assertEquals("/attached/b.png", replace.getToUrl());

        UpdateAdminRequest admin = new UpdateAdminRequest();
        assertThrows(ArgsException.class, admin::doValid);
        admin.setUserName("<b>admin</b>");
        admin.setEmail("<i>admin@example.com</i>");
        admin.setHeader("<span>/attached/header.png</span>");
        admin.doClean();
        admin.doValid();
        assertEquals("admin", admin.getUserName());
        assertEquals("admin@example.com", admin.getEmail());
        assertEquals("/attached/header.png", admin.getHeader());

        PersonalDataPreviewRequest personalData = new PersonalDataPreviewRequest();
        assertThrows(ArgsException.class, personalData::doValid);
        personalData.setQuery("   ");
        assertThrows(ArgsException.class, personalData::doClean);
        personalData.setQuery(repeat("q", 161));
        assertThrows(ArgsException.class, personalData::doValid);
        personalData.setQuery("  user@example.com  ");
        personalData.doClean();
        personalData.doValid();
        assertEquals("user@example.com", personalData.getQuery());

        TagManageRequest tag = new TagManageRequest();
        assertThrows(ArgsException.class, tag::doValid);
        tag.setSourceTag(" <b>old</b> ");
        tag.setTargetTag(" <i>new</i> ");
        tag.doClean();
        tag.doValid();
        assertEquals("old", tag.getSourceTag());
        assertEquals("new", tag.getTargetTag());

        UpgradeRestartNoticeRequest restartNotice = new UpgradeRestartNoticeRequest();
        assertThrows(ArgsException.class, restartNotice::doValid);
        restartNotice.setStatus(" success ");
        restartNotice.setBuildId(" build-1 ");
        restartNotice.doClean();
        restartNotice.doValid();
        assertEquals("success", restartNotice.getStatus());
        assertEquals("build-1", restartNotice.getBuildId());
        restartNotice.setStatus("done");
        assertThrows(ArgsException.class, restartNotice::doValid);

        UpdatePasswordRequest password = new UpdatePasswordRequest();
        assertThrows(ArgsException.class, password::doValid);
        password.setOldPassword("old");
        assertThrows(ArgsException.class, password::doValid);
        password.setNewPassword("new");
        password.doValid();
    }

    private static List<Class<?>> classesUnder(String relativeDir, String packageName) throws Exception {
        Path root = Paths.get(relativeDir);
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                String relative = root.relativize(path).toString();
                String className = packageName + "." + relative
                        .replace(File.separatorChar, '.')
                        .replaceAll("\\.java$", "");
                Class<?> type = Class.forName(className);
                if (!type.isAnnotation()) {
                    classes.add(type);
                }
            }
        }
        return classes;
    }

    private static List<Class<?>> expandDeclaredClasses(List<Class<?>> classes) {
        List<Class<?>> expanded = new ArrayList<>();
        for (Class<?> type : classes) {
            expanded.add(type);
            Collections.addAll(expanded, type.getDeclaredClasses());
        }
        return expanded;
    }

    private static boolean exerciseType(Class<?> type) throws Exception {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.isAnnotation()) {
            return false;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            for (Object constant : constants) {
                invokeGetters(type, constant);
            }
            return constants.length > 0;
        }
        Object bean = newInstance(type);
        if (bean == null) {
            return false;
        }
        int touched = 0;
        for (Method setter : type.getMethods()) {
            if (!isSetter(setter)) {
                continue;
            }
            Object value = sampleValue(setter.getParameterTypes()[0]);
            if (value == UnsupportedValue.INSTANCE) {
                continue;
            }
            setter.invoke(bean, value);
            Method getter = getterFor(type, setter);
            if (getter != null) {
                assertEquals(value, getter.invoke(bean));
            }
            touched++;
        }
        invokeGetters(type, bean);
        return touched > 0 || type.getDeclaredConstructors().length > 0;
    }

    private static void invokeGetters(Class<?> type, Object bean) throws Exception {
        for (Method getter : type.getMethods()) {
            if (isGetter(getter)) {
                getter.invoke(bean);
            }
        }
    }

    private static boolean isSetter(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && method.getName().startsWith("set")
                && method.getParameterCount() == 1
                && method.getDeclaringClass() != Object.class;
    }

    private static boolean isGetter(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 0
                && method.getDeclaringClass() != Object.class
                && (method.getName().startsWith("get") || method.getName().startsWith("is"));
    }

    private static Method getterFor(Class<?> type, Method setter) {
        String suffix = setter.getName().substring(3);
        try {
            return type.getMethod("get" + suffix);
        } catch (NoSuchMethodException ignored) {
            try {
                return type.getMethod("is" + suffix);
            } catch (NoSuchMethodException ignoredAgain) {
                return null;
            }
        }
    }

    private static Object newInstance(Class<?> type) throws Exception {
        Constructor<?> noArg = noArgConstructor(type);
        if (noArg != null) {
            noArg.setAccessible(true);
            return noArg.newInstance();
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Object[] args = sampleArgs(constructor.getParameterTypes());
            if (args != null) {
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            }
        }
        return null;
    }

    private static Constructor<?> noArgConstructor(Class<?> type) {
        try {
            return type.getDeclaredConstructor();
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object[] sampleArgs(Class<?>[] parameterTypes) throws Exception {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object value = sampleValue(parameterTypes[i]);
            if (value == UnsupportedValue.INSTANCE) {
                return null;
            }
            args[i] = value;
        }
        return args;
    }

    private static Object sampleValue(Class<?> type) throws Exception {
        if (type == String.class) {
            return "value";
        }
        if (type == Long.class || type == long.class) {
            return 7L;
        }
        if (type == Integer.class || type == int.class) {
            return 3;
        }
        if (type == Boolean.class || type == boolean.class) {
            return true;
        }
        if (type == Date.class) {
            return new Date(1_000);
        }
        if (type == List.class) {
            return Collections.singletonList("value");
        }
        if (type == Set.class) {
            return Collections.singleton("value");
        }
        if (type == Map.class) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("key", "value");
            return value;
        }
        if (type == Object.class) {
            return "object-value";
        }
        if (type == InputStream.class) {
            return new ByteArrayInputStream("body".getBytes(StandardCharsets.UTF_8));
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length == 0 ? UnsupportedValue.INSTANCE : constants[0];
        }
        Object nested = newInstance(type);
        return nested == null ? UnsupportedValue.INSTANCE : nested;
    }

    private static String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }

    private enum UnsupportedValue {
        INSTANCE
    }
}
