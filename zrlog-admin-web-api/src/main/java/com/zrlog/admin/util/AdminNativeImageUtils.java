package com.zrlog.admin.util;

import com.hibegin.http.server.util.NativeImageUtils;
import com.zrlog.admin.business.AdminConstants;
import java.util.*;

public class AdminNativeImageUtils {

    private static void adminJson() {
        CommonNativeImageUtils.reg();
        AccountNativeImageUtils.reg();
        ContentNativeImageUtils.reg();
        AssetsNativeImageUtils.reg();
        AiNativeImageUtils.reg();
        AccessNativeImageUtils.reg();
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(
                com.zrlog.admin.business.rest.request.UpdateTemplateConfigRequest.class,
                com.zrlog.admin.business.rest.request.AdminDashboardConfigRequest.class,
                com.zrlog.admin.business.rest.request.AdminDashboardCardRequest.class,
                com.zrlog.admin.business.rest.request.ReadMessageCenterNoticeRequest.class,
                com.zrlog.admin.business.rest.request.UpgradeRestartNoticeRequest.class,
                com.zrlog.admin.business.rest.request.ExecuteUpgradeRequest.class,
                com.zrlog.admin.business.rest.response.TemplateDownloadResponse.class,
                com.zrlog.admin.business.rest.response.TemplateEntryResponse.class,
                com.zrlog.admin.business.rest.response.IndexResponse.class,
                com.zrlog.admin.business.rest.response.StatisticsInfoResponse.class,
                com.zrlog.admin.business.rest.response.AdminDashboardWelcomeDataResponse.class,
                com.zrlog.admin.business.rest.response.AdminDashboardQuickActionDataResponse.class,
                com.zrlog.admin.business.rest.response.AdminDashboardAuditTrailDataResponse.class,
                com.zrlog.admin.business.rest.response.FirstUseChecklistResponse.class,
                com.zrlog.admin.business.rest.response.AdminDashboardDataInsightsResponse.class,
                com.zrlog.admin.business.rest.response.PublicVersionResponse.class,
                com.zrlog.admin.business.rest.response.UploadTemplateResponse.class,
                com.zrlog.admin.business.rest.response.UploadTemplateResponse.UploadTemplateData.class,
                com.zrlog.admin.util.ServerInfo.class,
                com.zrlog.admin.business.rest.response.SystemResponse.class,
                com.zrlog.admin.business.rest.response.ErrorPageResponse.class,
                com.zrlog.admin.business.rest.response.DevInfoResponse.class,
                com.zrlog.admin.business.rest.response.TemplateValuePreviewResponse.class,
                com.zrlog.admin.business.rest.response.PluginInfoResponse.class));
    }

    static List<String> getResources() {
        List<String> resourceUris = new ArrayList<>();
        resourceUris.add("/assets/admin/images/default-portrait.gif");
        resourceUris.add("/i18n/admin_backend_zh_CN.properties");
        resourceUris.add("/i18n/admin_backend_en_US.properties");
        resourceUris.addAll(AiNativeImageUtils.resources());
        resourceUris.add(AdminConstants.BUILD_SYSTEM_INFO_MD);
        return resourceUris;
    }

    public static void reg() {
        NativeImageUtils.doResourceLoadByResourceNames(getResources());
        adminJson();
    }
}
