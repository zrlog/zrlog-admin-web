package com.zrlog.admin.business.service;

import com.google.gson.JsonObject;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.knowledge.ContentToolModels.ArticleDetails;
import com.zrlog.admin.business.knowledge.ContentToolModels.SavedArticle;
import com.zrlog.admin.business.knowledge.ContentToolModels.TaxonomyEntry;
import com.zrlog.admin.business.knowledge.ContentToolModels.TaxonomyList;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.data.repository.TagRepository;
import com.zrlog.data.repository.TypeRepository;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.Log;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Content operations for tools, using the existing publishing, audit and version chain. */
@Singleton
public final class ContentToolService {
    private final jakarta.inject.Provider<TagRepository> tagProvider;
    private final jakarta.inject.Provider<TypeRepository> typeProvider;

    private final AccountPermissionService permissionService;

    private final ArticlePublication publication;
    private final Consumer<HttpRequest> refresh;

    @Inject
    public ContentToolService(
            ArticlePublication publication,
            ArticlePublishingService publishing,
            AccountPermissionService permissionService,
            jakarta.inject.Provider<TagRepository> tagProvider,
            jakarta.inject.Provider<TypeRepository> typeProvider) {
        this(
                publication,
                publishing::updateBlogCacheWithStaticSyncNotice,
                permissionService,
                tagProvider,
                typeProvider);
    }

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
        this(
                new ArticlePublication(publishing, permissionService, typeProvider),
                refresh,
                permissionService,
                tagProvider,
                typeProvider);
    }

    private ContentToolService(
            ArticlePublication publication,
            Consumer<HttpRequest> refresh,
            AccountPermissionService permissionService,
            jakarta.inject.Provider<TagRepository> tagProvider,
            jakarta.inject.Provider<TypeRepository> typeProvider) {
        this.tagProvider = tagProvider;
        this.typeProvider = typeProvider;

        this.permissionService = permissionService;

        this.publication = publication;
        this.refresh = refresh;
    }

    private static String status(boolean draft, boolean privateArticle) {
        return draft ? "draft" : privateArticle ? "private" : "published";
    }

    public ArticleDetails details(AccountAccess account, int id, Set<String> scopes)
            throws SQLException {
        Log row = publication.readArticle(account, id, scopes);
        ArticleDetails details = new ArticleDetails();
        details.id = id;
        details.version = ArticlePublication.version(row);
        details.typeId = Objects.requireNonNullElse(row.getTypeId(), 0);
        details.title = Objects.toString(row.getTitle(), "");
        details.alias = Objects.toString(row.getAlias(), "");
        details.digest = Objects.toString(row.getDigest(), "");
        details.keywords = Objects.toString(row.getKeywords(), "");
        details.thumbnail = Objects.toString(row.getThumbnail(), "");
        details.editorType = Objects.toString(row.getEditorType(), "");
        details.canComment = Boolean.TRUE.equals(row.getCanComment());
        details.recommended = Boolean.TRUE.equals(row.getRecommended());
        details.status =
                status(
                        Boolean.TRUE.equals(row.getRubbish()),
                        Boolean.TRUE.equals(row.getPrivacy()));
        return details;
    }

    public TaxonomyList taxonomy(AccountAccess account, boolean categories, int offset, int limit)
            throws SQLException {
        permissionService.require(account, AccountAction.TAXONOMY_READ);
        List<TaxonomyEntry> rows =
                categories
                        ? typeProvider
                                .get()
                                .queryList(
                                        "select typeId as id,typeName as name,alias,remark as description from type order by typeId limit ? offset ?",
                                        TaxonomyEntry.class,
                                        limit + 1,
                                        offset)
                        : tagProvider
                                .get()
                                .queryList(
                                        "select tagId as id,text as name from tag order by tagId limit ? offset ?",
                                        TaxonomyEntry.class,
                                        limit + 1,
                                        offset);
        TaxonomyList result = new TaxonomyList();
        if (rows.size() > limit) result.nextOffset = offset + limit;
        result.items.addAll(rows.subList(0, Math.min(rows.size(), limit)));
        return result;
    }

    public SavedArticle save(
            AccountAccess account,
            String operation,
            JsonObject args,
            Set<String> scopes,
            HttpRequest request,
            AdminAuditContext audit)
            throws SQLException {
        return save(account, operation, args, scopes, request, audit, null, null);
    }

    public SavedArticle save(
            AccountAccess account,
            String operation,
            JsonObject args,
            Set<String> scopes,
            HttpRequest request,
            AdminAuditContext audit,
            String conversationId,
            String runId)
            throws SQLException {
        boolean create = operation.equals("create_article");
        publication.requireWriteAccess(account, create);
        Log old = null;
        CreateArticleRequest body;
        int version = 0;
        if (create) {
            body = new CreateArticleRequest();
            body.setRubbish(true);
            body.setCanComment(true);
            body.setEditorType("markdown");
            body.setConversationId(conversationId);
        } else {
            int id = args.get("id").getAsInt();
            version = args.get("version").getAsInt();
            old = publication.updateTarget(account, id, version);
            body = ArticlePublication.updateRequest(old, id, version);
        }
        patch(args, body);
        if (operation.equals("publish_article")) {
            body.setRubbish(false);
            body.setPrivacy(false);
        }
        var publicationResult =
                publication.save(
                        account, old, body, scopes, audit, () -> refresh.accept(request), runId);
        CreateOrUpdateArticleResponse saved = publicationResult.article;
        SavedArticle result = new SavedArticle();
        result.id = saved.getLogId();
        result.version = create ? 0 : version + 1;
        result.status =
                status(
                        Boolean.TRUE.equals(saved.getRubbish()),
                        Boolean.TRUE.equals(saved.getPrivacy()));
        if (publicationResult.refresh == ArticlePublication.RefreshStatus.UPDATED) {
            result.refreshStatus = "updated";
        } else if (publicationResult.refresh == ArticlePublication.RefreshStatus.FAILED) {
            result.refreshStatus = "failed";
            result.warning = I18nUtil.getAdminBackendStringFromRes("admin.mcp.warning.refresh");
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
