package com.zrlog.admin.util;

import com.hibegin.http.server.util.NativeImageUtils;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.plugin.rest.response.UploadServiceResponseEntity;
import com.zrlog.business.rest.response.PluginStatusResponse;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.data.dto.FaviconBase64DTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminNativeImageUtils {

    private static void adminRequestJson() {
        //post
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(CreateArticleRequest.class, CreateTypeRequest.class, LoginRequest.class,
                CreateOrUpdateArticleResponse.class, CreateLinkRequest.class, CreateNavRequest.class,
                UpdateTemplateConfigRequest.class, TemplateVO.TemplateConfigMap.class));
        //update
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(UpdateNavRequest.class, UpdateTypeRequest.class, UpdateLinkRequest.class, UpdateAdminRequest.class,
                ReadCommentRequest.class, UpdatePasswordRequest.class, UpdateArticleRequest.class, UpdateMfaRequest.class));
    }

    private static void adminResponseJson() {
        //
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(TemplateDownloadResponse.class, ArticleResponseEntry.class,
                DownloadUpdatePackageResponse.class, UpgradeProcessResponse.class,
                PreCheckVersionResponse.class, BlogWebSiteInfo.class, OtherWebSiteInfo.class, AdminWebSiteInfo.class,
                UpgradeWebSiteInfo.class, TemplateVO.class, BasicWebSiteInfo.class, ArticleGlobalResponse.class,
                ArticlePageData.class, ApiStandardResponse.class, UploadFileResponse.class,
                IndexResponse.class, StatisticsInfoResponse.class, ServerSideDataResponse.class,
                UserBasicInfoResponse.class, AdminTokenVO.class, AdminFullTokenVO.class,
                VersionResponse.class, PluginStatusResponse.class, UploadTemplateResponse.class,
                DeleteResponse.class, DeleteResponse.DeleteResponseData.class,
                LoadEditArticleResponse.class, ServerInfo.class,
                SystemResponse.class, FaviconBase64DTO.class, AdminStaticSiteSyncResponse.class, UploadServiceResponseEntity.class,
                TemplateValuePreviewResponse.class,
                AIResponseEntry.class, AIResponseEntry.AIContentEntry.class,
                AIWebSiteInfo.class, AIWebSiteInfoWithAIMessages.class, ArticleActivityData.class,
                AIWebSiteInfoResponse.class, AIWebSiteInfoResponse.AIProvider.class,
                ArticleStatusCountResponse.class, ArticleVersionCompareResponse.class, ArticleVersionResponse.class,
                HealthCheckIssueResponse.class, HealthCheckResponse.class, HealthCheckSuggestionResponse.class,
                MessageCenterNoticeResponse.class, MfaStatusResponse.class));
    }

    private static void adminJson() {
        adminResponseJson();
        adminRequestJson();
    }


    private static List<String> getResources(AdminResource adminResource) {
        List<String> resourceUris = new ArrayList<>(adminResource.getAdminStaticResourceUris());
        resourceUris.add("/assets/admin/images/default-portrait.gif");
        resourceUris.add(AdminConstants.ADMIN_HTML_PAGE);
        resourceUris.add(AdminConstants.ADMIN_PWA_MANIFEST_JSON);
        resourceUris.add(AdminConstants.ADMIN_SERVICE_WORKER_JS);
        resourceUris.add(AdminResource.ADMIN_ASSET_MANIFEST_JSON);
        resourceUris.add(AdminConstants.FAVICON_PNG_PWA_192_URI_PATH);
        resourceUris.add(AdminConstants.FAVICON_PNG_PWA_512_URI_PATH);
        resourceUris.add("/i18n/admin_backend_zh_CN.properties");
        resourceUris.add("/i18n/admin_backend_en_US.properties");
        resourceUris.add(AdminConstants.BUILD_SYSTEM_INFO_MD);
        return resourceUris;
    }

    public static void reg(AdminResource adminResource) {
        NativeImageUtils.doResourceLoadByResourceNames(getResources(adminResource));
        adminJson();
    }
}
