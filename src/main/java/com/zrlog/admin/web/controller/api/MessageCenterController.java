package com.zrlog.admin.web.controller.api;

import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.ReadMessageCenterNoticeRequest;
import com.zrlog.admin.business.rest.request.UpgradeRestartNoticeRequest;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.service.MessageCenterService;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;

import java.sql.SQLException;
import java.util.List;

public class MessageCenterController extends BaseController {

    private final MessageCenterService messageCenterService = new MessageCenterService();

    @ResponseBody
    public ApiStandardResponse<List<MessageCenterNoticeResponse>> index() throws SQLException {
        return new ApiStandardResponse<>(messageCenterService.listNotices());
    }

    @ResponseBody
    public UpdateRecordResponse read() {
        ReadMessageCenterNoticeRequest readRequest = getRequestBodyWithNullCheck(ReadMessageCenterNoticeRequest.class);
        return new UpdateRecordResponse(messageCenterService.markRead(readRequest.getTaskKey()));
    }

    @ResponseBody
    public UpdateRecordResponse upgradeRestart() {
        UpgradeRestartNoticeRequest noticeRequest = getRequestBodyWithNullCheck(UpgradeRestartNoticeRequest.class);
        new MessageCenterOperationService().recordUpgradeRestart(noticeRequest.getStatus(), noticeRequest.getBuildId());
        return new UpdateRecordResponse(true);
    }
}
