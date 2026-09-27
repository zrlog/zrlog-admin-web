package com.zrlog.admin.util;

import com.hibegin.http.server.util.NativeImageUtils;
import java.util.*;

public final class ContentNativeImageUtils {
    private ContentNativeImageUtils() { }
    public static void reg() {
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(
                com.zrlog.admin.business.rest.request.CreateArticleRequest.class,
                com.zrlog.admin.business.rest.request.CreateTypeRequest.class,
                com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse.class,
                com.zrlog.admin.business.rest.request.CreateLinkRequest.class,
                com.zrlog.admin.business.rest.base.AbstractNavEntry.class,
                com.zrlog.admin.business.rest.request.CreateNavRequest.class,
                com.zrlog.admin.business.rest.request.UpdateNavRequest.class,
                com.zrlog.admin.business.rest.request.UpdateTypeRequest.class,
                com.zrlog.admin.business.rest.request.UpdateLinkRequest.class,
                com.zrlog.admin.business.rest.request.ReadCommentRequest.class,
                com.zrlog.admin.business.rest.request.UpdateArticleRequest.class,
                com.zrlog.admin.business.rest.request.ArticleVersionRollbackRequest.class,
                com.zrlog.admin.business.rest.request.ArticlePinningRequest.class,
                com.zrlog.admin.business.rest.request.MoveArticlePinningRequest.class,
                com.zrlog.admin.business.rest.request.PersonalDataPreviewRequest.class,
                com.zrlog.admin.business.rest.request.TagManageRequest.class,
                com.zrlog.admin.business.rest.response.ArticleResponseEntry.class,
                com.zrlog.admin.business.rest.response.ArticleGlobalResponse.class,
                com.zrlog.admin.business.rest.response.ArticlePageData.class,
                com.zrlog.admin.business.rest.response.LoadEditArticleResponse.class,
                com.zrlog.admin.business.rest.response.ArticleActivityData.class,
                com.zrlog.admin.business.rest.response.ArticleStatusCountResponse.class,
                com.zrlog.admin.business.rest.response.ArticleVersionCompareResponse.class,
                com.zrlog.admin.business.rest.response.ArticleVersionResponse.class,
                com.zrlog.admin.business.rest.response.ArticlePinningEntryResponse.class,
                com.zrlog.admin.business.rest.response.ArticlePinningResponse.class,
                com.zrlog.admin.business.rest.response.TagManagementArticleImpactResponse.class,
                com.zrlog.admin.business.rest.response.TagManagementEntryResponse.class,
                com.zrlog.admin.business.rest.response.TagManagementPreviewResponse.class,
                com.zrlog.admin.business.rest.response.PersonalDataPreviewResponse.class,
                com.zrlog.admin.business.rest.response.PersonalDataCommentExportResponse.class,
                com.zrlog.admin.business.rest.response.PersonalDataCommentExportResponse.CommentEntry.class,
                com.zrlog.admin.business.rest.response.LinkPreviewResponse.class,
                com.zrlog.admin.business.service.LinkPreviewService.LinkPreviewCacheEntry.class));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(com.zrlog.admin.business.knowledge.KnowledgeModels.class.getDeclaredClasses()));
    }
}
