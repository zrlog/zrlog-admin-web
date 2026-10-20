package com.zrlog.admin.business.service;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.ObjectHelpers;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.base.UserPreferences;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.exception.NotFindDbEntryException;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.common.vo.SocialPreviewDTO;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.util.SocialPreviewUtils;
import com.zrlog.model.Log;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ParseUtil;
import com.zrlog.util.ThreadUtils;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Singleton
public class ArticleQueryService {
    private static final Logger LOGGER = LoggerUtil.getLogger(ArticleQueryService.class);
    private final CacheService cacheService;
    private final Supplier<ArticleAssistant> assistant;
    private final AdminPageDataFactory pages;
    private final AccountPermissionService permissionService;
    private final UserPreferenceService userPreferenceService;
    private final jakarta.inject.Provider<Log> logProvider;

    @Inject
    public ArticleQueryService(
            CacheService cacheService,
            Supplier<ArticleAssistant> assistant,
            AdminPageDataFactory pages,
            AccountPermissionService permissionService,
            UserPreferenceService userPreferenceService,
            jakarta.inject.Provider<Log> logProvider) {
        this.cacheService = cacheService;
        this.assistant = Objects.requireNonNull(assistant);
        this.pages = pages;
        this.permissionService = permissionService;
        this.userPreferenceService = userPreferenceService;
        this.logProvider = logProvider;
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
        List<DateFormat> formats =
                Arrays.asList(
                        DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault()),
                        DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.ENGLISH),
                        new SimpleDateFormat("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH));
        for (DateFormat format : formats) {
            try {
                return format.parse(value).getTime();
            } catch (java.text.ParseException ignored) {
            }
        }
        return null;
    }

    public int resolveAdminPageSize(AccountAccess access, int requestedSize) {
        if (requestedSize > 0) {
            return requestedSize;
        }
        return this.userPreferenceService.effective(access).articlePageSize;
    }

    private String getAccessUrl(ArticleResponseEntry articleResponseEntry, HttpRequest request) {
        if (articleResponseEntry.getPrivacy() || articleResponseEntry.getRubbish()) {
            return "/article-edit?previewMode=true&id=" + articleResponseEntry.getId();
        }
        String key = articleResponseEntry.getId() + "";
        if (StringUtils.isNotEmpty(articleResponseEntry.getAlias())) {
            key = articleResponseEntry.getAlias();
        }
        return ZrLogUtil.getHomeUrlWithHost(request, cacheService.getPublicWebSiteInfo().getHost())
                + Constants.getArticleUri()
                + key
                + StaticSitePlugin.getSuffix(
                request,
                Boolean.TRUE.equals(
                        cacheService.getPublicWebSiteInfo().getGenerator_html_status()));
    }

