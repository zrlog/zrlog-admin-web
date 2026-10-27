package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.ReplaceArticleResourceUrlRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.FileManagerService;
import com.zrlog.admin.business.util.FileEntryUtils;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;

public class FileManagerController extends BaseController {
    private final AdminPageDataFactory pages;
    private final AdminRequestIdentity requestIdentity;


    private final FileManagerService fileManagerService;

    @Inject
    public FileManagerController(
            FileManagerService fileManagerService, AdminPageDataFactory pages,
            AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.pages = pages;
        this.fileManagerService = fileManagerService;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.list")
    public AdminPageDataResponse<FileManagerResponse> index() throws SQLException {
        String path = request.getParaToStr("path", "");
        String key = request.getParaToStr("key", "");
        String resourceType = request.getParaToStr("resourceType", "");
        var actor = requestIdentity.current();
        return new AdminPageDataResponse<>(fileManagerService.page(path, key, resourceType), "",
                pages.metadata(actor, request.getUri()));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.search")
    public ApiStandardResponse<List<FileEntryVO>> search() throws SQLException {
        String key = request.getParaToStr("key", "");
        return new ApiStandardResponse<>(fileManagerService.search(key));
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.refreshReferences")
    public ApiStandardResponse<Boolean> refreshReferenceIndex() throws SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        return new ApiStandardResponse<>(fileManagerService.refreshReferenceIndex());
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.replaceUrl")
    public ApiStandardResponse<ReplaceArticleResourceUrlResponse> replaceArticleResourceUrl()
            throws IOException, SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        ReplaceArticleResourceUrlRequest body = getRequestBodyWithNullCheck(ReplaceArticleResourceUrlRequest.class);
        var actor = requestIdentity.current();
        ReplaceArticleResourceUrlResponse response = fileManagerService.replaceArticleResourceUrlAndRecord(
                actor, body, AdminAuditContexts.from(request, actor.getUserId()));
        return new ApiStandardResponse<>(response);
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.restore")
    public ApiStandardResponse<UploadFileResponse> reuploadMissingLocalResource() throws IOException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        String path = request.getParaToStr("path", "");
        if (path.isEmpty()) {
            throw new ArgsException("path");
        }
        String uploadFieldName = "imgFile";
        File uploadFile = request.getFile(uploadFieldName);
        if (uploadFile == null) {
            uploadFile = request.getFile("file");
        }
        if (uploadFile == null || !uploadFile.exists()) {
            throw new ArgsException("imgFile");
        }
        UploadFileResponse response = fileManagerService.reuploadMissingLocalResourceAndRecord(
                path, uploadFile, request, AdminTokenThreadLocal.getUser(), AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()));
        return new ApiStandardResponse<>(response);
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.delete")
    public ApiStandardResponse<Boolean> delete() throws SQLException {
        String path = request.getParaToStr("path", "");
        if (path.isEmpty()) {
            throw new ArgsException("path");
        }
        return new ApiStandardResponse<>(fileManagerService.deleteAndRecord(path, AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId())));
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.rename")
    public ApiStandardResponse<Boolean> rename() throws SQLException {
        String path = request.getParaToStr("path", "");
        String newName = request.getParaToStr("newName", "");
        if (newName.isEmpty()) {
            throw new ArgsException("newName");
        }
        boolean syncArticleReferences = request.getParaToBool("syncArticleReferences", false);
        var actor = requestIdentity.current();
        return new ApiStandardResponse<>(fileManagerService.renameAndRecord(
                path, newName, syncArticleReferences, actor, AdminAuditContexts.from(request, actor.getUserId())));
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.createDirectory")
    public ApiStandardResponse<Boolean> mkdir() {
        String path = request.getParaToStr("path", "");
        if (path.isEmpty()) {
            throw new ArgsException("path");
        }
        return new ApiStandardResponse<>(fileManagerService.mkdirAndRecord(path, AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId())));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.readContent")
    public ApiStandardResponse<String> readContent() throws IOException {
        String path = request.getParaToStr("path", "");
        return new ApiStandardResponse<>(fileManagerService.readContent(path));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.read")
    public ApiStandardResponse<String> read() throws IOException {
        return readContent();
    }

    @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "file.download")
    public void download() throws IOException {
        String path = request.getParaToStr("path", "");
        if (FileEntryUtils.isExternalUrl(path)) {
            String target = path.trim();
            response.redirect(URI.create(target.startsWith("//") ? "https:" + target : target).toASCIIString());
            return;
        }
        try (java.io.InputStream target = fileManagerService.open(path)) {
            response.addHeader("Content-Disposition", "attachment;filename=" + new File(path).getName());
            response.write(target);
        }
    }
}
