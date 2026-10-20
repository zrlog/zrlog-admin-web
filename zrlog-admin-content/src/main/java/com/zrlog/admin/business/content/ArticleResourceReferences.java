package com.zrlog.admin.business.content;

import com.hibegin.common.util.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Article resource syntax and replacement rules; no database or filesystem access.
 */
public final class ArticleResourceReferences {
    private static final Pattern MARKDOWN_LINK_PATTERN =
            Pattern.compile("!?\\[[^\\]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern RESOURCE_URL_PATTERN =
            Pattern.compile(
                    "(?i)(https?://[^\\s\"'<>)]*|//[^\\s\"'<>)]*|/(?:[^\\s\"'<>)]*/)?attached/[^\\s\"'<>)]*)");
    private static final List<String> ARTICLE_RESOURCE_FIELDS =
            List.of("thumbnail", "content", "markdown", "digest");
    private static final Set<String> RESOURCE_FILE_EXTENSIONS =
            new HashSet<>(
                    Arrays.asList(
                            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "avif", "mp3",
                            "wav", "ogg", "m4a", "flac", "mp4", "mov", "webm", "avi", "mkv", "zip",
                            "rar", "7z", "tar", "gz", "bz2", "xz", "jar", "pdf", "doc", "docx",
                            "xls", "xlsx", "ppt", "pptx", "txt", "md", "json", "xml", "csv", "apk",
                            "dmg", "exe", "msi", "deb", "rpm"));
    private final String contextPath;

    public ArticleResourceReferences(String contextPath) {
        this.contextPath = normalizeContextPath(contextPath);
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

    public static Set<String> extractExternalResourceUrls(String html) {
        Set<String> urls = new LinkedHashSet<>();
        if (html == null || html.isEmpty()) {
            return urls;
        }
        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        doc.select("img[src],video[src],audio[src],source[src],track[src],embed[src]")
                .forEach(element -> addExternalUrl(urls, element.attr("src")));
        doc.select("video[poster]")
                .forEach(element -> addExternalUrl(urls, element.attr("poster")));
        doc.select("object[data]").forEach(element -> addExternalUrl(urls, element.attr("data")));
        doc.select("a[href]").forEach(element -> addExternalResourceLink(urls, element));
        return urls;
    }

    public static boolean isExternalUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        String trimmed = url.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("http://")
                && !normalized.startsWith("https://")
                && !normalized.startsWith("//")) {
            return false;
        }
        try {
            String parseValue = normalized.startsWith("//") ? "https:" + trimmed : trimmed;
            java.net.URI uri = new java.net.URI(parseValue);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isLikelyResourceUrl(String url) {
        return isExternalUrl(url) && RESOURCE_FILE_EXTENSIONS.contains(getExtension(url));
    }

    private static void addExternalUrl(Set<String> urls, String url) {
        if (isExternalUrl(url)) {
            urls.add(url.trim());
        }
    }

    private static void addExternalResourceLink(Set<String> urls, Element element) {
        String href = element.attr("href");
        if (isExternalUrl(href) && (element.hasAttr("download") || isLikelyResourceUrl(href))) {
            urls.add(href.trim());
        }
    }

    private static String getExtension(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "";
        }
        String normalized = stripQueryAndHash(fileName.trim()).toLowerCase(Locale.ROOT);
        int slashIndex = normalized.lastIndexOf('/');
        String name = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return "";
        }
        return name.substring(dotIndex + 1);
    }

