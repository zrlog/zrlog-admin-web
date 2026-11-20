package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.content.ArticleConversationBindings;
import com.zrlog.data.security.AccountAccess;
import jakarta.inject.Inject;
import java.sql.SQLException;

public final class AIArticleConversations implements ArticleConversationBindings {
    private final AIConversationService conversations;

    @Inject
    public AIArticleConversations(AIConversationService conversations) {
        this.conversations = conversations;
    }

    @Override
    public DraftConversationBinding prepareDraftConversationBinding(
            AccountAccess actor, String conversationId, String runId) throws SQLException {
        return conversations
                .captureAccount(actor.getUserId())
                .prepareDraftConversationBinding(conversationId, runId);
    }

    @Override
    public void articleDeleted(long articleId) {
        conversations.removeAIMessage(articleId);
    }
}
