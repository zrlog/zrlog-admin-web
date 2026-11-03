package com.zrlog.admin.business.service;

import com.google.gson.JsonObject;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.knowledge.ContentToolModels.ArticleDetails;
import com.zrlog.admin.business.knowledge.ContentToolModels.SavedArticle;
import com.zrlog.admin.business.knowledge.ContentToolModels.TaxonomyEntry;
import com.zrlog.admin.business.knowledge.ContentToolModels.TaxonomyList;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.security.ArticleAccess;
import com.zrlog.data.repository.TagRepository;
import com.zrlog.data.repository.TypeRepository;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import com.zrlog.model.Log;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Content operations for tools, using the existing publishing, audit and version chain.
 */
@Singleton
public final class ContentToolService {
    private final jakarta.inject.Provider<TagRepository> tagProvider;
    private final jakarta.inject.Provider<TypeRepository> typeProvider;

    private final AccountPermissionService permissionService;

    private final ArticlePublishingService publishing;
    private final Consumer<HttpRequest> refresh;

    @Inject
    public ContentToolService(
            ArticlePublishingService publishing,
            AccountPermissionService permissionService,
            jakarta.inject.Provider<TagRepository> tagProvider,
            jakarta.inject.Provider<TypeRepository> typeProvider) {
        this(
                publishing,
                publishing::updateBlogCacheWithStaticSyncNotice,
                permissionService,
                tagProvider,
                typeProvider);
    }

    public ContentToolService(
            ArticlePublishingService publishing,
            Consumer<HttpRequest> refresh,
            AccountPermissionService permissionService,
            jakarta.inject.Provider<TagRepository> tagProvider,
            jakarta.inject.Provider<TypeRepository> typeProvider) {
        this.tagProvider = tagProvider;
        this.typeProvider = typeProvider;

        this.permissionService = permissionService;

        this.publishing = publishing;
        this.refresh = refresh;
    }

    private static void copy(Log row, CreateArticleRequest body) {
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
    }

    private static String status(boolean draft, boolean privateArticle) {
        return draft ? "draft" : privateArticle ? "private" : "published";
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException(I18nUtil.getAdminBackendStringFromRes(key));
    }

    public ArticleDetails details(AccountAccess account, int id, Set<String> scopes) throws SQLException {
        permissionService.require(account, AccountAction.ARTICLE_READ);
        Log row = permissionService.article(account, id, false);
        if (!ArticleAccess.canRead(account, scopes, Objects.requireNonNullElse(row.getUserId(), 0), Boolean.TRUE.equals(row.getRubbish()), Boolean.TRUE.equals(row.getPrivacy())))
            throw new PermissionErrorException();
        ArticleDetails details = new ArticleDetails();
        details.id = id;
        details.version = Objects.requireNonNullElse(row.getVersion(), 0L).intValue();
        details.typeId = Objects.requireNonNullElse(row.getTypeId(), 0);
        details.title = Objects.toString(row.getTitle(), "");
        details.alias = Objects.toString(row.getAlias(), "");
        details.digest = Objects.toString(row.getDigest(), "");
        details.keywords = Objects.toString(row.getKeywords(), "");
        details.thumbnail = Objects.toString(row.getThumbnail(), "");
        details.editorType = Objects.toString(row.getEditorType(), "");
        details.canComment = Boolean.TRUE.equals(row.getCanComment());
        details.recommended = Boolean.TRUE.equals(row.getRecommended());
        details.status = status(Boolean.TRUE.equals(row.getRubbish()), Boolean.TRUE.equals(row.getPrivacy()));
        return details;
    }

    public TaxonomyList taxonomy(AccountAccess account, boolean categories, int offset, int limit) throws SQLException {
        permissionService.require(account, AccountAction.TAXONOMY_READ);
        List<TaxonomyEntry> rows = categories
                ? typeProvider.get().queryList("select typeId as id,typeName as name,alias,remark as description from type order by typeId limit ? offset ?", TaxonomyEntry.class, limit + 1, offset)
                : tagProvider.get().queryList("select tagId as id,text as name from tag order by tagId limit ? offset ?", TaxonomyEntry.class, limit + 1, offset);
        TaxonomyList result = new TaxonomyList();
        if (rows.size() > limit) result.nextOffset = offset + limit;
        result.items.addAll(rows.subList(0, Math.min(rows.size(), limit)));
        return result;
    }

    public SavedArticle save(AccountAccess account, String operation,
                             JsonObject args, Set<String> scopes, HttpRequest request, AdminAuditContext audit) throws SQLException {
        return saveWithRefresh(account, operation, args, scopes, audit, () -> refresh.accept(request));
    }