    private static String stripQueryAndHash(String value) {
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

    public Map<String, Object> replace(
            Map<String, Object> article, String fromUrl, String toUrl, boolean prefix) {
        Map<String, Object> updates = new LinkedHashMap<>();
        ResourceReplacement replacement = buildResourceReplacement(fromUrl, toUrl, prefix);
        if (replacement == null) {
            return updates;
        }
        for (String field : ARTICLE_RESOURCE_FIELDS) {
            String value = (String) article.get(field);
            Set<String> replaceableMatchKeys =
                    getReplaceableExternalResourceMatchKeys(field, value);
            if (replaceableMatchKeys.isEmpty()) {
                continue;
            }
            String replaced =
                    replaceExternalResourceReferences(value, replacement, replaceableMatchKeys);
            if (Objects.equals(value, replaced)) {
                continue;
            }
            updates.put(field, replaced);
        }
        return updates;
    }

    private ResourceReplacement buildResourceReplacement(
            String fromUrl, String toUrl, boolean prefix) {
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
            if (isExternalUrl(value)) {
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
            urls.addAll(extractExternalResourceUrls(value));
            urls.addAll(extractLocalResourcePaths(value));
        }
        return urls.stream()
                .map(this::toResourceMatchKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String replaceExternalResourceReferences(
            String value, ResourceReplacement replacement, Set<String> replaceableMatchKeys) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }
        Matcher matcher = RESOURCE_URL_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String url = matcher.group(1);
            String urlMatchKey = toResourceMatchKey(url);
            if (!matchesResourceKey(replacement, urlMatchKey)
                    || !replaceableMatchKeys.contains(urlMatchKey)) {
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
            return Objects.equals(replacement.fromKey, urlMatchKey)
                    || urlMatchKey.startsWith(replacement.fromKey + "/");
        }
        return Objects.equals(replacement.fromKey, urlMatchKey);
    }

    private String replaceResourceUrl(
            String url, String urlMatchKey, ResourceReplacement replacement) {
        if (!replacement.prefix) {
            return rebuildExternalUrl(url, replacement.toKey);
        }
        String suffix =
                urlMatchKey.length() > replacement.fromKey.length()
                        ? urlMatchKey.substring(replacement.fromKey.length())
                        : "";
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
        if (!isExternalUrl(trimmed)) {
            return normalizeResourceKey(trimmed);
        }
        try {
            String parseValue = trimmed.startsWith("//") ? "https:" + trimmed : trimmed;
            java.net.URI uri = new java.net.URI(parseValue);
            if (StringUtils.isEmpty(uri.getHost())) {
                return null;
            }
            return normalizeResourceKey(
                    uri.getHost() + Objects.requireNonNullElse(uri.getPath(), ""));
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeResourceKey(String value) {
        String normalized =
                stripContextPath(normalizePath(value.trim()).replace("\\", "/"))
                        .toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public Set<String> extractLocalResourcePaths(String html) {
        Set<String> paths = new LinkedHashSet<>();
        if (StringUtils.isEmpty(html)) {
            return paths;
        }
        Document doc = Jsoup.parse(html);
        doc.select("img[src],video[src],audio[src],source[src],track[src],embed[src]")
                .forEach(element -> addLocalPath(paths, element.attr("src")));
        doc.select("video[poster]").forEach(element -> addLocalPath(paths, element.attr("poster")));
        doc.select("object[data]").forEach(element -> addLocalPath(paths, element.attr("data")));
        doc.select("a[href]").forEach(element -> addLocalResourceLink(paths, element));
        return paths;
    }

    public Set<String> extractLocalMarkdownResourcePaths(String markdown) {
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

    public Set<String> extractExternalMarkdownResourceUrls(String markdown) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (isExternalUrl(value) && isLikelyResourceUrl(value)) {
                urls.add(value.trim());
            }
        }
        return urls;
    }

    private void addLocalResourceLink(Set<String> paths, Element element) {
        String href = element.attr("href");
        String normalized = normalizeLocalResourcePath(href);
        if (normalized != null
                && (element.hasAttr("download") || normalized.startsWith("/attached" + "/"))) {
            paths.add(normalized);
        }
    }

    private void addLocalPath(Set<String> paths, String value) {
        String normalized = normalizeLocalResourcePath(value);
        if (normalized != null) {
            paths.add(normalized);
        }
    }

    public String normalizeLocalResourcePath(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        String normalized = normalizePath(value.trim());
        if (isExternalUrl(normalized)) {
            return null;
        }
        normalized = stripContextPath(normalized.replace("\\", "/"));
        return normalized.startsWith("/attached" + "/") ? normalized : null;
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
}
