package com.zrlog.admin.business.content;

import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.common.Constants;
import com.zrlog.web.WebSetup;

/** Optional article assistance supplied by an enabled WebSetup. Content also works without it. */
public interface ArticleAssistant extends WebSetup {
    ArticleAssistant NONE = new ArticleAssistant() { };

    static ArticleAssistant current() {
        ArticleAssistant assistant = Constants.zrLogConfig == null ? null
                : Constants.zrLogConfig.getWebSetup(ArticleAssistant.class);
        return assistant == null ? NONE : assistant;
    }

    @Override default void setup() { }
    default void articleCreated(long articleId, int userId) { }
    default void articleDeleted(long articleId) { }
    default ArticleGlobalResponse enrichEditor(ArticleGlobalResponse article, long articleId) { return article; }
    default PublishProgress beginPublishCheck(ArticleGlobalResponse article, CreateArticleRequest request,
                                             AdminSseEmitter emitter) throws Exception {
        return PublishProgress.NONE;
    }

    interface PublishProgress {
        PublishProgress NONE = new PublishProgress() { };
        default void progress(AdminSseEmitter emitter) throws Exception { }
        default void complete(AdminSseEmitter emitter) throws Exception { }
    }
}