    /**
     * 将输入的分页过后的对象，转化PageableResponse的对象
     */
    private PageData<ArticleResponseEntry> convertPageable(
            PageData<ArticleBasicDTO> object, HttpRequest request) {
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

    public ArticleStatusCountResponse getStatusCounts(AccountAccess access) {
        if (access == null || !access.isEnabled()) throw new PermissionErrorException();
        return getStatusCounts(
                access.managesAllArticles() ? null : access.getUserId(),
                access.isAdministrator() ? null : access.getUserId());
    }

    private ArticleStatusCountResponse getStatusCounts(Integer authorId, Integer privateOwnerId) {
        ArticleStatusCountResponse counts = new ArticleStatusCountResponse();
        try {
            Log log = this.logProvider.get();
            List<Object> countParams = new ArrayList<>(Arrays.asList(false, false, true, true));
            if (authorId != null) countParams.add(authorId);
            if (privateOwnerId != null) {
                countParams.add(false);
                countParams.add(privateOwnerId);
            }
            Map<String, Object> row =
                    log.queryFirstWithParams(
                            "SELECT "
                                    + "count(1) AS totalCount,"
                                    + "SUM(CASE WHEN l.rubbish = ? AND l.privacy = ? THEN 1 ELSE 0 END) AS publishedCount,"
                                    + "SUM(CASE WHEN l.privacy = ? THEN 1 ELSE 0 END) AS privateCount,"
                                    + "SUM(CASE WHEN l.rubbish = ? THEN 1 ELSE 0 END) AS draftCount "
                                    + "FROM "
                                    + Log.TABLE_NAME
                                    + " l "
                                    + "inner join user u on u.userId = l.userId "
                                    + "inner join type t on t.typeId = l.typeId "
                                    + "where l.typeId is not null"
                                    + (authorId == null ? "" : " and l.userId=?")
                                    + (privateOwnerId == null
                                    ? ""
                                    : " and (l.privacy=? or l.userId=?)"),
                            countParams.toArray());
            counts.setTotal(toLong(row, "totalCount"));
            counts.setPublished(toLong(row, "publishedCount"));
            counts.setPrivateCount(toLong(row, "privateCount"));
            counts.setDraft(toLong(row, "draftCount"));
        } catch (SQLException e) {
            LOGGER.warning("Query article counts error: " + e.getMessage());
        }
        return counts;
    }

    public ArticlePageData adminPage(
            AccountAccess access,
            PageRequest pageRequest,
            String keywords,
            String typeAlias,
            String status,
            HttpRequest request) {
        if (access == null || !access.isEnabled()) throw new PermissionErrorException();
        Integer authorId = access.managesAllArticles() ? null : access.getUserId();
        Integer privateOwnerId = access.isAdministrator() ? null : access.getUserId();
        ExecutorService executorService = ThreadUtils.newFixedThreadPool(3);
        try {
            CompletableFuture<PageData<ArticleBasicDTO>> dataCompletableFuture =
                    CompletableFuture.supplyAsync(
                            () -> {
                                return this.logProvider
                                        .get()
                                        .adminFind(
                                                pageRequest,
                                                keywords,
                                                typeAlias,
                                                status,
                                                authorId,
                                                privateOwnerId);
                            },
                            executorService);
            CompletableFuture<List<TypeDTO>> listCompletableFuture =
                    CompletableFuture.supplyAsync(
                            () -> {
                                return cacheService.getArticleTypes();
                            },
                            executorService);
            // 统计各状态数量
            CompletableFuture<ArticleStatusCountResponse> countFuture =
                    CompletableFuture.supplyAsync(
                            () -> getStatusCounts(authorId, privateOwnerId), executorService);
            CompletableFuture.allOf(listCompletableFuture, dataCompletableFuture, countFuture)
                    .join();
            PageData<ArticleResponseEntry> articleResponseEntryPageData =
                    convertPageable(dataCompletableFuture.join(), request);
            ArticlePageData convert =
                    BeanUtil.convert(articleResponseEntryPageData, ArticlePageData.class);
            convert.setTypes(listCompletableFuture.join());
            convert.setKey(keywords);
            convert.setStatus(status);
            convert.setStatusCounts(countFuture.join());
            convert.setArticle_thumbnail_status(
                    cacheService.getPublicWebSiteInfo().getArticle_thumbnail_status());
            convert.setDefaultPageSize(pageRequest.getSize());
            return convert;
        } finally {
            executorService.shutdown();
        }
    }

    public CompletableFuture<List<ArticleActivityData>> activityDataList(Executor executor) {
        return CompletableFuture.supplyAsync(
                () -> {
                    Map<String, Long> adminArticleData;
                    try {
                        adminArticleData = getRecentArticleActivityData();
                    } catch (SQLException e) {
                        LOGGER.warning("Query activityDataList error," + e.getMessage());
                        adminArticleData = new HashMap<>();
                    }
                    return adminArticleData.entrySet().stream()
                            .map(
                                    e -> {
                                        return new ArticleActivityData(e.getKey(), e.getValue());
                                    })
                            .collect(Collectors.toList());
                },
                executor);
    }

    private Map<String, Long> getRecentArticleActivityData() throws SQLException {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        List<Map<String, Object>> rows =
                this.logProvider
                        .get()
                        .queryListWithParams(
                                "select releaseTime from "
                                        + Log.TABLE_NAME
                                        + " where releaseTime >= ? order by releaseTime desc",
                                ResultValueConvertUtils.formatDate(
                                        calendar.getTime(), "yyyy-MM-dd"));
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

    public AdminPageDataResponse<ArticleGlobalResponse> loadDetailById(
            AccountAccess access, String id, HttpRequest request) throws SQLException {
        if (access == null || !access.isEnabled()) throw new PermissionErrorException();
        int draftContextId = -access.getUserId();
        ArticleGlobalResponse response = new ArticleGlobalResponse();
        response.setArticle(
                StringUtils.isNotEmpty(id)
                        ? loadDetail(access, id, request)
                        : new LoadEditArticleResponse());
        response.setTags(cacheService.getTags());
        response.setTypes(cacheService.getArticleTypes());
        UserPreferences.Editor editor = this.userPreferenceService.effective(access).editor;
        response.setLinkPreviewEnabled(editor.linkPreviewEnabled);
        response.setPublishCheckEnabled(editor.publishCheckEnabled);
        response.setArticleCoverAspectRatio(editor.coverAspectRatio);
        response.setArticleEditAutoSaveInterval(editor.autoSaveInterval);
        Integer articleId = response.getArticle().getId();
        response =
                Objects.requireNonNullElse(assistant.get(), ArticleAssistant.NONE)
                        .enrichEditor(response, articleId == null ? draftContextId : articleId, access.getUserId());
        AdminPageDataResponse<ArticleGlobalResponse> standardResponse = pages.response(access, response);
        StringJoiner sj = new StringJoiner(AdminConstants.ADMIN_TITLE_CHAR);
        if (Objects.nonNull(response.getArticle().getTitle())) {
            sj.add(response.getArticle().getTitle());
        }
        sj.add(pages.title(request.getUri()));
        standardResponse.setDocumentTitle(sj.toString());
        return standardResponse;
    }

    private String getPreviewUrl(
            LoadEditArticleResponse articleResponseEntry, HttpRequest request) {
        if (request == null) {
            return null;
        }
        String key = articleResponseEntry.getLogId() + "";
        if (StringUtils.isNotEmpty(articleResponseEntry.getAlias())) {
            key = articleResponseEntry.getAlias();
        }
        if (articleResponseEntry.isPrivacy() || articleResponseEntry.isRubbish()) {
            return AdminConstants.ADMIN_URI_BASE_PATH
                    + "/403?message="
                    + I18nUtil.getAdminBackendStringFromRes(
                    "admin.article.preview.error.forbidden");
        }
        return ZrLogUtil.getHomeUrlWithHost(request, cacheService.getPublicWebSiteInfo().getHost())
                + Constants.getArticleUri()
                + key
                + StaticSitePlugin.getSuffix(
                request,
                Boolean.TRUE.equals(
                        cacheService.getPublicWebSiteInfo().getGenerator_html_status()))
                + "?v="
                + articleResponseEntry.getVersion();
    }

    private LoadEditArticleResponse toResponse(ArticleBasicDTO log, HttpRequest request) {
        Long lastUpdateDate = parseDateValue(log.getLastUpdateDate());
        // remove convert error str
        log.setLastUpdateDate(null);
        LoadEditArticleResponse loadEditArticleResponse =
                BeanUtil.convert(log, LoadEditArticleResponse.class);
        loadEditArticleResponse.setArrange_plugin(Objects.toString(log.getArrange_plugin(), ""));
        if (Objects.isNull(loadEditArticleResponse.getDigest())) {
            loadEditArticleResponse.setDigest("");
        }
        if (Objects.isNull(loadEditArticleResponse.getContent())) {
            loadEditArticleResponse.setContent("");
        }
        loadEditArticleResponse.setLastUpdateDate(lastUpdateDate);
        loadEditArticleResponse.setPreviewUrl(getPreviewUrl(loadEditArticleResponse, request));
        loadEditArticleResponse.setSocialPreview(
                buildSocialPreview(loadEditArticleResponse, request));
        return loadEditArticleResponse;
    }

    private SocialPreviewDTO buildSocialPreview(
            LoadEditArticleResponse loadEditArticleResponse, HttpRequest request) {
        PublicWebSiteInfo webSite = cacheService.getPublicWebSiteInfo();
        ArticleBasicDTO article = new ArticleBasicDTO();
        article.setTitle(loadEditArticleResponse.getTitle());
        article.setDigest(loadEditArticleResponse.getDigest());
        article.setContent(loadEditArticleResponse.getContent());
        article.setMarkdown(loadEditArticleResponse.getMarkdown());
        article.setPlain_content(
                ParseUtil.getPlainSearchText(
                        ObjectHelpers.requireNonNullElse(
                                loadEditArticleResponse.getContent(), "")));
        article.setThumbnail(loadEditArticleResponse.getThumbnail());
        String title = buildSocialPreviewTitle(loadEditArticleResponse.getTitle(), webSite);
        String url =
                StringUtils.isNotEmpty(loadEditArticleResponse.getPreviewUrl())
                        ? loadEditArticleResponse.getPreviewUrl()
                        : ZrLogUtil.getFullUrl(request, webSite.getHost());
        return SocialPreviewUtils.article(
                webSite, article, title, url, loadEditArticleResponse.getThumbnail());
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

    public LoadEditArticleResponse loadDetail(AccountAccess access, String id, HttpRequest request) throws SQLException {
        ArticleBasicDTO log = this.logProvider.get().adminFindByIdOrAlias(id);
        if (log == null) {
            throw new NotFindDbEntryException();
        }
        permissionService.readArticle(access, log.getLogId());
        return toResponse(log, request);
    }
}
