package com.zrlog.admin.business.content;

import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.web.WebSetup;
import java.sql.SQLException;

/** Optional article assistance supplied by an enabled WebSetup. Content also works without it. */
public interface ArticleAssistant extends WebSetup {
    ArticleAssistant NONE = new ArticleAssistant() {};

    @Override
    default void setup() {}

    default DraftConversationBinding prepareDraftConversationBinding(
            AccountAccess actor, String conversationId, String runId) throws SQLException {
        throw new ArgsException("conversationId");
    }

    interface DraftConversationBinding extends AutoCloseable {
        void bind(long articleId) throws SQLException;

        @Override
        void close() throws SQLException;
    }

    default void articleDeleted(long articleId) {}

    default ArticleGlobalResponse enrichEditor(
            ArticleGlobalResponse article, long articleId, int userId) {
        return article;
    }

    default PublishProgress beginPublishCheck(
            AccountAccess actor,
            ArticleGlobalResponse article,
            CreateArticleRequest request,
            AdminSseEmitter emitter)
            throws Exception {
        return PublishProgress.NONE;
    }

    interface PublishProgress {
        PublishProgress NONE = new PublishProgress() {};

        default void progress(AdminSseEmitter emitter) throws Exception {}

        default void complete(AdminSseEmitter emitter) throws Exception {}
    }
}
