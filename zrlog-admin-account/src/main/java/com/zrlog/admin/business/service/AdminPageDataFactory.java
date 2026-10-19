package com.zrlog.admin.business.service;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.MessageCenterStatusResponse;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.util.WebSiteUtils;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.function.Supplier;

/** Assembles page metadata from the owning application at response time. */
@Singleton
public final class AdminPageDataFactory {
    private final AccountPermissionService permissionService;

    private final Supplier<PublicWebSiteInfo> site;
    private final Supplier<String> buildId;
    private final MessageCenterStateService messages;

    @Inject
    public AdminPageDataFactory(
            Supplier<PublicWebSiteInfo> site,
            @Named("adminBuildId") Supplier<String> buildId,
            MessageCenterStateService messages,
            AccountPermissionService permissionService) {
        this.permissionService = permissionService;

        this.messages = messages;

        this.site = Objects.requireNonNull(site);
        this.buildId = Objects.requireNonNull(buildId);
    }

    private PublicWebSiteInfo siteInfo() {
        return Objects.requireNonNullElseGet(site.get(), () -> WebSiteUtils.fillDefaultInfo(new PublicWebSiteInfo()));
    }

    public String title(String uri) {
        return AdminConstants.getAdminDocumentTitleByUri(uri, siteInfo());
    }

    public AdminPageMetadata metadata(String uri) {
        PublicWebSiteInfo info = siteInfo();
        String notification = info.getSystem_notification();
        if (StringUtils.isEmpty(notification)) {
            notification = Objects.requireNonNullElse(System.getenv("SYSTEM_NOTIFICATION"), "");
        }
        MessageCenterStatusResponse status =
                AdminTokenThreadLocal.getUser() != null
                                && !permissionService.current().isAdministrator()
                        ? new MessageCenterStatusResponse()
                        : messages.current();
        return new AdminPageMetadata(Objects.requireNonNullElse(buildId.get(), ""),
                StringUtils.isNotEmpty(uri) ? AdminConstants.getAdminDocumentTitleByUri(uri, info) : null,
                notification, status);
    }

    public <T> AdminPageDataResponse<T> response(T data) {
        return response(data, "", null);
    }

    public <T> AdminPageDataResponse<T> response(T data, String message, String uri) {
        return new AdminPageDataResponse<>(data, message, metadata(uri));
    }
}
