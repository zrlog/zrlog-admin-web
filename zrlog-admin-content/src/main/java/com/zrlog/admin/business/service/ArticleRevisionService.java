package com.zrlog.admin.business.service;

import com.hibegin.common.util.BeanUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.request.ArticleVersionRollbackRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.AdminTokenVO;

import jakarta.inject.Inject;

import java.sql.SQLException;

public class ArticleRevisionService {
    private final ArticleQueryService queries;
    private final ArticleVersionService versions;
    private final AdminArticleService articles;

    @Inject
    public ArticleRevisionService(
            ArticleQueryService queries,
            ArticleVersionService versions,
            AdminArticleService articles) {
        this.queries = queries;
        this.versions = versions;
        this.articles = articles;
    }

    public ArticleVersionCompareResponse compare(
            Integer id, Integer fromVersion, Integer toVersion, HttpRequest request)
            throws SQLException {
        LoadEditArticleResponse current = queries.loadDetail(id.toString(), request);
        return versions.compare(
                fromVersion,
                toVersion,
                versions.loadVersion(id, fromVersion, current),
                versions.loadVersion(id, toVersion, current));
    }

    public CreateOrUpdateArticleResponse rollback(
            AdminTokenVO user, ArticleVersionRollbackRequest body, HttpRequest request)
            throws SQLException {
        if (body.getLogId() == null
                || body.getVersion() == null
                || body.getTargetVersion() == null) {
            throw new ArgsException("logId/version/targetVersion");
        }
        LoadEditArticleResponse current = queries.loadDetail(body.getLogId().toString(), request);
        LoadEditArticleResponse target =
                versions.loadVersion(body.getLogId(), body.getTargetVersion(), current);
        UpdateArticleRequest update = BeanUtil.convert(target, UpdateArticleRequest.class);
        update.setLogId(body.getLogId());
        update.setVersion(body.getVersion());
        return articles.update(user, update);
    }
}
