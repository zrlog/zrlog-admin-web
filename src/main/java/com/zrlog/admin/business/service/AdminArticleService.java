package com.zrlog.admin.business.service;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.*;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpFileHandle;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.ArticleMissingTitleException;
import com.zrlog.admin.business.exception.ArticleMissingTypeException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.util.ArticleHelpers;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.exception.NotFindDbEntryException;
import com.zrlog.common.exception.ResourceLockedException;
import com.zrlog.common.exception.UnknownException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.model.Log;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ParseUtil;
import com.zrlog.util.ThreadUtils;
import com.zrlog.util.ZrLogUtil;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

    private Lock getWriteLock(AdminTokenVO adminTokenVO, Integer logId) {
        return new DistributedLock("write_article_" + adminTokenVO.getSessionId() + "_" + ObjectUtil.requireNonNullElse(logId, Integer.MAX_VALUE));
    }

    private static String getFirstImgUrl(String htmlContent, AdminTokenVO adminTokenVO) {
        if (StringUtils.isEmpty(htmlContent)) {
            return "";
        }
        Elements elements = Jsoup.parse(htmlContent).select("img");
        if (elements.isEmpty()) {
            return null;
        }
        String url = elements.first().attr("src");
        try {
            String path = url;
            byte[] bytes;
            if (url.startsWith("https://") || url.startsWith("http://")) {
                path = URI.create(url).getPath();
                if (!path.startsWith(AdminConstants.ATTACHED_FOLDER)) {
                    path = (AdminConstants.ATTACHED_FOLDER + path).replace("//", "/");
                } else {
                    path = path.replace("//", "/");
                }
                bytes = getRequestBodyBytes(url);
                path = path.substring(0, path.indexOf('.')) + "_thumbnail" + path.substring(path.indexOf('.'));
            } else {
                bytes = IOUtil.getByteByInputStream(new FileInputStream(PathUtil.getStaticFile(url)));
                path = url.substring(0, url.indexOf('.')) + "_thumbnail" + url.substring(path.indexOf('.'));
            }
            File thumbnailFile = PathUtil.getStaticFile(path);
            if (bytes.length == 0) {
                return null;
            }
            int height = -1;
            int width = -1;
            //创建文件夹，避免保存失败
            thumbnailFile.getParentFile().mkdirs();
            IOUtil.writeBytesToFile(bytes, thumbnailFile);
            return new UploadService().getCloudUrl("", path, thumbnailFile.getPath(), null,
                    adminTokenVO).getUrl() + "?h=" + height + "&w=" + width;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        return null;
    }

    private static byte[] getRequestBodyBytes(String url) throws IOException, URISyntaxException, InterruptedException {
        HttpFileHandle fileHandler = new HttpFileHandle("");
        HttpUtil.getInstance().sendGetRequest(url, new HashMap<>(), fileHandler, new HashMap<>());
        return IOUtil.getByteByInputStream(new FileInputStream(fileHandler.getT().getPath()));
    }

    public CreateOrUpdateArticleResponse create(AdminTokenVO adminTokenVO, CreateArticleRequest createArticleRequest) throws SQLException {
        return save(adminTokenVO, createArticleRequest);
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
            Map<String, Object> log = getLog(adminTokenVO, createArticleRequest);
            if (createArticleRequest instanceof UpdateArticleRequest) {
                Number dbVersion = (Number) log.get("version");
                if (dbVersion.longValue() > ((UpdateArticleRequest) createArticleRequest).getVersion()) {
                    throw new UpdateArticleExpireException();
                }
                log.put("version", ((UpdateArticleRequest) createArticleRequest).getVersion() + 1);
                Log logDao = new Log();
                log.forEach((key, value) -> {
                    if (Objects.equals(key, "logId")) {
                        return;
                    }
                    logDao.set(key, value);
                });
                logDao.updateById(((UpdateArticleRequest) createArticleRequest).getLogId());
                articleVersionService.recordReversePatch(oldLog, log, adminTokenVO.getUserId());
            } else {
                Log dbLog = new Log();
                dbLog.getAttrs().putAll(log);
                dbLog.save();
            }
            CreateOrUpdateArticleResponse response = new CreateOrUpdateArticleResponse();
            response.setLogId((long) Double.parseDouble(log.get("logId") + ""));
            response.setPrivacy(createArticleRequest.isPrivacy());
            response.setRubbish(createArticleRequest.isRubbish());
            return response;
        } finally {
            lock.unlock();
        }

    }

    public boolean delete(Long logId) throws SQLException {
        return new Log().deleteById(Math.toIntExact(logId));
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
        }
        log.put("content", createArticleRequest.getContent());
        log.put("title", Jsoup.clean(createArticleRequest.getTitle(), Safelist.basic()));
        if (StringUtils.isNotEmpty(createArticleRequest.getKeywords())) {
            log.put("keywords", Jsoup.clean(createArticleRequest.getKeywords(), Safelist.basic()));
        } else {
            log.put("keywords", null);
        }
        log.put("markdown", createArticleRequest.getMarkdown());
        log.put("content", createArticleRequest.getContent());
        log.put("userId", adminTokenVO.getUserId());
        log.put("typeId", createArticleRequest.getTypeId());
        log.put("last_update_date", new Date());
        log.put("canComment", createArticleRequest.isCanComment());
        log.put("recommended", createArticleRequest.isRecommended());
        log.put("privacy", createArticleRequest.isPrivacy());
        log.put("rubbish", createArticleRequest.isRubbish());
        if (StringUtils.isEmpty(createArticleRequest.getThumbnail())) {
            log.put("thumbnail", getFirstImgUrl(createArticleRequest.getContent(), adminTokenVO));
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
            entry.setLastUpdateDate(ResultValueConvertUtils.formatDate(lastUpdateDate, "yyyy-MM-dd"));
            entry.setReleaseTime(ResultValueConvertUtils.formatDate(obj.getReleaseTime(), "yyyy-MM-dd"));
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
            counts.setTotal(log.getAdminCount());
            counts.setPublished(((Number) log.queryFirstObj("SELECT count(*) FROM " + Log.TABLE_NAME + " WHERE rubbish = ? AND privacy = ?", false, false)).longValue());
            counts.setPrivateCount(((Number) log.queryFirstObj("SELECT count(*) FROM " + Log.TABLE_NAME + " WHERE privacy = ?", true)).longValue());
            counts.setDraft(((Number) log.queryFirstObj("SELECT count(*) FROM " + Log.TABLE_NAME + " WHERE rubbish = ?", true)).longValue());
        } catch (SQLException e) {
            LOGGER.warning("Query article counts error: " + e.getMessage());
        }
        return counts;
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
            ArticleHelpers.wrapperSearchKeyword(dataCompletableFuture.join(), keywords);
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
                adminArticleData = new Log().getAdminArticleData();
            } catch (SQLException e) {
                LOGGER.warning("Query activityDataList error," + e.getMessage());
                adminArticleData = new HashMap<>();
            }
            return adminArticleData.entrySet().stream().map(e -> {
                return new ArticleActivityData(e.getKey(), e.getValue());
            }).collect(Collectors.toList());
        }, executor);
    }

    public AdminApiPageDataStandardResponse<ArticleGlobalResponse> loadDetailById(String id, HttpRequest request) throws SQLException {
        ArticleGlobalResponse response = new ArticleGlobalResponse();
        ExecutorService executorService = ThreadUtils.newFixedThreadPool(2);
        if (StringUtils.isNotEmpty(id)) {
            response.setArticle(loadDetail(id, request));
        } else {
            response.setArticle(new LoadEditArticleResponse());
        }
        try {
            CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
                response.setTags(Constants.zrLogConfig.getCacheService().getTags());
            }, executorService), CompletableFuture.runAsync(() -> {
                response.setTypes(Constants.zrLogConfig.getCacheService().getArticleTypes());
            }, executorService), CompletableFuture.runAsync(() -> {
                Integer articleId = response.getArticle().getId();
                if (articleId == null) {
                    articleId = 0;
                }
                AIWebSiteInfoWithAIMessages ai = new WebSiteService().getAiMessageInfoByArticleId(Long.valueOf(articleId));
                response.setAiProvider(ai.getAi_provider());
                response.setAiMessages(ai.getAiMessages().stream().filter(e -> !Objects.equals(e.getRole(), "system")).collect(Collectors.toList()));
            })).join();
        } finally {
            executorService.shutdown();
        }
        AdminApiPageDataStandardResponse<ArticleGlobalResponse> standardResponse = new AdminApiPageDataStandardResponse<>(response);
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
            return AdminConstants.ADMIN_URI_BASE_PATH + "/403?message=" + I18nUtil.getAdminBackendStringFromRes("preview403");
        }
        return ZrLogUtil.getHomeUrlWithHost(request) + Constants.getArticleUri() + key + StaticSitePlugin.getSuffix(request) + "?v=" + articleResponseEntry.getVersion();
    }

    private LoadEditArticleResponse toResponse(ArticleBasicDTO log, HttpRequest request) {
        Long lastUpdateDate = ResultValueConvertUtils.parseDate(log.getLastUpdateDate());
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
        return loadEditArticleResponse;
    }

    public LoadEditArticleResponse loadDetail(String id, HttpRequest request) throws SQLException {
        ArticleBasicDTO log = new Log().adminFindByIdOrAlias(id);
        if (log == null) {
            throw new NotFindDbEntryException();
        }
        return toResponse(log, request);
    }
}
