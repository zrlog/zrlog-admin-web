package com.zrlog.admin.util;

import com.hibegin.common.util.SecurityUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class UploadFileUtils {

    public static final String DEFAULT_UPLOAD_DIR = "thumbnail";

    public static String resolveUploadDir(String dir) {
        return dir == null || dir.trim().isEmpty() ? DEFAULT_UPLOAD_DIR : dir;
    }

    public static String generatorUri(String uploadFieldName, HttpRequest request) {
        File file = request.getFile(uploadFieldName);
        if (Objects.isNull(file)) {
            uploadFieldName = "file";
            file = request.getFile(uploadFieldName);
        }
        if (Objects.isNull(file)) {
            return "";
        }
        return generatorUri(request.getParaToStr("dir"), file, request.getParaToStr("name"));
    }

    public static String generatorUri(String dir, File file) {
        return generatorUri(dir, file, null);
    }

    public static String generatorUri(String dir, File file, String name) {
        String requestedName = normalizeUploadFileName(name);
        if (requestedName != null) {
            return buildUri(dir, requestedName);
        }
        String fileName = file.getName();
        String fileExt = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        String md5 = SecurityUtils.md5ByFile(file);
        return buildUri(dir, md5 + (fileExt.isEmpty() ? "" : "." + fileExt));
    }

    public static String generatorUri(String dir, String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
        String md5 = SecurityUtils.md5(fileName + System.currentTimeMillis());
        return buildUri(dir, md5 + "." + extension);
    }

    private static String buildUri(String dir, String fileNameWithExt) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        dir = normalizeAttachedDir(dir);
        boolean hasDir = dir.contains("/");
        String path = (hasDir ? "" : "/" + sdf.format(new Date())) + "/" + fileNameWithExt;
        return (AdminConstants.ATTACHED_FOLDER + dir + path).replaceAll("/{2,}", "/");
    }

    private static String normalizeAttachedDir(String dir) {
        dir = resolveUploadDir(dir);
        if (dir.contains("..")) {
            return "";
        }
        String normalized = dir.replace("\\", "/").replaceAll("/{2,}", "/");
        if (normalized.equals(AdminConstants.ATTACHED_FOLDER.substring(0, AdminConstants.ATTACHED_FOLDER.length() - 1))) {
            return "/";
        }
        if (normalized.startsWith(AdminConstants.ATTACHED_FOLDER)) {
            return normalized.substring(AdminConstants.ATTACHED_FOLDER.length() - 1);
        }
        return normalized;
    }

    private static String normalizeUploadFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalized = name.trim().replace("\\", "/");
        normalized = normalized.substring(normalized.lastIndexOf("/") + 1);
        if (normalized.isEmpty() || normalized.equals(".") || normalized.equals("..")) {
            return null;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (Character.isISOControl(character) || character == '?' || character == '#') {
                return null;
            }
        }
        return normalized;
    }

}
