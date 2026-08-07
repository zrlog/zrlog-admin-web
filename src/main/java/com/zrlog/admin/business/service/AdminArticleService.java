package com.zrlog.admin.business.service;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.*;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.ArticleMissingTitleException;
import com.zrlog.admin.business.exception.ArticleMissingTypeException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.blog.polyglot.markdown.MarkdownJsRenderer;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.exception.NotFindDbEntryException;
import com.zrlog.common.exception.ResourceLockedException;
import com.zrlog.common.exception.UnknownException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.common.vo.SocialPreviewDTO;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.data.util.SocialPreviewUtils;
import com.zrlog.model.Log;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ParseUtil;
import com.zrlog.util.ThreadUtils;
import com.zrlog.util.ZrLogUtil;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AdminArticleService {

    private static final Logger LOGGER = LoggerUtil.getLogger(AdminArticleService.class);
    private final ArticleVersionService articleVersionService = new ArticleVersionService(this);

    private static class MarkdownRendererHolder {
        private static final MarkdownJsRenderer INSTANCE = new MarkdownJsRenderer();
    }

    private Lock getWriteLock(AdminTokenVO adminTokenVO, Integer logId) {
        return new DistributedLock("write_article_" + adminTokenVO.getSessionId() + "_" + ObjectUtil.requireNonNullElse(logId, Integer.MAX_VALUE));
    }

    public CreateOrUpdateArticleResponse create(AdminTokenVO adminTokenVO, CreateArticleRequest createArticleRequest) throws SQLException {
        CreateOrUpdateArticleResponse response = save(adminTokenVO, createArticleRequest);
        if (!createArticleRequest.isPreserveDraftAiMessages()) {
            migrateDraftAIMessage(response.getLogId());
        }
        return response;
    }

    public CreateOrUpdateArticleResponse update(AdminTokenVO adminTokenVO, UpdateArticleRequest updateArticleRequest) throws SQLException {
        return save(adminTokenVO, updateArticleRequest);
    }

    private CreateOrUpdateArticleResponse save(AdminTokenVO adminTokenVO, CreateArticleRequest createArticleRequest) throws SQLException {
        if (Objects.isNull(createArticleRequest) || StringUtils.isEmpty(createArticleRequest.getTitle())) {
            throw new ArticleMissingTitleException();
        }
        if (Objects.isNull(createArticleRequest.getTypeId()) || createArticleRequest.getTypeId() < 1) {
            throw new ArticleMissingTypeException();
        }
        Integer logId = (createArticleRequest instanceof UpdateArticleRequest ? ((UpdateArticleRequest) createArticleRequest).getLogId() : null);
        Lock lock = getWriteLock(adminTokenVO, logId);
        try {
            if (!lock.tryLock(20, TimeUnit.SECONDS)) {
                throw new ResourceLockedException();
            }
        } catch (InterruptedException e) {
            throw new UnknownException(e);
        }
        try {
            Map<String, Object> oldLog = null;
            if (createArticleRequest instanceof UpdateArticleRequest) {
                oldLog = new Log().loadById(((UpdateArticleRequest) createArticleRequest).getLogId());
            }
            Map<String, Object> previousLog = oldLog;
            boolean clearsPublicState = isPublicArticle(previousLog)
                    && (createArticleRequest.isPrivacy() || createArticleRequest.isRubbish());
            if (clearsPublicState) {
                return ArticlePinningService.withOrderLock(
                        () -> persistArticle(adminTokenVO, createArticleRequest, previousLog, true));
            }
            return persistArticle(adminTokenVO, createArticleRequest, previousLog, false);
        } finally {
            lock.unlock();
        }

    }

    public boolean delete(Long logId) throws SQLException {
        boolean deleted = ArticlePinningService.withOrderLock(() -> {
            boolean removed = new Log().deleteById(Math.toIntExact(logId));
            if (removed) {
                new ArticlePinningService().normalizeOrderLocked();
            }
            return removed;
        });
        if (deleted) {
            removeAIMessage(logId);
        }
        return deleted;
    }

    private CreateOrUpdateArticleResponse persistArticle(AdminTokenVO adminTokenVO,
                                                         CreateArticleRequest createArticleRequest,
                                                         Map<String, Object> oldLog,
                                                         boolean normalizePinning) throws SQLException {
        Map<String, Object> log = getLog(adminTokenVO, createArticleRequest);
        if (createArticleRequest instanceof UpdateArticleRequest) {
            UpdateArticleRequest updateRequest = (UpdateArticleRequest) createArticleRequest;
            Number dbVersion = (Number) log.get("version");
            if (dbVersion.longValue() > updateRequest.getVersion()) {
                throw new UpdateArticleExpireException();
            }
            log.put("version", updateRequest.getVersion() + 1);
            Log logDao = new Log();
            log.forEach((key, value) -> {
                if (Objects.equals(key, "logId")
                        || (Objects.equals(key, "sticky") && !normalizePinning)) {
                    return;
                }
                logDao.set(key, value);
            });
            logDao.updateById(updateRequest.getLogId());
            articleVersionService.recordReversePatch(oldLog, log, adminTokenVO.getUserId());
            if (normalizePinning) {
                new ArticlePinningService().normalizeOrderLocked();
            }
        } else {
            Log dbLog = new Log();
            dbLog.getAttrs().putAll(log);
            dbLog.save();
        }
        CreateOrUpdateArticleResponse response = new CreateOrUpdateArticleResponse();
        response.setLogId((long) Double.parseDouble(log.get("logId") + ""));
        response.setPrivacy(createArticleRequest.isPrivacy());
        response.setRubbish(createArticleRequest.isRubbish());
        response.setPublicCacheRefreshRequired(
                isPublicArticle(oldLog)
                        || (!createArticleRequest.isPrivacy() && !createArticleRequest.isRubbish()));
        return response;
    }

    private static boolean isPublicArticle(Map<String, Object> log) {
        return log != null
                && !ResultValueConvertUtils.toBoolean(log.get("privacy"))
                && !ResultValueConvertUtils.toBoolean(log.get("rubbish"));
    }

    private void migrateDraftAIMessage(Long articleId) {
        try {
            new WebSiteService().migrateDraftAIMessageToArticle(articleId);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Migrate draft article AI messages failed, articleId=" + articleId, e);
        }
    }

    private void removeAIMessage(Long articleId) {
        try {
            new WebSiteService().removeAIMessage(articleId);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Remove article AI messages failed, articleId=" + articleId, e);
        }
    }

    private Map<String, Object> getLog(AdminTokenVO adminTokenVO, CreateArticleRequest createArticleRequest) throws SQLException {
        Map<String, Object> log;
        long articleId;
        if (createArticleRequest instanceof UpdateArticleRequest) {
            log = new Log().loadById(((UpdateArticleRequest) createArticleRequest).getLogId());
            articleId = Objects.requireNonNull(((UpdateArticleRequest) createArticleRequest).getLogId());
        } else {
            log = new HashMap<>();
            articleId = new Log().findMaxId() + 1;
            log.put("releaseTime", new Date());
            log.put("version", 0);
            log.put("logId", articleId);
            log.put("sticky", 0);
        }
        String content = createArticleRequest.getContent();
        if (StringUtils.isEmpty(content) && Objects.equals("markdown", createArticleRequest.getEditorType())) {
            content = MarkdownRendererHolder.INSTANCE.render(createArticleRequest.getMarkdown());
        }
        log.put("content", content);
        log.put("title", Jsoup.clean(createArticleRequest.getTitle(), Safelist.basic()));
        if (StringUtils.isNotEmpty(createArticleRequest.getKeywords())) {
            log.put("keywords", Jsoup.clean(createArticleRequest.getKeywords(), Safelist.basic()));
        } else {
            log.put("keywords", null);
        }
        log.put("markdown", createArticleRequest.getMarkdown());
        log.put("userId", adminTokenVO.getUserId());
        log.put("typeId", createArticleRequest.getTypeId());
        log.put("last_update_date", new Date());
        log.put("canComment", createArticleRequest.isCanComment());
        log.put("recommended", createArticleRequest.isRecommended());
        log.put("privacy", createArticleRequest.isPrivacy());
        log.put("rubbish", createArticleRequest.isRubbish());
        if (createArticleRequest.isPrivacy() || createArticleRequest.isRubbish()) {
            log.put("sticky", 0);
        }
        if (StringUtils.isEmpty(createArticleRequest.getThumbnail())) {
            log.put("thumbnail", "");
        } else {
            log.put("thumbnail", createArticleRequest.getThumbnail());
        }
        //fix digest xss
        String parseInputDigest = Jsoup.clean(ObjectHelpers.requireNonNullElse(createArticleRequest.getDigest(), ""), Safelist.basicWithImages());
        // 自动摘要
        if (StringUtils.isEmpty(parseInputDigest) && Objects.equals(createArticleRequest.isRubbish(), false)) {
            long autoSize = AdminConstants.getAutoDigestLength();
            if (autoSize < 0) {
                log.put("digest", log.get("content"));
            } else if (autoSize == 0) {
                log.put("digest", "");
            } else {
                log.put("digest", ParseUtil.autoDigest((String) log.get("content"), (int) autoSize));
            }
        } else {
            log.put("digest", parseInputDigest);
        }
        log.put("plain_content", ParseUtil.getPlainSearchText((String) log.get("content")));
        log.put("editor_type", createArticleRequest.getEditorType());
        String alias;
        if (StringUtils.isEmpty(createArticleRequest.getAlias())) {
            alias = Long.toString(articleId);
        } else {
            alias = createArticleRequest.getAlias();
        }
        log.put("alias", Jsoup.clean(alias.trim().replace(" ", "-").replace(".", "-"), Safelist.basic()));
        return log;
    }


    private static String getAccessUrl(ArticleResponseEntry articleResponseEntry, HttpRequest request) {
        if (articleResponseEntry.getPrivacy() || articleResponseEntry.getRubbish()) {
            return "/article-edit?previewMode=true&id=" + articleResponseEntry.getId();
        }
        String key = articleResponseEntry.getId() + "";
        if (StringUtils.isNotEmpty(articleResponseEntry.getAlias())) {
            key = articleResponseEntry.getAlias();
        }
        return ZrLogUtil.getHomeUrlWithHost(request) + Constants.getArticleUri() + key + StaticSitePlugin.getSuffix(request);
    }

    /**
     * 将输入的分页过后的对象，转化PageableResponse的对象
     */
    private PageData<ArticleResponseEntry> convertPageable(PageData<ArticleBasicDTO> object, HttpRequest request) {
        List<ArticleResponseEntry> dataList = new ArrayList<>();
        for (ArticleBasicDTO obj : object.getRows()) {
            String lastUpdateDate = obj.getLastUpdateDate();
            ArticleResponseEntry entry = BeanUtil.convert(obj, ArticleResponseEntry.class);
            entry.setUrl(getAccessUrl(entry, request));
            entry.setLastUpdateDate(formatDateValue(lastUpdateDate, "yyyy-MM-dd"));
            entry.setReleaseTime(formatDateValue(obj.getReleaseTime(), "yyyy-MM-dd"));
            dataList.add(entry);
        }
        PageData<ArticleResponseEntry> pageData = new PageData<>();
        pageData.setTotalElements(object.getTotalElements());
        pageData.setRows(dataList);
        pageData.setPage(object.getPage());
        pageData.setSize(object.getSize());
        pageData.setSort(object.getSort());
        return pageData;
    }

    public ArticleStatusCountResponse getStatusCounts() {
        ArticleStatusCountResponse counts = new ArticleStatusCountResponse();
        try {
            Log log = new Log();
            Map<String, Object> row = log.queryFirstWithParams(
                    "SELECT "
                            + "count(1) AS totalCount,"
                            + "SUM(CASE WHEN l.rubbish = ? AND l.privacy = ? THEN 1 ELSE 0 END) AS publishedCount,"
                            + "SUM(CASE WHEN l.privacy = ? THEN 1 ELSE 0 END) AS privateCount,"
                            + "SUM(CASE WHEN l.rubbish = ? THEN 1 ELSE 0 END) AS draftCount "
                            + "FROM " + Log.TABLE_NAME + " l "
                            + "inner join user u on u.userId = l.userId "
                            + "inner join type t on t.typeId = l.typeId "
                            + "where l.typeId is not null",
                    false, false, true, true);
            counts.setTotal(toLong(row, "totalCount"));
            counts.setPublished(toLong(row, "publishedCount"));
            counts.setPrivateCount(toLong(row, "privateCount"));
            counts.setDraft(toLong(row, "draftCount"));
        } catch (SQLException e) {
            LOGGER.warning("Query article counts error: " + e.getMessage());
        }
        return counts;
    }

    private static long toLong(Map<String, Object> row, String key) {
        if (row == null) {
            return 0L;
        }
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toLowerCase(Locale.ROOT));
        }
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    public ArticlePageData adminPage(PageRequest pageRequest, String keywords, String typeAlias, String status, HttpRequest request) {
        ExecutorService executorService = ThreadUtils.newFixedThreadPool(3);
        try {
            CompletableFuture<PageData<ArticleBasicDTO>> dataCompletableFuture = CompletableFuture.supplyAsync(() -> {
                return new Log().adminFind(pageRequest, keywords, typeAlias, status);
            }, executorService);
            CompletableFuture<List<TypeDTO>> listCompletableFuture = CompletableFuture.supplyAsync(() -> {
                return Constants.zrLogConfig.getCacheService().getArticleTypes();
            }, executorService);
            // 统计各状态数量
            CompletableFuture<ArticleStatusCountResponse> countFuture = CompletableFuture.supplyAsync(this::getStatusCounts, executorService);
            CompletableFuture.allOf(listCompletableFuture, dataCompletableFuture, countFuture).join();
            PageData<ArticleResponseEntry> articleResponseEntryPageData = convertPageable(dataCompletableFuture.join(), request);
            ArticlePageData convert = BeanUtil.convert(articleResponseEntryPageData, ArticlePageData.class);
            convert.setTypes(listCompletableFuture.join());
            convert.setKey(keywords);
            convert.setStatus(status);
            convert.setStatusCounts(countFuture.join());
            convert.setArticle_thumbnail_status(AdminConstants.getPublicWebSiteInfo().getArticle_thumbnail_status());
            convert.setDefaultPageSize(pageRequest.getSize());
            return convert;
        } finally {
            executorService.shutdown();
        }
    }

    public CompletableFuture<List<ArticleActivityData>> activityDataList(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> adminArticleData;
            try {
                adminArticleData = getRecentArticleActivityData();
            } catch (SQLException e) {
                LOGGER.warning("Query activityDataList error," + e.getMessage());
                adminArticleData = new HashMap<>();
            }
            return adminArticleData.entrySet().stream().map(e -> {
                return new ArticleActivityData(e.getKey(), e.getValue());
            }).collect(Collectors.toList());
        }, executor);
    }

    private Map<String, Long> getRecentArticleActivityData() throws SQLException {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        List<Map<String, Object>> rows = new Log().queryListWithParams(
                "select releaseTime from " + Log.TABLE_NAME + " where releaseTime >= ? order by releaseTime desc",
                ResultValueConvertUtils.formatDate(calendar.getTime(), "yyyy-MM-dd"));
        Map<String, Long> archives = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("releaseTime");
            if (Objects.isNull(value)) {
                continue;
            }
            String key = ResultValueConvertUtils.formatDate(value, "yyyy-MM-dd");
            archives.put(key, archives.getOrDefault(key, 0L) + 1);
        }
        return archives;
    }

    public AdminPageDataResponse<ArticleGlobalResponse> loadDetailById(String id, HttpRequest request) throws SQLException {
        ArticleGlobalResponse response = new ArticleGlobalResponse();
        ExecutorService executorService = ThreadUtils.newFixedThreadPool(2);
        if (StringUtils.isNotEmpty(id)) {
            response.setArticle(loadDetail(id, request));
        } else {
            response.setArticle(new LoadEditArticleResponse());
        }
        try {
            CompletableFuture<WebSiteService.ArticleEditorContext> articleEditorContext = CompletableFuture.supplyAsync(() -> {
                Integer articleId = response.getArticle().getId();
                if (articleId == null) {
                    articleId = 0;
                }
                return new WebSiteService().articleEditorContext(Long.valueOf(articleId));
            }, executorService);
            CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
                response.setTags(Constants.zrLogConfig.getCacheService().getTags());
            }, executorService), CompletableFuture.runAsync(() -> {
                response.setTypes(Constants.zrLogConfig.getCacheService().getArticleTypes());
            }, executorService), articleEditorContext).join();
            WebSiteService.ArticleEditorContext context = articleEditorContext.join();
            AIWebSiteInfoWithAIMessages ai = context.getAi();
            response.setAiProvider(ai.getAi_provider());
            response.setAiModel(ai.getAi_model());
            response.setAiConfigured(ai.getAi_provider() != null && StringUtils.isNotEmpty(ai.getAi_model())
                    && StringUtils.isNotEmpty(ai.getAi_api_key()));
            response.setAiMessages(ai.getAiMessages().stream().filter(e -> !Objects.equals(e.getRole(), "system")).collect(Collectors.toList()));
            ArticleEditWebSiteInfo articleEdit = context.getArticleEdit();
            response.setLinkPreviewEnabled(articleEdit.getArticle_editor_link_preview_enabled());
            response.setPublishCheckEnabled(articleEdit.getArticle_publish_check_enabled());
            response.setArticleCoverAspectRatio(articleEdit.getArticle_cover_aspect_ratio());
            response.setArticleEditAutoSaveInterval(articleEdit.getArticle_edit_auto_save_interval());
        } finally {
            executorService.shutdown();
        }
        AdminPageDataResponse<ArticleGlobalResponse> standardResponse = new AdminPageDataResponse<>(response);
        StringJoiner sj = new StringJoiner(AdminConstants.ADMIN_TITLE_CHAR);
        if (Objects.nonNull(response.getArticle().getTitle())) {
            sj.add(response.getArticle().getTitle());
        }
        sj.add(AdminConstants.getAdminDocumentTitleByUri(request.getUri()));
        standardResponse.setDocumentTitle(sj.toString());
        return standardResponse;
    }


    private String getPreviewUrl(LoadEditArticleResponse articleResponseEntry, HttpRequest request) {
        if (request == null) {
            return null;
        }
        String key = articleResponseEntry.getLogId() + "";
        if (StringUtils.isNotEmpty(articleResponseEntry.getAlias())) {
            key = articleResponseEntry.getAlias();
        }
        if (articleResponseEntry.isPrivacy() || articleResponseEntry.isRubbish()) {
            return AdminConstants.ADMIN_URI_BASE_PATH + "/403?message=" + I18nUtil.getAdminBackendStringFromRes("admin.article.preview.error.forbidden");
        }
        return ZrLogUtil.getHomeUrlWithHost(request) + Constants.getArticleUri() + key + StaticSitePlugin.getSuffix(request) + "?v=" + articleResponseEntry.getVersion();
    }

    private LoadEditArticleResponse toResponse(ArticleBasicDTO log, HttpRequest request) {
        Long lastUpdateDate = parseDateValue(log.getLastUpdateDate());
        //remove convert error str
        log.setLastUpdateDate(null);
        LoadEditArticleResponse loadEditArticleResponse = BeanUtil.convert(log, LoadEditArticleResponse.class);
        if (Objects.isNull(loadEditArticleResponse.getDigest())) {
            loadEditArticleResponse.setDigest("");
        }
        if (Objects.isNull(loadEditArticleResponse.getContent())) {
            loadEditArticleResponse.setContent("");
        }
        loadEditArticleResponse.setLastUpdateDate(lastUpdateDate);
        loadEditArticleResponse.setPreviewUrl(getPreviewUrl(loadEditArticleResponse, request));
        loadEditArticleResponse.setSocialPreview(buildSocialPreview(loadEditArticleResponse, request));
        return loadEditArticleResponse;
    }

    private SocialPreviewDTO buildSocialPreview(LoadEditArticleResponse loadEditArticleResponse, HttpRequest request) {
        PublicWebSiteInfo webSite = AdminConstants.getPublicWebSiteInfo();
        ArticleBasicDTO article = new ArticleBasicDTO();
        article.setTitle(loadEditArticleResponse.getTitle());
        article.setDigest(loadEditArticleResponse.getDigest());
        article.setContent(loadEditArticleResponse.getContent());
        article.setMarkdown(loadEditArticleResponse.getMarkdown());
        article.setPlain_content(ParseUtil.getPlainSearchText(
                ObjectHelpers.requireNonNullElse(loadEditArticleResponse.getContent(), "")));
        article.setThumbnail(loadEditArticleResponse.getThumbnail());
        String title = buildSocialPreviewTitle(loadEditArticleResponse.getTitle(), webSite);
        String url = StringUtils.isNotEmpty(loadEditArticleResponse.getPreviewUrl())
                ? loadEditArticleResponse.getPreviewUrl()
                : ZrLogUtil.getFullUrl(request);
        return SocialPreviewUtils.article(webSite, article, title, url, loadEditArticleResponse.getThumbnail());
    }

    private String buildSocialPreviewTitle(String articleTitle, PublicWebSiteInfo webSite) {
        StringJoiner sj = new StringJoiner(AdminConstants.ADMIN_TITLE_CHAR);
        if (StringUtils.isNotEmpty(articleTitle)) {
            sj.add(articleTitle);
        }
        if (webSite != null && StringUtils.isNotEmpty(webSite.getTitle())) {
            sj.add(webSite.getTitle());
        }
        return sj.toString();
    }

    public LoadEditArticleResponse loadDetail(String id, HttpRequest request) throws SQLException {
        ArticleBasicDTO log = new Log().adminFindByIdOrAlias(id);
        if (log == null) {
            throw new NotFindDbEntryException();
        }
        return toResponse(log, request);
    }

    private static String formatDateValue(Object date, String format) {
        Long time = parseDateValue(date);
        if (Objects.isNull(time)) {
            if (Objects.isNull(date)) {
                return "";
            }
            return date.toString();
        }
        return new SimpleDateFormat(format).format(new Date(time));
    }

    private static Long parseDateValue(Object date) {
        try {
            return ResultValueConvertUtils.parseDate(date);
        } catch (RuntimeException e) {
            Long parsed = parseGsonDateString(date);
            if (parsed != null) {
                return parsed;
            }
            throw e;
        }
    }

    private static Long parseGsonDateString(Object date) {
        if (!(date instanceof String)) {
            return null;
        }
        String value = ((String) date).replace('\u202f', ' ').replace('\u00a0', ' ');
        List<DateFormat> formats = Arrays.asList(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault()),
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.ENGLISH),
                new SimpleDateFormat("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH)
        );
        for (DateFormat format : formats) {
            try {
                return format.parse(value).getTime();
            } catch (java.text.ParseException ignored) {
            }
        }
        return null;
    }
}
