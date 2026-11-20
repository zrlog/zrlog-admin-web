package com.zrlog.admin.business.content;

import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.data.security.AccountAccess;

/** Optional article checks and their publish stream events. */
@FunctionalInterface
public interface ArticlePublishCheck {
    ArticlePublishCheck NONE = (actor, article, request, emitter) -> PublishProgress.NONE;

    PublishProgress beginPublishCheck(
            AccountAccess actor,
            ArticleGlobalResponse article,
            CreateArticleRequest request,
            AdminSseEmitter emitter)
            throws Exception;

    interface PublishProgress {
        PublishProgress NONE = new PublishProgress() {};

        default void progress(AdminSseEmitter emitter) throws Exception {}

        default void complete(AdminSseEmitter emitter) throws Exception {}
    }
}
