package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.io.LengthByteArrayInputStream;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.ReplaceArticleResourceUrlRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.rest.response.FileManagerResponse;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.FileManagerService;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.business.util.FileEntryUtils;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.ZrLogUtil;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class FileManagerController extends BaseController {

    private final FileManagerService fileManagerService = new FileManagerService();

    @ResponseBody
    public AdminPageDataResponse<FileManagerResponse> index() throws SQLException {
        String path = request.getParaToStr("path", "");
        String key = request.getParaToStr("key", "");
        String resourceType = request.getParaToStr("resourceType", "");
        FileManagerResponse response = new FileManagerResponse();
        response.setShortcuts(fileManagerService.getShortcuts());
        if (Objects.equals(resourceType, "broken")) {
            response.setEntries(fileManagerService.listBrokenLocalResourceReferences(key));
            response.setDirectoryActions(List.of());
        } else {
            response.setEntries(StringUtils.isEmpty(key) ? fileManagerService.list(path) : fileManagerService.search(key));
            response.setDirectoryActions(StringUtils.isEmpty(key) ? fileManagerService.getDirectoryActions(path) : List.of());
        }
        return new AdminPageDataResponse<>(response, "", request.getUri());
    }

    @ResponseBody
    public ApiStandardResponse<List<FileEntryVO>> search() throws SQLException {
        String key = request.getParaToStr("key", "");
        return new ApiStandardResponse<>(fileManagerService.search(key));
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    public ApiStandardResponse<Boolean> refreshReferenceIndex() throws SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        return new ApiStandardResponse<>(fileManagerService.refreshReferenceIndex());
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    public ApiStandardResponse<ReplaceArticleResourceUrlResponse> replaceArticleResourceUrl()
            throws IOException, SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        ReplaceArticleResourceUrlRequest body = getRequestBodyWithNullCheck(ReplaceArticleResourceUrlRequest.class);
        ReplaceArticleResourceUrlResponse response = fileManagerService.replaceArticleResourceUrl(AdminTokenThreadLocal.getUser(), body);
        new AdminAuditService().record(request, AdminAuditAction.REPLACE_ARTICLE_RESOURCE_URL,
                body.getFromUrl() + " -> " + body.getToUrl());
        new MessageCenterOperationService().recordReplaceArticleResourceUrl(response);
        return new ApiStandardResponse<>(response);
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
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
        UploadFileResponse response = fileManagerService.reuploadMissingLocalResource(
                path, uploadFile, request, AdminTokenThreadLocal.getUser());
        new AdminAuditService().record(request, AdminAuditAction.REUPLOAD_MISSING_FILE, path);
        return new ApiStandardResponse<>(response);
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    public ApiStandardResponse<Boolean> delete() throws SQLException {
        String path = request.getParaToStr("path", "");
        if (path.isEmpty()) {
            throw new ArgsException("path");
        }
        boolean ok = fileManagerService.delete(path);
        if (ok) {
            new AdminAuditService().record(request, AdminAuditAction.DELETE_FILE, path);
        }
        return new ApiStandardResponse<>(ok);
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    public ApiStandardResponse<Boolean> rename() throws SQLException {
        String path = request.getParaToStr("path", "");
        String newName = request.getParaToStr("newName", "");
        if (newName.isEmpty()) {
            throw new ArgsException("newName");
        }
        boolean syncArticleReferences = request.getParaToBool("syncArticleReferences", false);
        boolean ok = fileManagerService.rename(path, newName, syncArticleReferences, AdminTokenThreadLocal.getUser());
        if (ok) {
            new AdminAuditService().record(request, AdminAuditAction.RENAME_FILE, path + " -> " + newName);
        }
        return new ApiStandardResponse<>(ok);
    }

    @RequestMethod(method = HttpMethod.POST)
    @ResponseBody
    public ApiStandardResponse<Boolean> mkdir() {
        String path = request.getParaToStr("path", "");
        if (path.isEmpty()) {
            throw new ArgsException("path");
        }
        boolean ok = fileManagerService.mkdir(path);
        if (ok) {
            new AdminAuditService().record(request, AdminAuditAction.CREATE_DIRECTORY, path);
        }
        return new ApiStandardResponse<>(ok);
    }

    @ResponseBody
    public ApiStandardResponse<String> readContent() throws IOException {
        String path = request.getParaToStr("path", "");
        return new ApiStandardResponse<>(fileManagerService.readContent(path));
    }

    @ResponseBody
    public ApiStandardResponse<String> read() throws IOException {
        return readContent();
    }

    public void download() throws IOException {
        String path = request.getParaToStr("path", "");
        if (FileEntryUtils.isExternalUrl(path)) {
            response.redirect(path.startsWith("//") ? "https:" + path : path);
            return;
        }
        byte[] target = fileManagerService.read(path);
        response.addHeader("Content-Disposition", "attachment;filename=" + new File(path).getName());
        response.write(new LengthByteArrayInputStream(target));
    }
}
