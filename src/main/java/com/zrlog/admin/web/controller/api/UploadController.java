package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

public class UploadController extends Controller {

    //private static final Logger LOGGER = LoggerUtil.getLogger(UploadController.class);
    private final UploadService uploadService = new UploadService();

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ASSET_UPLOAD, descriptionKey = "asset.upload")
    public ApiStandardResponse<UploadFileResponse> index() throws IOException, SQLException {
        return upload();
    }

    /**
     * @deprecated use {@link #index()}.
     */
    @Deprecated
    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ASSET_UPLOAD, descriptionKey = "asset.thumbnail")
    public ApiStandardResponse<UploadFileResponse> thumbnail() throws IOException, SQLException {
        return upload();
    }

    private ApiStandardResponse<UploadFileResponse> upload() throws IOException, SQLException {
        File imgFile = request.getFile("imgFile");
        if (imgFile == null) {
            imgFile = request.getFile("file");
        }
        if (imgFile == null || !imgFile.exists()) {
            throw new ArgsException("imgFile");
        }
        return new ApiStandardResponse<>(uploadService.saveUploadedFile(
                imgFile, request.getParaToStr("dir"), request.getParaToStr("name"),
                getRequest(), AdminTokenThreadLocal.getUser()));
    }
}
