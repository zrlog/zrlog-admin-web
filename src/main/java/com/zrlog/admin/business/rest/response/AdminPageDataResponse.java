package com.zrlog.admin.business.rest.response;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.MessageCenterStateService;
import com.zrlog.common.Constants;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.rest.response.StandardResponse;
import com.zrlog.common.vo.PublicWebSiteInfo;

import java.util.Objects;

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

    public AdminPageDataResponse() {
        this(null);
    }

    public AdminPageDataResponse(T data) {
        this(data, "");
    }

    public T getData() {
        return data;
    }

    public AdminPageDataResponse(T data, String message) {
        this(data, message, null);
    }

    public AdminPageDataResponse(T data, String message, String requestUri) {
        this.data = data;
        this.setMessage(message);
        ZrLogConfig zrLogConfig = Constants.zrLogConfig;
        if (Objects.nonNull(zrLogConfig) && Objects.nonNull(AdminConstants.adminResource)) {
            this.pageBuildId = AdminConstants.adminResource.getStaticResourceBuildId();
        } else {
            this.pageBuildId = "";
        }
        PublicWebSiteInfo publicWebSiteInfo = AdminConstants.getPublicWebSiteInfo();
        if (StringUtils.isNotEmpty(requestUri)) {
            this.documentTitle = AdminConstants.getAdminDocumentTitleByUri(requestUri, publicWebSiteInfo);
        }
        String configMsg = publicWebSiteInfo.getSystem_notification();
        if (StringUtils.isNotEmpty(configMsg)) {
            this.systemNotification = configMsg;
        } else {
            this.systemNotification = Objects.requireNonNullElse(System.getenv("SYSTEM_NOTIFICATION"), "");
        }
        this.messageCenter = new MessageCenterStateService().current();
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
