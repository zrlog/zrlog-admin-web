package com.zrlog.admin.business.service;

import com.hibegin.common.util.FileUtils;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.plugin.rest.response.UploadServiceResponseEntity;
import com.zrlog.admin.util.UploadFileUtils;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.AdminTokenVO;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UploadService {

    private static final Logger LOGGER = LoggerUtil.getLogger(UploadService.class);
    private final DbFileService dbFileService = new DbFileService();

    public UploadFileResponse saveUploadedFile(File file, String dir, HttpRequest request,
                                               AdminTokenVO adminTokenVO) throws IOException, SQLException {
        return saveUploadedFile(file, dir, null, request, adminTokenVO);
    }

    public UploadFileResponse saveUploadedFile(File file, String dir, String name, HttpRequest request,
                                               AdminTokenVO adminTokenVO) throws IOException, SQLException {
        String resolvedDir = UploadFileUtils.resolveUploadDir(dir);
        com.zrlog.data.security.AccountAccess actor = AccountPermissionService.account(adminTokenVO);
        if (!actor.isAdministrator()) {
            resolvedDir = normalizeTemporaryDir(resolvedDir) == null
                    ? "users/" + actor.getUserId()
                    : AdminConstants.ADMIN_DB_ATTACHED_TMP + "/users/" + actor.getUserId();
        }
        String uri = UploadFileUtils.generatorUri(resolvedDir, file, name);
        String temporaryUri = buildTemporaryUri(resolvedDir, file, name);
        if (temporaryUri != null) {
            try (FileInputStream inputStream = new FileInputStream(file)) {
                return dbFileService.toDbFile(temporaryUri, IOUtil.getByteByInputStream(inputStream));
            }
        }
        String finalFilePath = PathUtil.getStaticFile(uri).toString();
        FileUtils.moveOrCopyFile(file.toString(), finalFilePath, true);
        return getCloudUrl(uri, finalFilePath, request, adminTokenVO);
    }

    public UploadFileResponse getCloudUrl(String uri, String finalFilePath, HttpRequest request, AdminTokenVO adminTokenVO) {
        String contextPath = request.getContextPath();
        // try push to cloud
        String url;
        try {
            Map<String, String[]> uploadParams = new HashMap<>();
            uploadParams.put("fileInfo", new String[]{finalFilePath + "," + uri});
            uploadParams.put("name", new String[]{"uploadService"});

            PluginCorePlugin pluginCorePlugin = Constants.zrLogConfig.getPlugin(PluginCorePlugin.class);
            UploadServiceResponseEntity[] urls = pluginCorePlugin.requestService(request,
                    uploadParams, adminTokenVO, UploadServiceResponseEntity[].class);
            if (urls != null && urls.length > 0) {
                url = urls[0].getUrl();
                if (!url.startsWith("https://") && !url.startsWith("http://")) {
                    String tUrl = url;
                    if (!url.startsWith("/")) {
                        tUrl = "/" + url;
                    }
                    url = contextPath + tUrl;
                }
            } else {
                url = contextPath + uri;
            }
        } catch (Exception e) {
            url = contextPath + uri;
            LOGGER.log(Level.SEVERE, "", e);
        }
        return new UploadFileResponse(url);
    }

    public UploadFileResponse saveThumbnailBytes(byte[] bytes, String extension, HttpRequest request,
                                                 AdminTokenVO adminTokenVO) {
        String uri = buildGeneratedThumbnailUri(bytes, extension);
        return saveBytesToUri(bytes, uri, request, adminTokenVO);
    }

    public UploadFileResponse saveBytes(byte[] bytes, String extension, String dir, HttpRequest request,
                                        AdminTokenVO adminTokenVO) {
        String uri = UploadFileUtils.generatorUri(dir, "upload." + normalizeExtension(extension));
        return saveBytesToUri(bytes, uri, request, adminTokenVO);
    }

    String buildTemporaryUri(String dir, File file) {
        return buildTemporaryUri(dir, file, null);
    }

    String buildTemporaryUri(String dir, File file, String name) {
        String normalizedDir = normalizeTemporaryDir(dir);
        if (normalizedDir == null) {
            return null;
        }
        String uri = UploadFileUtils.generatorUri(normalizedDir, file, name);
        String suffix = uri.substring(AdminConstants.ATTACHED_FOLDER.length() - 1).replaceAll("/{2,}", "/");
        return AdminConstants.ADMIN_DB_ATTACHED_TMP + (suffix.startsWith("/") ? suffix : "/" + suffix);
    }

    String normalizeTemporaryDir(String dir) {
        if (dir == null || dir.contains("..")) {
            return null;
        }
        String normalized = dir.replace("\\", "/").replaceAll("/{2,}", "/");
        if (normalized.equals(AdminConstants.ADMIN_DB_ATTACHED_TMP)
                || normalized.equals(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/")) {
            return "/";
        }
        if (normalized.startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/")) {
            return normalized.substring(AdminConstants.ADMIN_DB_ATTACHED_TMP.length());
        }
        return null;
    }

    private UploadFileResponse saveBytesToUri(byte[] bytes, String uri, HttpRequest request, AdminTokenVO adminTokenVO) {
        String finalFilePath = PathUtil.getStaticFile(uri).toString();
        File thumbnailFile = new File(finalFilePath);
        if (!thumbnailFile.getParentFile().exists()) {
            thumbnailFile.getParentFile().mkdirs();
        }
        IOUtil.writeBytesToFile(bytes, thumbnailFile);
        return getCloudUrl(uri, finalFilePath, request, adminTokenVO);
    }

    String normalizeExtension(String extension) {
        return extension == null || extension.trim().isEmpty() ? "png" : extension;
    }

    private String buildGeneratedThumbnailUri(byte[] bytes, String extension) {
        StringJoiner joiner = new StringJoiner(".");
        joiner.add(md5(bytes));
        joiner.add(normalizeExtension(extension));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        return AdminConstants.ATTACHED_FOLDER + "thumbnail/" + sdf.format(new Date()) + "/" + joiner;
    }

    private String md5(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
