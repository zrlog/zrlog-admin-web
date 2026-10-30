package com.zrlog.admin.business.service;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.automation.AutomationModels;
import com.zrlog.admin.business.automation.ScheduledTaskProvider;
import com.zrlog.admin.business.automation.TaskExecutionContext;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.knowledge.ContentToolModels;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;

import java.util.List;
import java.util.Set;

/** Scheduled publication uses the same version, ownership and audit chain as interactive publication. */
public final class ScheduledArticleTaskProvider implements ScheduledTaskProvider {
    private static final Set<String> SCOPES = Set.of("articles:read", "articles:read_drafts", "articles:read_private",
            "articles:all", "articles:write", "articles:publish");
    private final ContentToolService content;
    private final AccountPermissionService permissions;
    private final Refresh refresh;

    public ScheduledArticleTaskProvider(ContentToolService content, AccountPermissionService permissions, Refresh refresh) {
        this.content = content;
        this.permissions = permissions;
        this.refresh = refresh;
    }

    @Override
    public List<AutomationModels.Definition> definitions() {
        var definition = new AutomationModels.Definition();
        definition.type = "article.publish";
        definition.titleKey = "admin.automation.publish.title";
        definition.descriptionKey = "admin.automation.publish.description";
        definition.category = "content";
        definition.timeoutSeconds = 50;
        definition.requiredActions = List.of(AccountAction.ARTICLE_READ.getId(), AccountAction.ARTICLE_UPDATE.getId(), AccountAction.ARTICLE_PUBLISH.getId());
        var article = new AutomationModels.Field();
        article.name = "articleId";
        article.type = "article";
        article.labelKey = "admin.automation.publish.article";
        article.required = true;
        article.minimum = 1L;
        definition.fields = List.of(article);
        definition.defaultSchedule = new AutomationModels.Schedule();
        return List.of(definition);
    }

    @Override
    public String instanceKey(String type, JsonObject parameters) { return "article:" + integer(parameters, "articleId", 1); }

    @Override
    public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) throws Exception {
        permissions.require(actor, AccountAction.ARTICLE_PUBLISH);
        int id = integer(parameters, "articleId", 1);
        int version = integer(parameters, "version", 0);
        ContentToolModels.ArticleDetails current = content.details(actor, id, SCOPES);
        if (current.version != version) throw new IllegalArgumentException("automation.articleChanged");
        if (!"draft".equals(current.status)) throw new IllegalArgumentException("automation.articleNotDraft");
        JsonObject normalized = new JsonObject();
        normalized.addProperty("articleId", id);
        normalized.addProperty("version", version);
        normalized.addProperty("title", current.title);
        return normalized;
    }

    @Override
    public AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
        context.checkActive();
        int id = integer(parameters, "articleId", 1);
        int version = integer(parameters, "version", 0);
        ContentToolModels.ArticleDetails current = content.details(context.actor(), id, SCOPES);
        if (current.version != version || !"draft".equals(current.status)) {
            return attention(id, "admin.automation.articleChanged");
        }
        JsonObject args = new JsonObject();
        args.addProperty("id", id);
        args.addProperty("version", version);
        ContentToolModels.SavedArticle saved;
        try {
            context.checkActive();
            saved = content.saveWithRefresh(context.actor(), "publish_article", args, SCOPES, context.audit(), () -> {
                try { refresh.run(context); }
                catch (Exception error) { throw new IllegalStateException("automation.publicationRefreshFailed", error); }
            });
        } catch (UpdateArticleExpireException changed) {
            return attention(id, "admin.automation.articleChanged");
        }
        var result = "failed".equals(saved.refreshStatus) ? attention(id, "admin.automation.publicationRefreshFailed")
                : AutomationModels.Result.success("admin.automation.publish.completed");
        result.actionPath = "/article-edit?id=" + id;
        result.text = current.title;
        result.notifyOwner = "success".equals(result.status);
        return result;
    }

    private static AutomationModels.Result attention(int id, String key) {
        var result = AutomationModels.Result.attention(key);
        result.actionPath = "/article-edit?id=" + id;
        return result;
    }

    private static int integer(JsonObject parameters, String key, int minimum) {
        try {
            if (!parameters.has(key) || !parameters.get(key).isJsonPrimitive() || !parameters.getAsJsonPrimitive(key).isNumber())
                throw new IllegalArgumentException();
            int number = parameters.get(key).getAsBigDecimal().intValueExact();
            if (number < minimum) throw new IllegalArgumentException();
            return number;
        } catch (RuntimeException error) { throw new IllegalArgumentException("automation.invalidParameters", error); }
    }

    @FunctionalInterface
    public interface Refresh { void run(TaskExecutionContext context) throws Exception; }
}
