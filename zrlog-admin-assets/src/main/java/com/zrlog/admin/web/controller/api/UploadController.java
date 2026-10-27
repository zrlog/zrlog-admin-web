package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.UploadedFile;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;

public class UploadController extends Controller {

    //private static final Logger LOGGER = LoggerUtil.getLogger(UploadController.class);
    private final UploadService uploadService;
    private final AdminRequestIdentity requestIdentity;

    @Inject
    public UploadController(UploadService uploadService, AdminRequestIdentity requestIdentity) {
        this.uploadService = uploadService;
        this.requestIdentity = requestIdentity;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ASSET_UPLOAD, descriptionKey = "asset.upload")
    public ApiStandardResponse<UploadFileResponse> index() throws IOException, SQLException {
        return upload();
    }

    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.ASSET_UPLOAD, descriptionKey = "file.download")
    public void resource() throws IOException {
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Content-Type", "application/octet-stream");
        try (java.io.InputStream input = uploadService.openTemporaryResource(requestIdentity.current(), request.getParaToStr("path"))) {
            response.write(input);
        } catch (java.io.FileNotFoundException missing) {
            response.renderCode(404);
        }
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
        UploadedFile imgFile = request.getUploadedFile("imgFile");
        if (imgFile == null) {
            imgFile = request.getUploadedFile("file");
        }
        if (imgFile == null || !imgFile.getFile().exists()) {
            throw new ArgsException("imgFile");
        }
        return new ApiStandardResponse<>(uploadService.saveUploadedFile(
                requestIdentity.current(), imgFile, request.getParaToStr("dir"), request.getParaToStr("name"),
                Boolean.parseBoolean(request.getParaToStr("temporary", "false")),
                getRequest(), AdminTokenThreadLocal.getUser()));
    }
}
