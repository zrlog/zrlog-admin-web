package com.zrlog.admin.business.service;

import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.data.repository.TypeRepository;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.security.ArticleAccess;
import com.zrlog.model.Log;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;

/** Article writes shared by scheduled publication and content tools. */
@Singleton
public final class ArticlePublication {
    private static final Set<String> SCHEDULED_SCOPES =
            Set.of(
                    "articles:read",
                    "articles:read_drafts",
                    "articles:read_private",
                    "articles:all",
                    "articles:write",
                    "articles:publish");
    private final ArticlePublishingService publishing;
    private final AccountPermissionService permissions;
    private final Provider<TypeRepository> types;

    @Inject
    public ArticlePublication(
            ArticlePublishingService publishing,
            AccountPermissionService permissions,
            Provider<TypeRepository> types) {
        this.publishing = publishing;
        this.permissions = permissions;
        this.types = types;
    }

    Log readArticle(AccountAccess account, int id, Set<String> scopes) throws SQLException {
        permissions.require(account, AccountAction.ARTICLE_READ);
        Log row = permissions.article(account, id, false);
        if (!ArticleAccess.canRead(
                account,
                scopes,
                Objects.requireNonNullElse(row.getUserId(), 0),
                Boolean.TRUE.equals(row.getRubbish()),
                Boolean.TRUE.equals(row.getPrivacy()))) throw new PermissionErrorException();
        return row;
    }

    String confirmSchedule(AccountAccess actor, int id, int expectedVersion) throws SQLException {
        Log article = readArticle(actor, id, SCHEDULED_SCOPES);
        if (version(article) != expectedVersion)
            throw new IllegalArgumentException("automation.articleChanged");
        if (!Boolean.TRUE.equals(article.getRubbish()))
            throw new IllegalArgumentException("automation.articleNotDraft");
        return Objects.toString(article.getTitle(), "");
    }

    ScheduledResult publishScheduledDraft(
            AccountAccess actor,
            int id,
            int expectedVersion,
            AdminAuditContext audit,
            BeforeWrite beforeWrite,
            Runnable refresh)
            throws Exception {
        Log candidate = readArticle(actor, id, SCHEDULED_SCOPES);
        requireDraftVersion(candidate, expectedVersion);
        beforeWrite.check();
        requireWriteAccess(actor, false);
        Log current = updateTarget(actor, id, expectedVersion);
        requireDraftVersion(current, expectedVersion);
        UpdateArticleRequest body = updateRequest(current, id, expectedVersion);
        body.setRubbish(false);
        body.setPrivacy(false);
        SaveResult result = save(actor, current, body, SCHEDULED_SCOPES, audit, refresh, null);
        return new ScheduledResult(Objects.toString(candidate.getTitle(), ""), result.refresh);
    }

    private static void requireDraftVersion(Log row, int expectedVersion) {
        if (version(row) != expectedVersion || !Boolean.TRUE.equals(row.getRubbish()))
            throw new UpdateArticleExpireException();
    }

    void requireWriteAccess(AccountAccess account, boolean create) {
        permissions.require(
                account, create ? AccountAction.ARTICLE_CREATE : AccountAction.ARTICLE_UPDATE);
        if (ZrLogUtil.isPreviewMode()) throw new PermissionErrorException();
    }

    Log updateTarget(AccountAccess actor, int id, int expectedVersion) throws SQLException {
        Log row = permissions.article(actor, id, false);
        if (version(row) != expectedVersion) throw new UpdateArticleExpireException();
        return row;
    }

    static int version(Log row) {
        return Objects.requireNonNullElse(row.getVersion(), 0L).intValue();
    }

