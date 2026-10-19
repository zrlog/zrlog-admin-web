package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.ReadCommentRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.service.AdminCommentService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.controller.BaseController;
import com.zrlog.data.dto.CommentDTO;
import com.zrlog.data.security.AccountAction;

import jakarta.inject.Inject;

import java.sql.SQLException;

public class CommentController extends BaseController {

    @Inject
    public CommentController(AdminPageDataFactory pages, AdminCommentService commentService) {
        this.commentService = commentService;

        this.pages = pages;
    }

    private final AdminPageDataFactory pages;

    private final AdminCommentService commentService;

    @RefreshCache(updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequiresAction(value = AccountAction.COMMENT_MANAGE, descriptionKey = "comment.delete")
    @RequestMethod(method = HttpMethod.POST)
    public DeleteResponse delete() throws SQLException {
        return commentService.delete(getParamWithEmptyCheck("id").split(","));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.COMMENT_MANAGE, descriptionKey = "comment.read")
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse read() {
        return commentService.read(getRequestBodyWithNullCheck(ReadCommentRequest.class));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.COMMENT_MANAGE, descriptionKey = "comment.readAll")
    public UpdateRecordResponse readAll() throws SQLException {
        commentService.readAll();
        return new UpdateRecordResponse(true);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.COMMENT_MANAGE, descriptionKey = "comment.list")
    public AdminPageDataResponse<PageData<CommentDTO>> index() throws SQLException {
        return pages.response(commentService.page(ControllerUtil.getPageRequest(this)), "", request.getUri());
    }
}
