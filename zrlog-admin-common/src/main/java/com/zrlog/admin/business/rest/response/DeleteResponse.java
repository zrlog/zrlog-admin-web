package com.zrlog.admin.business.rest.response;

import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.I18nUtil;

import java.util.Objects;

public class DeleteResponse extends ApiStandardResponse<DeleteResponse.DeleteResponseData> {

    public DeleteResponse() {
        this(false);
    }

    public DeleteResponse(boolean delete) {
        super(new DeleteResponse.DeleteResponseData(delete));
        DeleteResponseData data = getData();
        if (Objects.nonNull(data)) {
            setMessage(Objects.equals(data.delete, true) ? I18nUtil.getAdminBackendStringFromRes("admin.common.delete.success") : I18nUtil.getAdminBackendStringFromRes("admin.common.delete.error"));
            setError(Objects.equals(data.delete, true) ? 0 : 1);
        } else {
            setMessage(I18nUtil.getAdminBackendStringFromRes("admin.common.delete.error"));
            setError(1);
        }
    }

    public static final class DeleteResponseData {
        private final Boolean delete;

        public DeleteResponseData(Boolean delete) {
            this.delete = delete;
        }

        public Boolean getDelete() {
            return delete;
        }
    }
}
