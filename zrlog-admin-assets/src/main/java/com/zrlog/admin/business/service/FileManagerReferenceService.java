package com.zrlog.admin.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.rest.request.ReplaceArticleResourceUrlRequest;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.rest.response.FileReferenceIndexCacheVO;
import com.zrlog.admin.business.rest.response.FileReferenceVO;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.admin.business.util.FileEntryUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.model.Log;
import com.zrlog.util.ParseUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileManagerReferenceService {

    private static final Logger LOGGER = LoggerUtil.getLogger(FileManagerReferenceService.class);
    private static final Object REFERENCE_INDEX_CACHE_LOCK = new Object();
    private static final String REFERENCE_INDEX_CACHE_KEY = "file_manager_reference_index";
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("!?\\[[^\\]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern RESOURCE_URL_PATTERN = Pattern.compile("(?i)(https?://[^\\s\"'<>)]*|//[^\\s\"'<>)]*|/(?:[^\\s\"'<>)]*/)?attached/[^\\s\"'<>)]*)");
    private static final List<String> ARTICLE_RESOURCE_FIELDS = List.of("thumbnail", "content", "markdown", "digest");

    private final WebsiteCacheService cacheService;
    private final String contextPath;
    private FileReferenceIndexCacheVO requestReferenceIndex;

    public FileManagerReferenceService() {
        this(new WebsiteCacheService(), getConfiguredContextPath());
    }

    FileManagerReferenceService(WebsiteCacheService cacheService) {
        this(cacheService, getConfiguredContextPath());
    }

    FileManagerReferenceService(WebsiteCacheService cacheService, String contextPath) {
        this.cacheService = cacheService;
        this.contextPath = normalizeContextPath(contextPath);
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

    public ReplaceArticleResourceUrlResponse replaceArticleResourceUrl(AdminTokenVO user,
                                                                       ReplaceArticleResourceUrlRequest request)
            throws SQLException {
        return replaceArticleResourceReferences(user, request.getFromUrl(), request.getToUrl(),
                !FileEntryUtils.isExternalUrl(request.getFromUrl()));
    }

    public ReplaceArticleResourceUrlResponse replaceArticleResourceReferences(AdminTokenVO user, String fromUrl,
                                                                              String toUrl, boolean prefix)
            throws SQLException {
        List<Map<String, Object>> articles = new Log().queryList("logId, version, thumbnail, content, markdown, digest");
        ReplaceArticleResourceUrlResponse response = new ReplaceArticleResourceUrlResponse();
        response.setScannedArticles(articles.size());
        ArticleVersionService articleVersionService = new ArticleVersionService();
        for (Map<String, Object> article : articles) {
            Map<String, Object> updates = buildArticleResourceUrlUpdates(article, fromUrl, toUrl, prefix);
            if (updates.isEmpty()) {
                continue;
            }
            Map<String, Object> oldLog = new Log().loadById(toInt(article.get("logId")));
            Integer nextVersion = toInt(oldLog.get("version")) + 1;
            updates.put("version", nextVersion);
            updates.put("last_update_date", new Date());
            if (updates.containsKey("content")) {
                updates.put("plain_content", ParseUtil.getPlainSearchText(Objects.toString(updates.get("content"), "")));
            }
            Log logDao = new Log();
            updates.forEach(logDao::set);
            logDao.updateById(toInt(article.get("logId")));
            Map<String, Object> newLog = new HashMap<>(oldLog);
            newLog.putAll(updates);
            articleVersionService.recordReversePatch(oldLog, newLog, user == null ? null : user.getUserId());
            response.setUpdatedArticles(response.getUpdatedArticles() + 1);
            response.setUpdatedFields(response.getUpdatedFields() + countResourceFields(updates));
        }
        if (response.getUpdatedArticles() > 0) {
            requestReferenceIndex = null;
            clearReferenceIndexCache();
        }
        return response;
    }

    public String normalizePath(String value) {
        int queryIndex = value.indexOf('?');
        int hashIndex = value.indexOf('#');
        int endIndex = value.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (hashIndex >= 0) {
            endIndex = Math.min(endIndex, hashIndex);
        }
        return value.substring(0, endIndex);
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

    Map<String, Object> buildArticleResourceUrlUpdates(Map<String, Object> article, String fromUrl,
                                                       String toUrl, boolean prefix) {
        Map<String, Object> updates = new LinkedHashMap<>();
        ResourceReplacement replacement = buildResourceReplacement(fromUrl, toUrl, prefix);
        if (replacement == null) {
            return updates;
        }
        for (String field : ARTICLE_RESOURCE_FIELDS) {
            String value = (String) article.get(field);
            Set<String> replaceableMatchKeys = getReplaceableExternalResourceMatchKeys(field, value);
            if (replaceableMatchKeys.isEmpty()) {
                continue;
            }
            String replaced = replaceExternalResourceReferences(value, replacement, replaceableMatchKeys);
            if (Objects.equals(value, replaced)) {
                continue;
            }
            updates.put(field, replaced);
        }
        return updates;
    }

    private ResourceReplacement buildResourceReplacement(String fromUrl, String toUrl, boolean prefix) {
        String fromMatchKey = toResourceMatchKey(fromUrl);
        String toMatchKey = toResourceMatchKey(toUrl);
        if (StringUtils.isEmpty(fromMatchKey) || StringUtils.isEmpty(toMatchKey)) {
            return null;
        }
        return new ResourceReplacement(fromMatchKey, toMatchKey, prefix);
    }

    private Set<String> getReplaceableExternalResourceMatchKeys(String field, String value) {
        Set<String> urls = new LinkedHashSet<>();
        if (StringUtils.isEmpty(value)) {
            return urls;
        }
        if (Objects.equals(field, "thumbnail")) {
            if (FileEntryUtils.isExternalUrl(value)) {
                urls.add(value);
            } else {
                String normalized = normalizeLocalResourcePath(value);
                if (normalized != null) {
                    urls.add(normalized);
                }
            }
        } else if (Objects.equals(field, "markdown")) {
            urls.addAll(extractExternalMarkdownResourceUrls(value));
            urls.addAll(extractLocalMarkdownResourcePaths(value));
        } else {
            urls.addAll(FileEntryUtils.extractExternalResourceUrls(value));
            urls.addAll(extractLocalResourcePaths(value));
        }
        return urls.stream()
                .map(this::toResourceMatchKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String replaceExternalResourceReferences(String value, ResourceReplacement replacement,
                                                     Set<String> replaceableMatchKeys) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }
        Matcher matcher = RESOURCE_URL_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String url = matcher.group(1);
            String urlMatchKey = toResourceMatchKey(url);
            if (!matchesResourceKey(replacement, urlMatchKey) || !replaceableMatchKeys.contains(urlMatchKey)) {
                continue;
            }
            String nextUrl = replaceResourceUrl(url, urlMatchKey, replacement);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(nextUrl));
            changed = true;
        }
        if (!changed) {
            return value;
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean matchesResourceKey(ResourceReplacement replacement, String urlMatchKey) {
        if (replacement == null || StringUtils.isEmpty(urlMatchKey)) {
            return false;
        }
        if (replacement.prefix) {
            return Objects.equals(replacement.fromKey, urlMatchKey) || urlMatchKey.startsWith(replacement.fromKey + "/");
        }
        return Objects.equals(replacement.fromKey, urlMatchKey);
    }

    private String replaceResourceUrl(String url, String urlMatchKey, ResourceReplacement replacement) {
        if (!replacement.prefix) {
            return rebuildExternalUrl(url, replacement.toKey);
        }
        String suffix = urlMatchKey.length() > replacement.fromKey.length()
                ? urlMatchKey.substring(replacement.fromKey.length()) : "";
        return rebuildExternalUrl(url, replacement.toKey + suffix);
    }

    private String rebuildExternalUrl(String url, String targetKey) {
        try {
            String parseValue = url.startsWith("//") ? "https:" + url : url;
            java.net.URI uri = new java.net.URI(parseValue);
            String sourceKey = toResourceMatchKey(url);
            if (StringUtils.isEmpty(sourceKey)) {
                return url;
            }
            int keyIndex = url.toLowerCase(Locale.ROOT).indexOf(sourceKey.toLowerCase(Locale.ROOT));
            if (keyIndex < 0) {
                return url;
            }
            String tail = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            tail += uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment();
            return url.substring(0, keyIndex) + targetKey + tail;
        } catch (Exception e) {
            return url;
        }
    }

    private String toResourceMatchKey(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!FileEntryUtils.isExternalUrl(trimmed)) {
            return normalizeResourceKey(trimmed);
        }
        try {
            String parseValue = trimmed.startsWith("//") ? "https:" + trimmed : trimmed;
            java.net.URI uri = new java.net.URI(parseValue);
            if (StringUtils.isEmpty(uri.getHost())) {
                return null;
            }
            return normalizeResourceKey(uri.getHost() + Objects.requireNonNullElse(uri.getPath(), ""));
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeResourceKey(String value) {
        String normalized = stripContextPath(normalizePath(value.trim()).replace("\\", "/")).toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private int countResourceFields(Map<String, Object> updates) {
        int count = 0;
        for (String field : ARTICLE_RESOURCE_FIELDS) {
            if (updates.containsKey(field)) {
                count++;
            }
        }
        return count;
    }

    private String buildArticleReferenceSignature() throws SQLException {
        List<Map<String, Object>> rows = new Log().queryList("count(*) AS article_count, max(logId) AS max_log_id, max(version) AS max_version, max(last_update_date) AS max_last_update_date");
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
        List<Map<String, Object>> articles = new Log().queryList("logId, title, alias, thumbnail, content, markdown");
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
                for (String path : extractLocalResourcePaths(content)) {
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
                for (String path : extractLocalMarkdownResourcePaths(markdown)) {
                    FileReferenceVO reference = addReference(localReferenceMap, path, articleReference);
                    reference.setContent(true);
                }
                for (String url : extractExternalMarkdownResourceUrls(markdown)) {
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

    private Set<String> extractLocalResourcePaths(String html) {
        Set<String> paths = new LinkedHashSet<>();
        if (StringUtils.isEmpty(html)) {
            return paths;
        }
        Document doc = Jsoup.parse(html);
        doc.select("img[src],video[src],audio[src],source[src],track[src],embed[src]").forEach(element ->
                addLocalPath(paths, element.attr("src"))
        );
        doc.select("video[poster]").forEach(element -> addLocalPath(paths, element.attr("poster")));
        doc.select("object[data]").forEach(element -> addLocalPath(paths, element.attr("data")));
        doc.select("a[href]").forEach(element -> addLocalResourceLink(paths, element));
        return paths;
    }

    private Set<String> extractLocalMarkdownResourcePaths(String markdown) {
        Set<String> paths = new LinkedHashSet<>();
        Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String normalized = normalizeLocalResourcePath(matcher.group(1));
            if (normalized != null) {
                paths.add(normalized);
            }
        }
        return paths;
    }

    private Set<String> extractExternalMarkdownResourceUrls(String markdown) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (FileEntryUtils.isExternalUrl(value) && FileEntryUtils.isLikelyResourceUrl(value)) {
                urls.add(value.trim());
            }
        }
        return urls;
    }

    private void addLocalResourceLink(Set<String> paths, Element element) {
        String href = element.attr("href");
        String normalized = normalizeLocalResourcePath(href);
        if (normalized != null && (element.hasAttr("download") || normalized.startsWith(FileManagerService.ATTACHED_ROOT + "/"))) {
            paths.add(normalized);
        }
    }

    private void addLocalPath(Set<String> paths, String value) {
        String normalized = normalizeLocalResourcePath(value);
        if (normalized != null) {
            paths.add(normalized);
        }
    }

    String normalizeLocalResourcePath(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        String normalized = normalizePath(value.trim());
        if (FileEntryUtils.isExternalUrl(normalized)) {
            return null;
        }
        normalized = stripContextPath(normalized.replace("\\", "/"));
        return normalized.startsWith(FileManagerService.ATTACHED_ROOT + "/") ? normalized : null;
    }

    private String stripContextPath(String path) {
        if (StringUtils.isEmpty(path) || StringUtils.isEmpty(contextPath)) {
            return path;
        }
        if (path.equals(contextPath)) {
            return "";
        }
        return path.startsWith(contextPath + "/") ? path.substring(contextPath.length()) : path;
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

    private static final class ResourceReplacement {
        private final String fromKey;
        private final String toKey;
        private final boolean prefix;

        private ResourceReplacement(String fromKey, String toKey, boolean prefix) {
            this.fromKey = fromKey;
            this.toKey = toKey;
            this.prefix = prefix;
        }
    }

    private static String getConfiguredContextPath() {
        if (Constants.zrLogConfig == null || Constants.zrLogConfig.getServerConfig() == null) {
            return "";
        }
        return normalizeContextPath(Constants.zrLogConfig.getServerConfig().getContextPath());
    }

    private static String normalizeContextPath(String contextPath) {
        if (StringUtils.isEmpty(contextPath) || Objects.equals(contextPath, "/")) {
            return "";
        }
        String normalized = contextPath.trim().replace("\\", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
