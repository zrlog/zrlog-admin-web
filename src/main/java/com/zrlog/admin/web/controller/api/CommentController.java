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
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.controller.BaseController;
import com.zrlog.data.dto.CommentDTO;

import java.sql.SQLException;

public class CommentController extends BaseController {

    private final AdminCommentService commentService = new AdminCommentService();

    @RefreshCache(updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    public DeleteResponse delete() throws SQLException {
        return commentService.delete(getParamWithEmptyCheck("id").split(","));
    }

    @ResponseBody
    public UpdateRecordResponse read() {
        return commentService.read(getRequestBodyWithNullCheck(ReadCommentRequest.class));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse readAll() throws SQLException {
        commentService.readAll();
        return new UpdateRecordResponse(true);
    }

    @ResponseBody
    public AdminPageDataResponse<PageData<CommentDTO>> index() throws SQLException {
        return new AdminPageDataResponse<>(commentService.page(ControllerUtil.getPageRequest(this)), "", request.getUri());
    }
}
