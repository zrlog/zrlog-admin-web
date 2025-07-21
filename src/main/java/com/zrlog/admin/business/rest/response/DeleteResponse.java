package com.zrlog.admin.business.rest.response;

import com.zrlog.util.I18nUtil;

import java.util.Objects;

public class DeleteResponse extends AdminApiPageDataStandardResponse<DeleteResponse.DeleteResponseData> {

    public DeleteResponse() {
        this(false);
    }

    public DeleteResponse(boolean delete) {
        super(new DeleteResponse.DeleteResponseData(delete));
        if (Objects.nonNull(data)) {
            setMessage(Objects.equals(data.delete, true) ? I18nUtil.getAdminBackendStringFromRes("deleteSuccess") : I18nUtil.getAdminBackendStringFromRes("deleteError"));
            setError(Objects.equals(data.delete, true) ? 0 : 1);
        } else {
            setMessage(I18nUtil.getAdminBackendStringFromRes("deleteError"));
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