    static UpdateArticleRequest updateRequest(Log row, int id, int version) {
        UpdateArticleRequest body = new UpdateArticleRequest();
        body.setLogId(id);
        body.setVersion(version);
        body.setTitle(Objects.toString(row.getTitle(), ""));
        body.setTypeId((long) Objects.requireNonNullElse(row.getTypeId(), 0));
        body.setAlias(Objects.toString(row.getAlias(), ""));
        body.setMarkdown(Objects.toString(row.getMarkdown(), ""));
        body.setContent(Objects.toString(row.getContent(), ""));
        body.setDigest(Objects.toString(row.getDigest(), ""));
        body.setKeywords(Objects.toString(row.getKeywords(), ""));
        body.setThumbnail(Objects.toString(row.getThumbnail(), ""));
        body.setEditorType(Objects.toString(row.getEditorType(), ""));
        body.setCanComment(Boolean.TRUE.equals(row.getCanComment()));
        body.setRecommended(Boolean.TRUE.equals(row.getRecommended()));
        body.setPrivacy(Boolean.TRUE.equals(row.getPrivacy()));
        body.setRubbish(Boolean.TRUE.equals(row.getRubbish()));
        return body;
    }

    SaveResult save(
            AccountAccess actor,
            Log old,
            CreateArticleRequest body,
            Set<String> scopes,
            AdminAuditContext audit,
            Runnable refresh,
            String runId)
            throws SQLException {
        int author =
                old == null ? actor.getUserId() : Objects.requireNonNullElse(old.getUserId(), 0);
        if (!ArticleAccess.canWrite(
                actor,
                scopes,
                author,
                old == null || Boolean.TRUE.equals(old.getRubbish()),
                old != null && Boolean.TRUE.equals(old.getPrivacy()),
                body.isRubbish(),
                body.isPrivacy())) throw new PermissionErrorException();
        body.doValid();
        body.doClean();
        requireCategory(body.getTypeId());
        publishing.prepareRequest(body);
        if (body.getMarkdown() != null
                && !body.getMarkdown().isBlank()
                && (body.getContent() == null || body.getContent().isBlank())) {
            throw new ValidationException("admin.mcp.validation.htmlRequired");
        }
        CreateOrUpdateArticleResponse saved =
                old == null
                        ? publishing.create(actor, body, audit, runId)
                        : publishing.update(actor, (UpdateArticleRequest) body, audit);
        if (!saved.isPublicCacheRefreshRequired())
            return new SaveResult(saved, RefreshStatus.SKIPPED);
        try {
            refresh.run();
            return new SaveResult(saved, RefreshStatus.UPDATED);
        } catch (RuntimeException failure) {
            return new SaveResult(saved, RefreshStatus.FAILED);
        }
    }

    private void requireCategory(long typeId) throws CategoryLookupException {
        try {
            if (types.get().queryFirstObj("select 1 from type where typeId=?", typeId) == null)
                throw new ValidationException("admin.mcp.validation.category");
        } catch (SQLException cause) {
            throw new CategoryLookupException(cause);
        }
    }

    public static final class CategoryLookupException extends SQLException {
        private CategoryLookupException(SQLException cause) {
            super("Could not verify the article category", cause);
        }
    }

    /** The article was rejected before the publishing service attempted a write. */
    public static final class ValidationException extends IllegalArgumentException {
        private ValidationException(String key) {
            super(I18nUtil.getAdminBackendStringFromRes(key));
        }
    }

    @FunctionalInterface
    interface BeforeWrite {
        void check() throws Exception;
    }

    enum RefreshStatus {
        SKIPPED,
        UPDATED,
        FAILED
    }

    static final class SaveResult {
        final CreateOrUpdateArticleResponse article;
        final RefreshStatus refresh;

        SaveResult(CreateOrUpdateArticleResponse article, RefreshStatus refresh) {
            this.article = article;
            this.refresh = refresh;
        }
    }

    static final class ScheduledResult {
        final String title;
        final RefreshStatus refresh;

        ScheduledResult(String title, RefreshStatus refresh) {
            this.title = title;
            this.refresh = refresh;
        }
    }
}
