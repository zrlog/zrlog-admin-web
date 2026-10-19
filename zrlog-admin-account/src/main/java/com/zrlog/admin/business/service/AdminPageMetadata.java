package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.MessageCenterStatusResponse;

/** Values copied into the existing, flat page response contract. */
public final class AdminPageMetadata {
    private final String pageBuildId;
    private final String documentTitle;
    private final String systemNotification;
    private final MessageCenterStatusResponse messageCenter;

    public AdminPageMetadata(String pageBuildId, String documentTitle, String systemNotification,
                             MessageCenterStatusResponse messageCenter) {
        this.pageBuildId = pageBuildId;
        this.documentTitle = documentTitle;
        this.systemNotification = systemNotification;
        this.messageCenter = messageCenter;
    }
    public String getPageBuildId() { return pageBuildId; }
    public String getDocumentTitle() { return documentTitle; }
    public String getSystemNotification() { return systemNotification; }
    public MessageCenterStatusResponse getMessageCenter() { return messageCenter; }
}
