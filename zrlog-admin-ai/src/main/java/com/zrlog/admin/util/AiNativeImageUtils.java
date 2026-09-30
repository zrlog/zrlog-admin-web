package com.zrlog.admin.util;

import com.hibegin.http.server.util.NativeImageUtils;
import java.util.*;

public final class AiNativeImageUtils {
    private AiNativeImageUtils() { }
    public static void reg() {
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(
                com.zrlog.admin.business.rest.request.AnalyzeCommentRequest.class,
                com.zrlog.admin.business.rest.response.AnalyzeCommentResponse.class,
                com.zrlog.admin.business.rest.request.OptimizeWebsiteDescriptionRequest.class,
                com.zrlog.admin.business.rest.request.OptimizeAiPromptRequest.class,
                com.zrlog.admin.business.rest.request.GenerateArticleTitleRequest.class,
                com.zrlog.admin.business.rest.request.ApplyArticleCoverRequest.class,
                com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest.class,
                com.zrlog.admin.business.rest.request.ScoreArticleRequest.class,
                com.zrlog.admin.business.rest.response.AIArticleGlobalResponse.class,
                com.zrlog.admin.business.rest.response.AIResponseEntry.class,
                com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry.class,
                com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry.ArticleContextMeta.class,
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
                com.zrlog.admin.business.rest.response.PublishCheckResponse.class,
                com.zrlog.admin.business.rest.response.PublishCheckToolPayload.class,
                com.zrlog.admin.business.rest.base.AIWebSiteInfo.class,
                com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages.class,
                com.zrlog.admin.business.rest.response.AIWebSiteInfoResponse.class,
                com.zrlog.admin.business.rest.response.AIWebSiteInfoResponse.AIProvider.class,
                com.zrlog.admin.business.rest.response.ArticleAIMessageExportResponse.class,
                com.zrlog.admin.business.ai.model.AIModelCapability.class,
                com.zrlog.admin.business.ai.model.AIModelEntry.class,
                com.zrlog.admin.business.ai.model.AIModelCatalogDocument.class,
                com.zrlog.admin.business.ai.model.AIModelCatalogDocument.Provider.class,
                com.zrlog.admin.business.ai.model.AIProviderType.class,
                com.zrlog.admin.business.ai.model.AIProviderRequests.CompletionRequest.class,
                com.zrlog.admin.business.ai.model.AIProviderRequests.Message.class,
                com.zrlog.admin.business.ai.model.AIProviderRequests.ImageGenerationRequest.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.CompletionResponse.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.ErrorPayload.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.Choice.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.Message.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.Delta.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.ToolCallDelta.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.ImageGenerationResponse.class,
                com.zrlog.admin.business.ai.model.AIProviderResponses.ImageData.class,
                com.zrlog.admin.business.rest.request.UpdateAIMessageRequest.class,
                com.zrlog.admin.business.rest.response.OptimizeWebsiteDescriptionResponse.class,
                com.zrlog.admin.business.rest.response.OptimizeAiPromptResponse.class,
                com.zrlog.admin.business.rest.response.GenerateArticleTitleResponse.class,
                com.zrlog.admin.business.rest.response.GenerateArticleCoverResponse.class,
                com.zrlog.admin.business.rest.response.GenerateArticleAliasResponse.class,
                com.zrlog.admin.business.rest.response.GenerateArticleDigestResponse.class,
                com.zrlog.admin.business.rest.response.GenerateArticleMarkdownResponse.class,
                com.zrlog.admin.business.rest.response.GenerateArticleTagsResponse.class,
                com.zrlog.admin.business.rest.response.ScoreArticleResponse.class,
                com.zrlog.admin.business.rest.response.ScoreArticleResponse.ScoreItem.class,
                com.zrlog.admin.business.rest.response.ArticleSeoCheckResponse.class,
                com.zrlog.admin.business.rest.response.ArticleSeoCheckResponse.SeoItem.class,
                com.zrlog.admin.business.rest.response.ArticleProofreadResponse.class,
                com.zrlog.admin.business.rest.response.ArticleProofreadResponse.ProofreadItem.class,
                com.zrlog.admin.business.rest.response.ArticleStructureAdviceResponse.class,
                com.zrlog.admin.business.rest.response.ArticleStructureAdviceResponse.StructureItem.class,
                com.zrlog.admin.business.rest.response.ArticleReaderQuestionsResponse.class,
                com.zrlog.admin.business.rest.response.ArticleReaderQuestionsResponse.ReaderQuestionItem.class));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(com.zrlog.admin.business.ai.model.AIChatModels.class.getDeclaredClasses()));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(com.zrlog.admin.business.ai.model.AIProviderRequests.class.getDeclaredClasses()));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(com.zrlog.admin.business.ai.model.OpenAIResponses.class.getDeclaredClasses()));
    }
    public static List<String> resources() {
        List<String> resourceUris = new ArrayList<>();
        resourceUris.add("/ai/comment-review/prompt_zh_CN.md");
        resourceUris.add("/ai/comment-review/prompt_en_US.md");
        resourceUris.add(com.zrlog.admin.business.ai.model.AIModelCatalog.RESOURCE);
        for (com.zrlog.admin.business.ai.prompt.AIPromptVO promptVO : com.zrlog.admin.business.ai.prompt.AIPromptVO.getAll()) {
            if (promptVO.getPromptPrefix() != null) {
                resourceUris.add(promptVO.getPromptPrefix() + "zh_CN.md");
                resourceUris.add(promptVO.getPromptPrefix() + "en_US.md");
            }
            if (promptVO.getInputPrefix() != null) {
                resourceUris.add(promptVO.getInputPrefix() + "zh_CN.md");
                resourceUris.add(promptVO.getInputPrefix() + "en_US.md");
            }
        }
        return resourceUris;
    }
}
