package com.zrlog.admin.business.content;

import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;

/** Optional data added to the article editor response. */
@FunctionalInterface
public interface ArticleEditorDecorator {
    ArticleEditorDecorator NONE = (article, articleId, userId) -> article;

    ArticleGlobalResponse enrichEditor(ArticleGlobalResponse article, long articleId, int userId);
}
