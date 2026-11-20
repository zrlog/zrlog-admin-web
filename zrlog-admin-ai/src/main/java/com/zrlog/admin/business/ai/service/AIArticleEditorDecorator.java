package com.zrlog.admin.business.ai.service;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.content.ArticleEditorDecorator;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.response.AIArticleGlobalResponse;
import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AIArticleEditorDecorator implements ArticleEditorDecorator {
    private final AIConversationService conversations;

    @Inject
    public AIArticleEditorDecorator(AIConversationService conversations) {
        this.conversations = conversations;
    }

    @Override
    public ArticleGlobalResponse enrichEditor(
            ArticleGlobalResponse article, long articleId, int userId) {
        var accountConversations = conversations.captureAccount(userId);
        String draftConversationId = null;
        if (articleId <= 0) {
            try {
                draftConversationId = accountConversations.draftConversationId();
            } catch (SQLException error) {
                throw new IllegalStateException("Unable to load writing draft", error);
            }
        }
        AIWebSiteInfoWithAIMessages ai =
                accountConversations
                        .captureSession(draftConversationId)
                        .getAiMessageInfoByArticleId(articleId <= 0 ? -(long) userId : articleId);
        AIArticleGlobalResponse response = new AIArticleGlobalResponse(article);
        response.setDraftConversationId(draftConversationId);
        response.setAiProvider(ai.getAi_provider());
        response.setAiModel(ai.getAi_model());
        response.setAiFileInput(AIFileService.capabilities(ai));
        response.setAiConfigured(
                ai.getAi_provider() != null
                        && StringUtils.isNotEmpty(ai.getAi_model())
                        && (StringUtils.isNotEmpty(ai.getAi_api_key())
                                || StringUtils.isNotEmpty(ai.getAi_base_url())));
        response.setAiMessages(
                articleId <= 0 && draftConversationId == null
                        ? List.of()
                        : ai.getAiMessages().stream()
                                .filter(e -> !Objects.equals(e.getRole(), "system"))
                                .collect(Collectors.toList()));
        return response;
    }
}
