package com.zrlog.admin.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.content.ArticleResourceReferences;
import com.zrlog.admin.business.rest.request.ReplaceArticleResourceUrlRequest;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.rest.response.FileReferenceIndexCacheVO;
import com.zrlog.admin.business.rest.response.FileReferenceVO;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.admin.business.util.FileEntryUtils;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.Log;

import jakarta.inject.Inject;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FileManagerReferenceService {
    private final Supplier<ArticleResourceService> articles;
    private final ArticleResourceReferences references;
    private final jakarta.inject.Provider<Log> logProvider;

    private static final Logger LOGGER = LoggerUtil.getLogger(FileManagerReferenceService.class);
    private static final Object REFERENCE_INDEX_CACHE_LOCK = new Object();
    private static final String REFERENCE_INDEX_CACHE_KEY = "file_manager_reference_index";
    private final WebsiteCacheService cacheService;
    private FileReferenceIndexCacheVO requestReferenceIndex;

    @Inject
    public FileManagerReferenceService(
            WebsiteCacheService cacheService,
            String contextPath,
            Supplier<ArticleResourceService> articles,
            jakarta.inject.Provider<Log> logProvider) {
        this.cacheService = cacheService;
        this.references = new ArticleResourceReferences(contextPath);
        this.articles = articles;
        this.logProvider = logProvider;
    }

    public Map<String, List<FileReferenceVO>> buildLocalReferenceMap() throws SQLException {
        return getReferenceIndex().getLocalReferences();
    }

    public Map<String, List<FileReferenceVO>> buildExternalReferenceMap() throws SQLException {
        return getReferenceIndex().getExternalReferences();
    }

    public boolean refreshReferenceIndex() throws SQLException {
        String signature = buildArticleReferenceSignature();
        synchronized (REFERENCE_INDEX_CACHE_LOCK) {
            requestReferenceIndex = rebuildReferenceIndex(signature);
            return writeReferenceIndexCache(requestReferenceIndex);
        }
    }

    public List<FileEntryVO> getExternalResources(String path, boolean referenceEnabled,
                                                  Function<FileEntryVO, FileEntryVO> entryDecorator)
            throws SQLException {
        Map<String, List<FileReferenceVO>> referenceMap = referenceEnabled ? buildExternalReferenceMap()
                : Collections.emptyMap();

        if (path.equals(FileManagerService.EXTERNAL_ROOT)) {
            if (!referenceEnabled) {
                return Collections.emptyList();
            }
            Map<String, Map<Integer, FileReferenceVO>> domainReferenceMap = new LinkedHashMap<>();
            for (Map.Entry<String, List<FileReferenceVO>> entry : referenceMap.entrySet()) {
                mergeReferenceList(domainReferenceMap.computeIfAbsent(getDomain(entry.getKey()),
                        ignored -> new LinkedHashMap<>()), entry.getValue());
            }
            return domainReferenceMap.entrySet().stream().map(entry -> {
                FileEntryVO vo = entryDecorator.apply(new FileEntryVO(entry.getKey(),
                        FileManagerService.EXTERNAL_ROOT + "/" + entry.getKey(), "directory", 0, "", 0));
                setReferences(vo, new ArrayList<>(entry.getValue().values()));
                return vo;
            }).collect(Collectors.toList());
        }

        String targetDomain = path.substring(FileManagerService.EXTERNAL_ROOT.length() + 1);
        return referenceMap.entrySet().stream()
                .filter(entry -> getDomain(entry.getKey()).equals(targetDomain))
                .map(entry -> {
                    FileEntryVO vo = entryDecorator.apply(new FileEntryVO(entry.getKey(), entry.getKey(), "file",
                            0, FileEntryUtils.toMimeType(entry.getKey()), 0));
                    setReferences(vo, entry.getValue());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    public List<FileEntryVO> applyReferenceInfo(List<FileEntryVO> entries,
                                                Map<String, List<FileReferenceVO>> localReferenceMap) {
        for (FileEntryVO entry : entries) {
            if (!"file".equals(entry.getType())) {
                setReferences(entry, getDirectoryReferences(entry, localReferenceMap));
                continue;
            }
            setReferences(entry, localReferenceMap.getOrDefault(normalizePath(entry.getPath()), Collections.emptyList()));
        }
        return entries;
    }

    public List<FileEntryVO> getMissingLocalResources(Function<FileEntryVO, FileEntryVO> entryDecorator)
            throws SQLException {
        Map<String, List<FileReferenceVO>> localReferenceMap = buildLocalReferenceMap();
        List<String> paths = new ArrayList<>(localReferenceMap.keySet());
        Collections.sort(paths);
        List<FileEntryVO> entries = new ArrayList<>();
        for (String path : paths) {
            if (!path.startsWith(FileManagerService.ATTACHED_ROOT + "/") || localResourceExists(path)) {
                continue;
            }
            FileEntryVO entry = new FileEntryVO(getResourceName(path), path, "file", -1,
                    FileEntryUtils.toMimeType(path), 0);
            entry.setVirtual(true);
            entry.setMissing(true);
            entry.setMissingReason("targetMissing");
            FileEntryVO decorated = entryDecorator.apply(entry);
            setReferences(decorated, localReferenceMap.getOrDefault(path, Collections.emptyList()));
            entries.add(decorated);
        }
        return entries;
    }

    public ReplaceArticleResourceUrlResponse replaceArticleResourceUrl(AccountAccess actor,
                                                                       ReplaceArticleResourceUrlRequest request)
            throws SQLException {
        return replaceArticleResourceReferences(actor, request.getFromUrl(), request.getToUrl(),
                !FileEntryUtils.isExternalUrl(request.getFromUrl()));
    }

    public ReplaceArticleResourceUrlResponse replaceArticleResourceReferences(AccountAccess actor, String fromUrl,
                                                                              String toUrl, boolean prefix)
            throws SQLException {
        ReplaceArticleResourceUrlResponse response =
                articles.get().replaceReferences(actor, fromUrl, toUrl, prefix, references);
        if (response.getUpdatedArticles() > 0) {
            requestReferenceIndex = null;
            clearReferenceIndexCache();
        }
        return response;
    }

    public String normalizePath(String value) {
        return references.normalizePath(value);
    }

    private FileReferenceIndexCacheVO getReferenceIndex() throws SQLException {
        if (requestReferenceIndex != null) {
            return requestReferenceIndex;
        }
        String signature = buildArticleReferenceSignature();
        synchronized (REFERENCE_INDEX_CACHE_LOCK) {
            FileReferenceIndexCacheVO cached = readReferenceIndexCache();
            if (cached != null && Objects.equals(signature, cached.getSignature())) {
                requestReferenceIndex = cached;
                return requestReferenceIndex;
            }
            requestReferenceIndex = rebuildReferenceIndex(signature);
            writeReferenceIndexCache(requestReferenceIndex);
            return requestReferenceIndex;
        }
    }

    private FileReferenceIndexCacheVO readReferenceIndexCache() {
        FileReferenceIndexCacheVO cached = cacheService.getJson(REFERENCE_INDEX_CACHE_KEY, FileReferenceIndexCacheVO.class);
        if (cached == null || cached.getLocalReferences() == null || cached.getExternalReferences() == null) {
            return null;
        }
        return cached;
    }

    boolean writeReferenceIndexCache(FileReferenceIndexCacheVO referenceIndex) {
        boolean saved = cacheService.putJson(REFERENCE_INDEX_CACHE_KEY, referenceIndex);
        if (!saved) {
            LOGGER.log(Level.WARNING, "Write file-manager reference index cache failed; using request-scoped index");
        }
        return saved;
    }

    boolean clearReferenceIndexCache() {
        boolean removed = cacheService.remove(REFERENCE_INDEX_CACHE_KEY);
        if (!removed) {
            LOGGER.log(Level.WARNING, "Clear file-manager reference index cache failed");
        }
        return removed;
    }

    private void setReferences(FileEntryVO entry, List<FileReferenceVO> references) {
        entry.setReferences(references);
        entry.setReferenceCount(references.size());
        entry.setReferenced(!references.isEmpty());
    }

    private boolean localResourceExists(String path) {
        if (StringUtils.isEmpty(path) || path.contains("..")) {
            return false;
        }
        File target = PathUtil.getStaticFile(path);
        return target.exists();
    }

    private String getResourceName(String path) {
        int index = path.lastIndexOf('/');
        if (index < 0 || index == path.length() - 1) {
            return path;
        }
        return path.substring(index + 1);
    }

    private List<FileReferenceVO> getDirectoryReferences(FileEntryVO entry,
                                                         Map<String, List<FileReferenceVO>> localReferenceMap) {
        String path = entry.getPath();
        if (StringUtils.isEmpty(path) || !path.startsWith(FileManagerService.ATTACHED_ROOT + "/")) {
            return Collections.emptyList();
        }
        String prefix = normalizePath(path);
        Map<Integer, FileReferenceVO> referenceMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<FileReferenceVO>> localReferenceEntry : localReferenceMap.entrySet()) {
            if (!localReferenceEntry.getKey().startsWith(prefix + "/")) {
                continue;
            }
            mergeReferenceList(referenceMap, localReferenceEntry.getValue());
        }
        return new ArrayList<>(referenceMap.values());
    }

    private String buildArticleReferenceSignature() throws SQLException {
        List<Map<String, Object>> rows =
                this.logProvider
                        .get()
                        .queryList(
                                "count(*) AS article_count, max(logId) AS max_log_id, max(version) AS max_version, max(last_update_date) AS max_last_update_date");
        if (rows.isEmpty()) {
            return "empty";
        }
        Map<String, Object> row = rows.get(0);
        return Objects.toString(row.get("article_count"), "0")
                + ":" + Objects.toString(row.get("max_log_id"), "0")
                + ":" + Objects.toString(row.get("max_version"), "0")
                + ":" + Objects.toString(row.get("max_last_update_date"), "");
    }

    private FileReferenceIndexCacheVO rebuildReferenceIndex(String signature) throws SQLException {
        List<Map<String, Object>> articles =
                this.logProvider
                        .get()
                        .queryList("logId, title, alias, thumbnail, content, markdown");
        Map<String, Map<Integer, FileReferenceVO>> localReferenceMap = new HashMap<>();
        Map<String, Map<Integer, FileReferenceVO>> externalReferenceMap = new LinkedHashMap<>();
        for (Map<String, Object> article : articles) {
            int logId = toInt(article.get("logId"));
            FileReferenceVO articleReference = new FileReferenceVO(logId, Objects.toString(article.get("title"), ""),
                    Objects.toString(article.get("alias"), ""));
            String thumbnail = (String) article.get("thumbnail");
            String normalizedThumbnail = normalizeLocalResourcePath(thumbnail);
            if (normalizedThumbnail != null) {
                FileReferenceVO reference = addReference(localReferenceMap, normalizedThumbnail, articleReference);
                reference.setThumbnail(true);
            } else if (FileEntryUtils.isExternalUrl(thumbnail)) {
                FileReferenceVO reference = addReference(externalReferenceMap, thumbnail.trim(), articleReference);
                reference.setThumbnail(true);
            }
            String content = (String) article.get("content");
            if (StringUtils.isNotEmpty(content)) {
                for (String path : references.extractLocalResourcePaths(content)) {
                    FileReferenceVO reference = addReference(localReferenceMap, path, articleReference);
                    reference.setContent(true);
                }
                for (String url : FileEntryUtils.extractExternalResourceUrls(content)) {
                    FileReferenceVO reference = addReference(externalReferenceMap, url, articleReference);
                    reference.setContent(true);
                }
            }
            String markdown = (String) article.get("markdown");
            if (StringUtils.isNotEmpty(markdown)) {
                for (String path : references.extractLocalMarkdownResourcePaths(markdown)) {
                    FileReferenceVO reference = addReference(localReferenceMap, path, articleReference);
                    reference.setContent(true);
                }
                for (String url : references.extractExternalMarkdownResourceUrls(markdown)) {
                    FileReferenceVO reference = addReference(externalReferenceMap, url, articleReference);
                    reference.setContent(true);
                }
            }
        }
        FileReferenceIndexCacheVO cache = new FileReferenceIndexCacheVO();
        cache.setSignature(signature);
        cache.setLocalReferences(toReferenceListMap(localReferenceMap));
        cache.setExternalReferences(toReferenceListMap(externalReferenceMap));
        return cache;
    }

    private FileReferenceVO addReference(Map<String, Map<Integer, FileReferenceVO>> referenceMap, String resourcePath,
                                         FileReferenceVO articleReference) {
        return referenceMap
                .computeIfAbsent(resourcePath, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(articleReference.getLogId(), ignored ->
                        new FileReferenceVO(articleReference.getLogId(), articleReference.getTitle(), articleReference.getAlias()));
    }

    private void mergeReferenceList(Map<Integer, FileReferenceVO> target, List<FileReferenceVO> references) {
        for (FileReferenceVO reference : references) {
            FileReferenceVO merged = target.computeIfAbsent(reference.getLogId(), ignored ->
                    new FileReferenceVO(reference.getLogId(), reference.getTitle(), reference.getAlias()));
            merged.setThumbnail(merged.isThumbnail() || reference.isThumbnail());
            merged.setContent(merged.isContent() || reference.isContent());
        }
    }

    private Map<String, List<FileReferenceVO>> toReferenceListMap(Map<String, Map<Integer, FileReferenceVO>> referenceMap) {
        Map<String, List<FileReferenceVO>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, FileReferenceVO>> entry : referenceMap.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return result;
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(Objects.toString(value, "0"));
    }

    String normalizeLocalResourcePath(String value) {
        return references.normalizeLocalResourcePath(value);
    }

    private String getDomain(String url) {
        try {
            if (url.startsWith("//")) {
                url = "http:" + url;
            }
            java.net.URI uri = new java.net.URI(url);
            String domain = uri.getHost();
            return domain == null ? "unknown" : domain;
        } catch (Exception e) {
            return "unknown";
        }
    }

}
