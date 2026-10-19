package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.service.AdminPageMetadata;
import com.zrlog.common.rest.response.StandardResponse;


/**
 * Response for admin page hydration APIs.
 * <p>
 * This response includes page-level metadata used by the admin shell:
 * static resource build id, document title, system notification, and message center status.
 * Use ApiStandardResponse for ordinary actions, polling endpoints, public APIs, and SSE response events.
 */
public class AdminPageDataResponse<T> extends StandardResponse {
    protected final String pageBuildId;
    protected String documentTitle;
    protected final T data;
    protected final String systemNotification;
    protected final MessageCenterStatusResponse messageCenter;

    public AdminPageDataResponse(T data, String message, AdminPageMetadata metadata) {
        this.data = data;
        this.setMessage(message);
        this.pageBuildId = metadata.getPageBuildId();
        this.documentTitle = metadata.getDocumentTitle();
        this.systemNotification = metadata.getSystemNotification();
        this.messageCenter = metadata.getMessageCenter();
    }

    public T getData() {
        return data;
    }

    public String getPageBuildId() {
        return pageBuildId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public MessageCenterStatusResponse getMessageCenter() {
        return messageCenter;
    }
}
