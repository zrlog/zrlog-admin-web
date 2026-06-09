package com.zrlog.admin.business.service;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UploadService {

    private static final Logger LOGGER = LoggerUtil.getLogger(UploadService.class);

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
        UploadFileResponse uploadFileResponse = saveBytesToUri(bytes, uri, request, adminTokenVO);
        return new UploadFileResponse(uploadFileResponse.getUrl() + "?h=-1&w=-1");
    }

    public UploadFileResponse saveBytes(byte[] bytes, String extension, String dir, HttpRequest request,
                                        AdminTokenVO adminTokenVO) {
        String uri = UploadFileUtils.generatorUri(dir, "upload." + normalizeExtension(extension));
        return saveBytesToUri(bytes, uri, request, adminTokenVO);
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

    private String normalizeExtension(String extension) {
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
