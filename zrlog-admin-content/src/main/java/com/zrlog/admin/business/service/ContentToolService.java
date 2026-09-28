package com.zrlog.admin.business.service;

import com.google.gson.JsonObject;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.knowledge.ContentToolModels.*;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.security.ArticleAccess;
import com.zrlog.model.Type;
import com.zrlog.model.Tag;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

/** Content operations for tools, using the existing publishing, audit and version chain. */
public final class ContentToolService {
    private final ArticlePublishingService publishing = new ArticlePublishingService();
    private final Consumer<HttpRequest> refresh;

    public ContentToolService() { this(request -> new ArticlePublishingService().updateBlogCacheWithStaticSyncNotice(request)); }
    public ContentToolService(Consumer<HttpRequest> refresh) { this.refresh = refresh; }

    public ArticleDetails details(int id, Set<String> scopes) throws SQLException {
        AccountPermissionService.require(AccountAction.ARTICLE_READ);
        AccountAccess account = AccountPermissionService.current();
        Map<String,Object> row = AccountPermissionService.article(account, id, false);
        if (!ArticleAccess.canRead(account, scopes, number(row, "userId"), bool(row, "rubbish"), bool(row, "privacy"))) throw new PermissionErrorException();
        ArticleDetails details = new ArticleDetails();
        details.id = id; details.version = number(row, "version"); details.typeId = number(row, "typeId");
        details.title = text(row, "title"); details.alias = text(row, "alias"); details.digest = text(row, "digest");
        details.keywords = text(row, "keywords"); details.thumbnail = text(row, "thumbnail"); details.editorType = text(row, "editor_type");
        details.canComment = bool(row, "canComment"); details.recommended = bool(row, "recommended");
        details.status = status(bool(row, "rubbish"), bool(row, "privacy"));
        return details;
    }

    public TaxonomyList taxonomy(boolean categories, int offset, int limit) throws SQLException {
        AccountPermissionService.require(AccountAction.TAXONOMY_READ);
        List<Map<String,Object>> rows = categories
                ? new Type().queryListWithParams("select typeId,typeName,alias,remark from type order by typeId limit ? offset ?", limit + 1, offset)
                : new Tag().queryListWithParams("select tagId,text from tag order by tagId limit ? offset ?", limit + 1, offset);
        TaxonomyList result = new TaxonomyList();
        if (rows.size() > limit) result.nextOffset = offset + limit;
        for (Map<String,Object> row : rows.subList(0, Math.min(rows.size(), limit))) {
            TaxonomyEntry item = new TaxonomyEntry();
            item.id = number(row, categories ? "typeId" : "tagId"); item.name = text(row, categories ? "typeName" : "text");
            if (categories) { item.alias = text(row, "alias"); item.description = text(row, "remark"); }
            result.items.add(item);
        }
        return result;
    }

    public SavedArticle save(String operation, JsonObject args, Set<String> scopes, HttpRequest request) throws SQLException {
        boolean create = operation.equals("create_article");
        AccountPermissionService.require(create ? AccountAction.ARTICLE_CREATE : AccountAction.ARTICLE_UPDATE);
        if (ZrLogUtil.isPreviewMode()) throw new PermissionErrorException();
        AccountAccess account = AccountPermissionService.current();
        Map<String,Object> old = null;
        CreateArticleRequest body;
        int version = 0;
        if (create) {
            body = new CreateArticleRequest(); body.setRubbish(true); body.setCanComment(true); body.setEditorType("markdown");
        } else {
            int id = args.get("id").getAsInt();
            old = AccountPermissionService.article(account, id, false);
            version = args.get("version").getAsInt();
            if (version != number(old, "version")) throw new UpdateArticleExpireException();
            UpdateArticleRequest update = new UpdateArticleRequest();
            update.setLogId(id); update.setVersion(version); copy(old, update); body = update;
        }
        patch(args, body);
        if (operation.equals("publish_article")) { body.setRubbish(false); body.setPrivacy(false); }
        int author = old == null ? account.getUserId() : number(old, "userId");
        if (!ArticleAccess.canWrite(account, scopes, author, old == null || bool(old, "rubbish"),
                old != null && bool(old, "privacy"), body.isRubbish(), body.isPrivacy())) throw new PermissionErrorException();
        body.setPreserveDraftAiMessages(true);
        body.doValid(); body.doClean();
        if (new Type().loadById(body.getTypeId()) == null) throw invalid("admin.mcp.validation.category");
        publishing.prepareRequest(body);
        if (body.getMarkdown() != null && !body.getMarkdown().isBlank() && (body.getContent() == null || body.getContent().isBlank())) {
            throw invalid("admin.mcp.validation.htmlRequired");
        }
        CreateOrUpdateArticleResponse saved = create
                ? publishing.create(AdminTokenThreadLocal.getUser(), body, request)
                : publishing.update(AdminTokenThreadLocal.getUser(), (UpdateArticleRequest) body, request);
        SavedArticle result = new SavedArticle(); result.id = saved.getLogId(); result.version = create ? 0 : version + 1;
        result.status = status(Boolean.TRUE.equals(saved.getRubbish()), Boolean.TRUE.equals(saved.getPrivacy()));
        if (saved.isPublicCacheRefreshRequired()) {
            try { refresh.accept(request); result.refreshStatus = "updated"; }
            catch (RuntimeException e) {
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
        if (args.has("content")) { body.setContent(args.get("content").getAsString()); if (!args.has("markdown")) { body.setMarkdown(""); body.setEditorType("html"); } }
        if (args.has("status")) {
            String status = args.get("status").getAsString(); body.setRubbish(status.equals("draft")); body.setPrivacy(status.equals("private"));
        }
    }
    private static void copy(Map<String,Object> row, CreateArticleRequest body) {
        body.setTitle(text(row,"title")); body.setTypeId((long) number(row,"typeId")); body.setAlias(text(row,"alias"));
        body.setMarkdown(text(row,"markdown")); body.setContent(text(row,"content")); body.setDigest(text(row,"digest"));
        body.setKeywords(text(row,"keywords")); body.setThumbnail(text(row,"thumbnail")); body.setEditorType(text(row,"editor_type"));
        body.setCanComment(bool(row,"canComment")); body.setRecommended(bool(row,"recommended"));
        body.setPrivacy(bool(row,"privacy")); body.setRubbish(bool(row,"rubbish"));
    }
    private static int number(Map<String,Object> row, String key) { return row.get(key) instanceof Number ? ((Number)row.get(key)).intValue() : 0; }
    private static String text(Map<String,Object> row, String key) { return Objects.toString(row.get(key), ""); }
    private static boolean bool(Map<String,Object> row, String key) { return AccountAccess.truth(row.get(key)); }
    private static String status(boolean draft, boolean privateArticle) { return draft ? "draft" : privateArticle ? "private" : "published"; }
    private static IllegalArgumentException invalid(String key) { return new IllegalArgumentException(I18nUtil.getAdminBackendStringFromRes(key)); }
}
