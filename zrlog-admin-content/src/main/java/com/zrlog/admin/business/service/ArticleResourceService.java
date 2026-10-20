package com.zrlog.admin.business.service;

import com.zrlog.admin.business.content.ArticleResourceReferences;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.Log;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Content owns all article mutations, including those initiated by file operations. */
@Singleton
public class ArticleResourceService {
    private final AdminArticleService articles;
    private final AccountPermissionService permissions;
    private final Provider<Log> logs;

    @Inject
    public ArticleResourceService(
            AdminArticleService articles,
            AccountPermissionService permissions,
            Provider<Log> logs) {
        this.articles = articles;
        this.permissions = permissions;
        this.logs = logs;
    }

    public ReplaceArticleResourceUrlResponse replaceReferences(
            AdminTokenVO user,
            String from,
            String to,
            boolean prefix,
            ArticleResourceReferences references)
            throws SQLException {
        if (!AccountAction.FILE_MANAGE.allowed(permissions.account(user)))
            throw new PermissionErrorException();
        List<Map<String, Object>> candidates =
                logs.get()
                        .queryListWithParams(
                                "select logId, thumbnail, content, markdown, digest from "
                                        + Log.TABLE_NAME);
        ReplaceArticleResourceUrlResponse result = new ReplaceArticleResourceUrlResponse();
        result.setScannedArticles(candidates.size());
        for (Map<String, Object> candidate : candidates) {
            if (references.replace(candidate, from, to, prefix).isEmpty()) continue;
            int fields =
                    articles.replaceResourceReferences(
                            user,
                            ((Number) candidate.get("logId")).intValue(),
                            from,
                            to,
                            prefix,
                            references);
            if (fields > 0) {
                result.setUpdatedArticles(result.getUpdatedArticles() + 1);
                result.setUpdatedFields(result.getUpdatedFields() + fields);
            }
        }
        return result;
    }
}
