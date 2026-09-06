package com.zrlog.admin.util;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.server.util.NativeImageUtils;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.business.service.LinkPreviewService;
import com.zrlog.admin.business.ai.prompt.AIPromptVO;
import com.zrlog.admin.plugin.rest.response.UploadServiceResponseEntity;
import com.zrlog.business.dto.StoredUpgradeNotice;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.rest.response.*;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.BaseTemplateVO;
import com.zrlog.common.vo.LockVO;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.data.dto.CommentDTO;
import com.zrlog.data.dto.FaviconBase64DTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminNativeImageUtils {

    private static void adminRequestJson() {
        //post
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(CreateArticleRequest.class, CreateTypeRequest.class, LoginRequest.class,
                CreateOrUpdateArticleResponse.class, CreateLinkRequest.class, AbstractNavEntry.class, CreateNavRequest.class,
                UpdateTemplateConfigRequest.class, TemplateVO.TemplateConfigMap.class));
        //update
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(UpdateNavRequest.class, UpdateTypeRequest.class, UpdateLinkRequest.class, UpdateAdminRequest.class,
                ReadCommentRequest.class, UpdatePasswordRequest.class, UpdateArticleRequest.class, UpdateMfaRequest.class,
                OptimizeWebsiteDescriptionRequest.class, OptimizeAiPromptRequest.class, GenerateArticleTitleRequest.class,
                ApplyArticleCoverRequest.class,
                AddArticleAIContextRequest.class, GenerateArticleFieldRequest.class, ScoreArticleRequest.class,
                ArticleVersionRollbackRequest.class, ArticlePinningRequest.class, MoveArticlePinningRequest.class,
                ReplaceArticleResourceUrlRequest.class, PersonalDataPreviewRequest.class));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(AdminDashboardConfigRequest.class,
                AdminDashboardCardRequest.class, FirstUseChecklistRequest.class,
                WebhookConfigRequest.class, WebhookMessageNoticeRequest.class,
                TagManageRequest.class, ReadMessageCenterNoticeRequest.class, UpgradeRestartNoticeRequest.class,
                ExecuteUpgradeRequest.class));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(
                PasskeyAuthenticationVerifyRequest.class, PasskeyRegistrationOptionsRequest.class,
                PasskeyRegistrationVerifyRequest.class, PasskeyRemoveRequest.class,
                PasskeyCredential.class, PasskeyCredential.AuthenticatorResponse.class,
                com.zrlog.admin.business.service.PasskeyService.ChallengeState.class));
    }

    private static void adminResponseJson() {
        //
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(TemplateDownloadResponse.class, ArticleResponseEntry.class,
                UpgradeProcessResponse.class,
                PreCheckVersionResponse.class, BackupProtectionStatus.class,
                BlogWebSiteInfo.class, OtherWebSiteInfo.class, AdminWebSiteInfo.class,
                UpgradeWebSiteInfo.class, ArticleEditWebSiteInfo.class, TemplateVO.class, TemplateVO.TemplateConfigVO.class,
                BaseTemplateVO.class, BasicWebSiteInfo.class, ArticleGlobalResponse.class,
                FeatureLabWebSiteInfo.class, ContentProtectorWebSiteInfo.class,
                PageData.class, ArticlePageData.class, LinkDTO.class, LogNavDTO.class, TypeDTO.class, CommentDTO.class,
                ApiStandardResponse.class, UploadFileResponse.class,
                AdminManifestResponse.class, AdminManifestResponse.Icon.class,
                IndexResponse.class, StatisticsInfoResponse.class, ServerSideDataResponse.class,
                AdminResourceInfoResponse.class, AdminResourceInfoResponse.DefaultLoginInfo.class,
                AdminDashboardConfigResponse.class, AdminDashboardCardConfigResponse.class,
                AdminDashboardCardResponse.class, AdminDashboardWelcomeDataResponse.class,
                AdminDashboardQuickActionDataResponse.class, AdminDashboardAuditTrailDataResponse.class,
                FirstUseChecklistResponse.class,
                AdminAuditLogEntryResponse.class, AdminDashboardDataInsightsResponse.class,
                AdminSsePayloads.Message.class, AdminSsePayloads.Tool.class, AdminSsePayloads.Error.class,
                UserInfoResponse.class, UserBasicInfoResponse.class, AdminTokenVO.class, AdminFullTokenVO.class,
                VersionResponse.class, PublicVersionResponse.class, PluginStatusResponse.class, UploadTemplateResponse.class,
                UploadTemplateResponse.UploadTemplateData.class,
                DeleteResponse.class, DeleteResponse.DeleteResponseData.class, UpdateRecordResponse.class,
                LoadEditArticleResponse.class, ServerInfo.class,
                SystemResponse.class, ErrorPageResponse.class, DevInfoResponse.class, WebsiteKvEntryResponse.class,
                LockVO.class, FaviconBase64DTO.class, AdminStaticSiteSyncResponse.class, UploadServiceResponseEntity.class,
                TemplateValuePreviewResponse.class,
                AIResponseEntry.class, AIResponseEntry.AIContentEntry.class,
                AIResponseEntry.AIContentEntry.ArticleContextMeta.class,
                com.zrlog.admin.business.ai.dto.AIStreamPayloads.Chunk.class,
                com.zrlog.admin.business.ai.dto.AIStreamPayloads.ErrorPayload.class,
                com.zrlog.admin.business.ai.dto.AIStreamPayloads.CoverPayload.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.Titles.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.Tags.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.Markdown.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ArticleScore.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ArticleScoreItem.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ArticleSeo.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ArticleSeoItem.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ArticleProofread.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ArticleProofreadItem.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ArticleStructure.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ArticleStructureItem.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ReaderQuestions.class,
                com.zrlog.admin.business.ai.dto.AIToolResponsePayloads.ReaderQuestionItem.class,
                PublishCheckResponse.class, PublishCheckToolPayload.class,
                AIWebSiteInfo.class, AIWebSiteInfoWithAIMessages.class, ArticleActivityData.class,
                AIWebSiteInfoResponse.class, AIWebSiteInfoResponse.AIProvider.class, ArticleAIMessageExportResponse.class,
                com.zrlog.admin.business.ai.model.AIModelCapability.class,
                com.zrlog.admin.business.ai.model.AIModelEntry.class,
                com.zrlog.admin.business.ai.model.AIProviderType.class,
                com.zrlog.admin.business.ai.model.AIProviderRequests.CompletionRequest.class,
                com.zrlog.admin.business.ai.model.AIProviderRequests.Message.class,
                com.zrlog.admin.business.ai.model.AIProviderRequests.ImageGenerationRequest.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.CompletionResponse.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.ErrorPayload.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.Choice.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.Message.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.Delta.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.ImageGenerationResponse.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.ImageData.class,
                com.zrlog.admin.business.type.FileEntryAccess.class,
                com.zrlog.admin.business.type.FileEntryAction.class,
                com.zrlog.admin.business.type.FileDirectoryAction.class,
                UpdateAIMessageRequest.class,
                OptimizeWebsiteDescriptionResponse.class, OptimizeAiPromptResponse.class, GenerateArticleTitleResponse.class,
                GenerateArticleCoverResponse.class,
                GenerateArticleAliasResponse.class, GenerateArticleDigestResponse.class,
                GenerateArticleMarkdownResponse.class, GenerateArticleTagsResponse.class,
                ScoreArticleResponse.class, ScoreArticleResponse.ScoreItem.class,
                ArticleSeoCheckResponse.class, ArticleSeoCheckResponse.SeoItem.class,
                ArticleProofreadResponse.class, ArticleProofreadResponse.ProofreadItem.class,
                ArticleStructureAdviceResponse.class, ArticleStructureAdviceResponse.StructureItem.class,
                ArticleReaderQuestionsResponse.class, ArticleReaderQuestionsResponse.ReaderQuestionItem.class,
                ArticleStatusCountResponse.class, ArticleVersionCompareResponse.class, ArticleVersionResponse.class,
                ArticlePinningEntryResponse.class, ArticlePinningResponse.class,
                PluginInfoResponse.class,
                TagManagementArticleImpactResponse.class, TagManagementEntryResponse.class, TagManagementPreviewResponse.class,
                MessageCenterStatusResponse.class, MessageCenterNoticeResponse.class, MessageCenterNoticeResponse.VersionUpdatePayload.class,
                MessageCenterNoticeResponse.UnreadCommentPayload.class,
                MessageCenterNoticeResponse.WebhookMessagePayload.class,
                MessageCenterNoticeResponse.OperationTaskPayload.class, MessageCenterOperationNoticeEntry.class,
                MessageCenterOperationNoticeEntry.ReplaceArticleResourceUrlPayload.class,
                MessageCenterOperationNoticeEntry.StaticSiteSyncPayload.class,
                MessageCenterOperationNoticeEntry.UpgradePayload.class,
                MessageCenterOperationNoticeEntry.UpgradeRestartPayload.class,
                MessageCenterOperationNoticeEntry.PublishCheckPayload.class,
                WebhookConfigEntry.class, WebhookConfigResponse.class, WebhookTokenResponse.class,
                WebhookMessageNoticeEntry.class, WebhookMessageNoticeCreateResponse.class,
                PersonalDataPreviewResponse.class, PersonalDataCommentExportResponse.class,
                PersonalDataCommentExportResponse.CommentEntry.class,
                MfaStatusResponse.class, StaticSiteProgressResponse.class, StoredUpgradeNotice.class,
                PasskeyOptionsResponse.class, PasskeyCredentialDescriptor.class,
                PasskeyRegistrationOptionsResponse.class, PasskeyRegistrationOptionsResponse.RelyingParty.class,
                PasskeyRegistrationOptionsResponse.User.class, PasskeyRegistrationOptionsResponse.CredentialParameter.class,
                PasskeyRegistrationOptionsResponse.AuthenticatorSelection.class,
                PasskeyAuthenticationOptionsResponse.class, PasskeySummaryResponse.class,
                AdminPageDataResponse.class, FileEntryVO.class, FileReferenceVO.class,
                FileReferenceIndexCacheVO.class, FileManagerResponse.class, ReplaceArticleResourceUrlResponse.class,
                LinkPreviewResponse.class, LinkPreviewService.LinkPreviewCacheEntry.class,
                com.zrlog.common.vo.SocialPreviewDTO.class, CheckVersionResponse.class));
    }

    private static void adminJson() {
        adminResponseJson();
        adminRequestJson();
    }


    static List<String> getResources(AdminResource adminResource) {
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
        for (AIPromptVO promptVO : AIPromptVO.getAll()) {
            if (promptVO.getPromptPrefix() != null) {
                resourceUris.add(promptVO.getPromptPrefix() + "zh_CN.md");
                resourceUris.add(promptVO.getPromptPrefix() + "en_US.md");
            }
            if (promptVO.getInputPrefix() != null) {
                resourceUris.add(promptVO.getInputPrefix() + "zh_CN.md");
                resourceUris.add(promptVO.getInputPrefix() + "en_US.md");
            }
        }
        resourceUris.add(AdminConstants.BUILD_SYSTEM_INFO_MD);
        return resourceUris;
    }

    public static void reg(AdminResource adminResource) {
        NativeImageUtils.doResourceLoadByResourceNames(getResources(adminResource));
        adminJson();
    }
}
