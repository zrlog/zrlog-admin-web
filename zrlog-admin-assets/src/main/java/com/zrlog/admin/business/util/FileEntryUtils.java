package com.zrlog.admin.business.util;

import com.hibegin.http.server.util.MimeTypeUtil;
import com.zrlog.admin.business.content.ArticleResourceReferences;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.type.FileEntryAccess;

import java.util.*;

public class FileEntryUtils {

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
        return ArticleResourceReferences.extractExternalResourceUrls(html);
    }

    public static boolean isExternalUrl(String url) {
        return ArticleResourceReferences.isExternalUrl(url);
    }

    public static boolean isLikelyResourceUrl(String url) {
        return ArticleResourceReferences.isLikelyResourceUrl(url);
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
