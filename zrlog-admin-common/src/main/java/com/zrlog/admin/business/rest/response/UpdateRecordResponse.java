package com.zrlog.admin.business.rest.response;

import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.I18nUtil;

public class UpdateRecordResponse extends ApiStandardResponse<Object> {

    public UpdateRecordResponse() {
    }

    public UpdateRecordResponse(Boolean success) {
        setError(success ? 0 : 1);
        setMessage(success ? I18nUtil.getAdminBackendStringFromRes("admin.common.update.success") : I18nUtil.getAdminBackendStringFromRes("admin.common.update.error"));
    }
}
