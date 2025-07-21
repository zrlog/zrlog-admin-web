package com.zrlog.admin.web.controller.api;

import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.response.AdminApiPageDataStandardResponse;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.business.service.MessageCenterService;
import com.zrlog.common.controller.BaseController;

import java.util.List;

public class MessageCenterController extends BaseController {

    private final MessageCenterService messageCenterService = new MessageCenterService();

    @ResponseBody
    public AdminApiPageDataStandardResponse<List<MessageCenterNoticeResponse>> index() {
        return new AdminApiPageDataStandardResponse<>(messageCenterService.listNotices(), "", request.getUri());
    }
}
