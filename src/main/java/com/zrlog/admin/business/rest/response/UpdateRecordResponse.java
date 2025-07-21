package com.zrlog.admin.business.rest.response;

import com.zrlog.util.I18nUtil;

public class UpdateRecordResponse extends AdminApiPageDataStandardResponse<Object> {

    public UpdateRecordResponse() {
    }

    public UpdateRecordResponse(Boolean success) {
        setError(success ? 0 : 1);
        setMessage(success ? I18nUtil.getAdminBackendStringFromRes("updateSuccess") : I18nUtil.getAdminBackendStringFromRes("updateError"));
    }
}
