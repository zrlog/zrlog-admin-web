package com.zrlog.admin.business.service;

import com.zrlog.admin.business.content.ArticleResourceReferences;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.repository.LogRepository;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import com.zrlog.data.dto.ArticleBasicDTO;
import java.util.List;

/**
 * Content owns all article mutations, including those initiated by file operations.
 */
@Singleton
public class ArticleResourceService {
    private final AdminArticleService articles;
    private final AccountIdentityService identities;
    private final Provider<LogRepository> logs;

    @Inject
    public ArticleResourceService(
            AdminArticleService articles,
            AccountIdentityService identities,
            Provider<LogRepository> logs) {
        this.articles = articles;
        this.identities = identities;
        this.logs = logs;
    }

    public ReplaceArticleResourceUrlResponse replaceReferences(
            AccountAccess actor,
            String from,
            String to,
            boolean prefix,
            ArticleResourceReferences references)
            throws SQLException {
        actor = identities.revalidate(actor);
        if (!AccountAction.FILE_MANAGE.allowed(actor))
            throw new PermissionErrorException();
        List<ArticleBasicDTO> candidates =
                logs.get()
                        .queryList(
                                "select logId, thumbnail, content, markdown, digest from "
                                        + LogRepository.TABLE_NAME, ArticleBasicDTO.class);
        ReplaceArticleResourceUrlResponse result = new ReplaceArticleResourceUrlResponse();
        result.setScannedArticles(candidates.size());
        for (ArticleBasicDTO candidate : candidates) {
            if (references.replace(candidate, from, to, prefix).isEmpty()) continue;
            int fields =
                    articles.replaceResourceReferences(
                            actor,
                            candidate.getLogId().intValue(),
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
