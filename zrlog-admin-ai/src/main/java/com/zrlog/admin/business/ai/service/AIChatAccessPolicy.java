package com.zrlog.admin.business.ai.service;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.ai.model.AIChatModels.*;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.knowledge.KnowledgeModels.Options;
import com.zrlog.admin.business.rest.base.UserPreferences;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.business.service.UserPreferenceService;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.security.ArticleAccess;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.*;

/** Authorization and approval previews shared by request entry points and tool execution. */
public class AIChatAccessPolicy {
    private final AccountIdentityService identities;
    private final AccountPermissionService permissionService;
    private final UserPreferenceService userPreferenceService;

    @Inject
    public AIChatAccessPolicy(AccountIdentityService identities, AccountPermissionService permissionService,
                              UserPreferenceService userPreferenceService) {
        this.identities = identities;
        this.permissionService = permissionService;
        this.userPreferenceService = userPreferenceService;
    }

    static Set<String> scopes(UserPreferences.Assistant settings) {
        Options options = new Options();
        options.allArticles = Set.of("accessible_public", "accessible_all").contains(settings.knowledgeScope);
        options.drafts = Set.of("own_all", "accessible_all").contains(settings.knowledgeScope);
        options.privateArticles = options.drafts;
        Set<String> scopes = options.scopes();
        // Account actions remain authoritative; preferences constrain the article data scope.
        scopes.add("articles:write");
        scopes.add("articles:publish");
        return scopes;
    }

    void authorizeArticle(AccountAccess actor, long articleId) {
        AccountAccess account = identities.revalidate(actor);
        if (!AccountAction.ARTICLE_ASSIST.allowed(account)) throw new PermissionErrorException();
        if (articleId > 0) {
            try {
                permissionService.article(account, articleId, false);
            } catch (SQLException e) {
                throw new PermissionErrorException();
            }
        }
    }

    Approval prepareApproval(AccountAccess actor, String tool, JsonObject args) throws SQLException {
        Approval approval = new Approval();
        approval.id = UUID.randomUUID().toString();
        approval.tool = tool;
        approval.expiresAt = System.currentTimeMillis() + AIApprovalStore.APPROVAL_TTL;
        Map<String, Object> old =
                approvalArticle(
                        actor, tool,
                        args,
                        scopes(
                                this.userPreferenceService.assistant(identities.revalidate(actor))));
        if (args.has("id")) {
            approval.articleId = args.get("id").getAsLong();
            approval.version = args.get("version").getAsInt();
            if (approval.version != ((Number) old.get("version")).intValue())
                throw new UpdateArticleExpireException();
            approval.title = Objects.toString(old.get("title"), "");
            approval.publicImpact =
                    !AccountAccess.truth(old.get("rubbish"))
                            && !AccountAccess.truth(old.get("privacy"));
        } else if (args.has("title")) approval.title = args.get("title").getAsString();
        approval.publicImpact |= tool.equals("publish_article") || args.has("status") && args.get("status").getAsString().equals("published");
        for (var entry : args.entrySet()) {
            if (Set.of("id", "version", "data").contains(entry.getKey())) continue;
            ApprovalChange change = new ApprovalChange();
            change.field = entry.getKey();
            String before = Objects.toString(old.get(entry.getKey().equals("editorType") ? "editor_type" : entry.getKey()), "");
            String after = entry.getValue().getAsString();
            if (entry.getKey().equals("status") && !old.isEmpty())
                before =
                        AccountAccess.truth(old.get("rubbish"))
                                ? "draft"
                                : AccountAccess.truth(old.get("privacy")) ? "private" : "published";
            change.truncated = before.length() > 4000 || after.length() > 4000;
            change.before = before.substring(0, Math.min(4000, before.length()));
            change.after = after.substring(0, Math.min(4000, after.length()));
            approval.changes.add(change);
        }
        if (tool.equals("upload_attachment")) {
            ApprovalChange size = new ApprovalChange();
            size.field = "size";
            size.before = "";
            size.after = Integer.toString(Base64.getDecoder().decode(args.get("data").getAsString()).length);
            approval.changes.add(size);
        }
        return approval;
    }

    Map<String, Object> approvalArticle(AccountAccess actor, String tool, JsonObject args, Set<String> scopes) throws SQLException {
        AccountAccess account = identities.revalidate(actor);
        if (tool.equals("upload_attachment")) {
            permissionService.require(account, AccountAction.ASSET_UPLOAD);
            return Map.of();
        }
        Map<String, Object> old =
                args.has("id")
                        ? permissionService.article(account, args.get("id").getAsLong(), false)
                        : Map.of();
        boolean draft = old.isEmpty() || AccountAccess.truth(old.get("rubbish"));
        boolean privacy = AccountAccess.truth(old.get("privacy"));
        String status = tool.equals("publish_article") ? "published" : args.has("status") ? args.get("status").getAsString() : null;
        int author = old.isEmpty() ? account.getUserId() : ((Number) old.get("userId")).intValue();
        if (!ArticleAccess.canWrite(account, scopes, author, draft, privacy,
                status == null ? draft : status.equals("draft"), status == null ? privacy : status.equals("private")))
            throw new PermissionErrorException();
        return old;
    }

    CurrentArticleContext currentArticleContext(AccountAccess actor, long articleId) throws SQLException {
        CurrentArticleContext context = new CurrentArticleContext();
        context.articleId = articleId;
        if (articleId == 0) {
            context.status = "unsaved";
            return context;
        }
        Map<String, Object> row =
                permissionService.article(actor, articleId, false);
        context.title = Objects.toString(row.get("title"), "");
        context.version = ((Number) Objects.requireNonNullElse(row.get("version"), 0)).intValue();
        context.typeId = ((Number) Objects.requireNonNullElse(row.get("typeId"), 0)).longValue();
        context.status =
                AccountAccess.truth(row.get("rubbish"))
                        ? "draft"
                        : AccountAccess.truth(row.get("privacy")) ? "private" : "published";
        return context;
    }

}