    /** Non-HTTP callers supply their own bounded refresh operation. */
    public SavedArticle saveWithRefresh(AccountAccess account, String operation, JsonObject args, Set<String> scopes,
                                        AdminAuditContext audit, Runnable refreshOperation) throws SQLException {
        boolean create = operation.equals("create_article");
        permissionService.require(account, create ? AccountAction.ARTICLE_CREATE : AccountAction.ARTICLE_UPDATE);
        if (ZrLogUtil.isPreviewMode()) throw new PermissionErrorException();
        Log old = null;
        CreateArticleRequest body;
        int version = 0;
        if (create) {
            body = new CreateArticleRequest();
            body.setRubbish(true);
            body.setCanComment(true);
            body.setEditorType("markdown");
        } else {
            int id = args.get("id").getAsInt();
            old = permissionService.article(account, id, false);
            version = args.get("version").getAsInt();
            if (version != Objects.requireNonNullElse(old.getVersion(), 0L).intValue()) throw new UpdateArticleExpireException();
            UpdateArticleRequest update = new UpdateArticleRequest();
            update.setLogId(id);
            update.setVersion(version);
            copy(old, update);
            body = update;
        }
        patch(args, body);
        if (operation.equals("publish_article")) {
            body.setRubbish(false);
            body.setPrivacy(false);
        }
        int author = old == null ? account.getUserId() : Objects.requireNonNullElse(old.getUserId(), 0);
        if (!ArticleAccess.canWrite(account, scopes, author, old == null || Boolean.TRUE.equals(old.getRubbish()),
                old != null && Boolean.TRUE.equals(old.getPrivacy()), body.isRubbish(), body.isPrivacy()))
            throw new PermissionErrorException();
        body.setPreserveDraftAiMessages(true);
        body.doValid();
        body.doClean();
        if (this.typeProvider.get().loadById((long) (body.getTypeId())) == null)
            throw invalid("admin.mcp.validation.category");
        publishing.prepareRequest(body);
        if (body.getMarkdown() != null && !body.getMarkdown().isBlank() && (body.getContent() == null || body.getContent().isBlank())) {
            throw invalid("admin.mcp.validation.htmlRequired");
        }
        CreateOrUpdateArticleResponse saved = create
                ? publishing.create(account, body, audit)
                : publishing.update(account, (UpdateArticleRequest) body, audit);
        SavedArticle result = new SavedArticle();
        result.id = saved.getLogId();
        result.version = create ? 0 : version + 1;
        result.status = status(Boolean.TRUE.equals(saved.getRubbish()), Boolean.TRUE.equals(saved.getPrivacy()));
        if (saved.isPublicCacheRefreshRequired()) {
            try {
                refreshOperation.run();
                result.refreshStatus = "updated";
            } catch (RuntimeException e) {
                result.refreshStatus = "failed";
                result.warning = I18nUtil.getAdminBackendStringFromRes("admin.mcp.warning.refresh");
            }
        }
        return result;
    }

    private void patch(JsonObject args, CreateArticleRequest body) {
        if (args.has("title")) body.setTitle(args.get("title").getAsString());
        if (args.has("typeId")) body.setTypeId(args.get("typeId").getAsLong());
        if (args.has("alias")) body.setAlias(args.get("alias").getAsString());
        if (args.has("digest")) body.setDigest(args.get("digest").getAsString());
        if (args.has("keywords")) body.setKeywords(args.get("keywords").getAsString());
        if (args.has("thumbnail")) body.setThumbnail(args.get("thumbnail").getAsString());
        if (args.has("canComment")) body.setCanComment(args.get("canComment").getAsBoolean());
        if (args.has("recommended")) body.setRecommended(args.get("recommended").getAsBoolean());
        if (args.has("editorType")) body.setEditorType(args.get("editorType").getAsString());
        if (args.has("markdown")) {
            body.setMarkdown(args.get("markdown").getAsString());
            if (!args.has("editorType")) body.setEditorType("markdown");
            if (!args.has("content")) body.setContent("");
        }
        if (args.has("content")) {
            body.setContent(args.get("content").getAsString());
            if (!args.has("markdown")) {
                body.setMarkdown("");
                body.setEditorType("html");
            }
        }
        if (args.has("status")) {
            String status = args.get("status").getAsString();
            body.setRubbish(status.equals("draft"));
            body.setPrivacy(status.equals("private"));
        }
    }
}
