package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.FileUtils;
import com.hibegin.common.util.IOUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.service.DbFileService;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.util.UploadFileUtils;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;

public class UploadController extends Controller {

    //private static final Logger LOGGER = LoggerUtil.getLogger(UploadController.class);

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public ApiStandardResponse<UploadFileResponse> index() throws IOException, SQLException {
        String uploadFieldName = "imgFile";
        File imgFile = request.getFile(uploadFieldName);
        if (imgFile == null) {
            imgFile = request.getFile("file");
            if (imgFile != null) {
                uploadFieldName = "file";
            }
        }
        if (imgFile == null || !imgFile.exists()) {
            throw new ArgsException("imgFile");
        }
        String dir = request.getParaToStr("dir", "");
        String uri = UploadFileUtils.generatorUri(uploadFieldName, request);
        String tempUri = buildTemporaryUri(dir, imgFile);
        if (tempUri != null) {
            return new ApiStandardResponse<>(
                    new DbFileService().toDbFile(tempUri, IOUtil.getByteByInputStream(new FileInputStream(imgFile))));
        }
        String finalFilePath = PathUtil.getStaticFile(uri).toString();
        FileUtils.moveOrCopyFile(imgFile.toString(), finalFilePath, true);
        return new ApiStandardResponse<>(new UploadService().getCloudUrl(uri, finalFilePath, getRequest(), AdminTokenThreadLocal.getUser()));
    }


    @ResponseBody
    public ApiStandardResponse<UploadFileResponse> thumbnail() throws IOException {
        String uploadFieldName = "imgFile";
        File tempImgFile = request.getFile(uploadFieldName);
        if (tempImgFile == null) {
            tempImgFile = request.getFile("file");
            if (tempImgFile != null) {
                uploadFieldName = "file";
            }
        }
        if (tempImgFile == null || !tempImgFile.exists()) {
            throw new ArgsException("imgFile");
        }
        String uri = UploadFileUtils.generatorUri(uploadFieldName, request);
        try {
            String finalFilePath = PathUtil.getStaticFile(uri).toString();
            byte[] bytes = IOUtil.getByteByInputStream(new FileInputStream(tempImgFile));
            File thumbnailFile = new File(finalFilePath);
            if (!thumbnailFile.getParentFile().exists()) {
                thumbnailFile.getParentFile().mkdirs();
            }
            int height = -1;
            int width = -1;
            //copy file
            IOUtil.writeBytesToFile(bytes, thumbnailFile);
            UploadFileResponse uploadFileResponse = new UploadService().getCloudUrl(uri, finalFilePath, getRequest(), AdminTokenThreadLocal.getUser());
            return new ApiStandardResponse<>(new UploadFileResponse(uploadFileResponse.getUrl() + "?h=" + height + "&w=" + width));
        } finally {
            tempImgFile.delete();
        }
    }

    String buildTemporaryUri(String dir, File file) {
        String normalizedDir = normalizeTemporaryDir(dir);
        if (normalizedDir == null) {
            return null;
        }
        String uri = UploadFileUtils.generatorUri(normalizedDir, file);
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
}
