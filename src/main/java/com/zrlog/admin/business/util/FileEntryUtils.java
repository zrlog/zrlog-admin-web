package com.zrlog.admin.business.util;

import com.hibegin.http.server.util.MimeTypeUtil;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.type.FileEntryAccess;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.*;

public class FileEntryUtils {

    private static final Set<String> RESOURCE_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "avif",
            "mp3", "wav", "ogg", "m4a", "flac",
            "mp4", "mov", "webm", "avi", "mkv",
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "json", "xml", "csv",
            "apk", "dmg", "exe", "msi", "deb", "rpm"
    ));
    private static final Set<String> ARCHIVE_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar"
    ));
    private static final Set<String> ARCHIVE_MIME_TYPES = new HashSet<>(Arrays.asList(
            "application/zip", "application/vnd.rar", "application/x-7z-compressed",
            "application/x-tar", "application/gzip", "application/x-bzip2",
            "application/x-xz", "application/java-archive"
    ));
    private static final Map<String, String> MIME_TYPE_OVERRIDES = Map.ofEntries(
            Map.entry("avif", "image/avif"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("flac", "audio/flac"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("gz", "application/gzip"),
            Map.entry("xz", "application/x-xz"),
            Map.entry("md", "text/markdown"),
            Map.entry("log", "text/plain"),
            Map.entry("tsx", "application/typescript-jsx"),
            Map.entry("apk", "application/vnd.android.package-archive"),
            Map.entry("dmg", "application/x-apple-diskimage"),
            Map.entry("exe", "application/vnd.microsoft.portable-executable"),
            Map.entry("msi", "application/x-msdownload"),
            Map.entry("deb", "application/vnd.debian.binary-package"),
            Map.entry("rpm", "application/x-rpm")
    );

    public static FileEntryVO decorateEntry(FileEntryVO entry) {
        if (entry.isMissing()) {
            entry.setImage(false);
            entry.setTextPreviewable(false);
            entry.setIconType("file");
            return entry;
        }
        if ("directory".equals(entry.getType())) {
            entry.setImage(false);
            entry.setTextPreviewable(false);
            entry.setIconType(entry.isVirtual() ? "library" :
                    (entry.getAccess() == FileEntryAccess.ADMIN_ONLY ? "directory_locked" : "directory"));
            return entry;
        }
        String mimeType = entry.getMimeType() == null ? "" : entry.getMimeType().toLowerCase(Locale.ROOT);
        String fileName = entry.getName() == null ? "" : entry.getName().toLowerCase(Locale.ROOT);
        boolean image = mimeType.startsWith("image/");
        boolean textPreviewable = mimeType.startsWith("text/")
                || "application/json".equals(mimeType)
                || "application/xml".equals(mimeType)
                || "application/javascript".equals(mimeType)
                || "application/typescript".equals(mimeType)
                || "application/typescript-jsx".equals(mimeType);
        entry.setImage(image);
        entry.setTextPreviewable(textPreviewable);
        if (image) {
            entry.setIconType("image");
        } else if (textPreviewable) {
            entry.setIconType("code");
        } else if (ARCHIVE_FILE_EXTENSIONS.contains(getExtension(fileName)) || ARCHIVE_MIME_TYPES.contains(mimeType)) {
            entry.setIconType("archive");
        } else {
            entry.setIconType("file");
        }
        return entry;
    }

    public static String toMimeType(String fileName) {
        String extension = getExtension(fileName);
        String override = MIME_TYPE_OVERRIDES.get(extension);
        if (override != null) {
            return override;
        }
        return MimeTypeUtil.getMimeStrByExt(extension);
    }

    public static Set<String> extractExternalResourceUrls(String html) {
        Set<String> urls = new LinkedHashSet<>();
        if (html == null || html.isEmpty()) {
            return urls;
        }
        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        doc.select("img[src],video[src],audio[src],source[src],track[src],embed[src]").forEach(element ->
                addExternalUrl(urls, element.attr("src"))
        );
        doc.select("video[poster]").forEach(element -> addExternalUrl(urls, element.attr("poster")));
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
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://") && !normalized.startsWith("//")) {
            return false;
        }
        try {
            String parseValue = normalized.startsWith("//") ? "https:" + trimmed : trimmed;
            java.net.URI uri = new java.net.URI(parseValue);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().trim().isEmpty();
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
}
