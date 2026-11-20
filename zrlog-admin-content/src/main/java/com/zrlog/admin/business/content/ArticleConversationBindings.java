package com.zrlog.admin.business.content;

import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.security.AccountAccess;
import java.sql.SQLException;

/** Optional writing conversations attached to articles. */
public interface ArticleConversationBindings {
    ArticleConversationBindings NONE = new ArticleConversationBindings() {};

    default DraftConversationBinding prepareDraftConversationBinding(
            AccountAccess actor, String conversationId, String runId) throws SQLException {
        throw new ArgsException("conversationId");
    }

    default void articleDeleted(long articleId) {}

    interface DraftConversationBinding extends AutoCloseable {
        void bind(long articleId) throws SQLException;

        @Override
        void close() throws SQLException;
    }
}
