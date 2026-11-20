package com.zrlog.admin.business.service;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.automation.AutomationModels;
import com.zrlog.admin.business.automation.ScheduledTaskProvider;
import com.zrlog.admin.business.automation.TaskExecutionContext;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import java.util.List;
import java.util.function.Supplier;

/**
 * Scheduled publication uses the same version, ownership and audit chain as interactive
 * publication.
 */
public final class ScheduledArticleTaskProvider implements ScheduledTaskProvider {
    private final Supplier<ArticlePublication> publication;
    private final Refresh refresh;

    public ScheduledArticleTaskProvider(Supplier<ArticlePublication> publication, Refresh refresh) {
        this.publication = publication;
        this.refresh = refresh;
    }

    @Override
    public List<AutomationModels.Definition> systemDefinitions() {
        return List.of();
    }

    @Override
    public List<String> types() {
        return List.of("article.publish");
    }

    @Override
    public List<AutomationModels.Definition> definitions() {
        var definition = new AutomationModels.Definition();
        definition.type = "article.publish";
        definition.titleKey = "admin.assistant.publish.title";
        definition.descriptionKey = "admin.assistant.publish.description";
        definition.category = "content";
        definition.timeoutSeconds = 50;
        definition.requiredActions =
                List.of(
                        AccountAction.ARTICLE_READ.getId(),
                        AccountAction.ARTICLE_UPDATE.getId(),
                        AccountAction.ARTICLE_PUBLISH.getId());
        var article = new AutomationModels.Field();
        article.name = "articleId";
        article.type = "article";
        article.labelKey = "admin.assistant.publish.article";
        article.required = true;
        article.minimum = 1L;
        definition.fields = List.of(article);
        definition.defaultSchedule = new AutomationModels.Schedule();
        return List.of(definition);
    }

    @Override
    public String instanceKey(String type, JsonObject parameters) {
        return "article:" + integer(parameters, "articleId", 1);
    }

    @Override
    public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor)
            throws Exception {
        AccountPermissionService.requireAction(actor, AccountAction.ARTICLE_PUBLISH.getId());
        int id = integer(parameters, "articleId", 1);
        int version = integer(parameters, "version", 0);
        String title = publication.get().confirmSchedule(actor, id, version);
        JsonObject normalized = new JsonObject();
        normalized.addProperty("articleId", id);
        normalized.addProperty("version", version);
        normalized.addProperty("title", title);
        return normalized;
    }

    @Override
    public AutomationModels.Result execute(
            String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
        context.checkActive();
        int id = integer(parameters, "articleId", 1);
        int version = integer(parameters, "version", 0);
        ArticlePublication.ScheduledResult saved;
        try {
            saved =
                    publication
                            .get()
                            .publishScheduledDraft(
                                    context.actor(),
                                    id,
                                    version,
                                    context.audit(),
                                    context::checkActive,
                                    () -> {
                                        try {
                                            refresh.run(context);
                                        } catch (Exception error) {
                                            throw new IllegalStateException(
                                                    "automation.publicationRefreshFailed", error);
                                        }
                                    });
        } catch (UpdateArticleExpireException changed) {
            return attention(id, "admin.assistant.error.articleChanged");
        }
        var result =
                saved.refresh == ArticlePublication.RefreshStatus.FAILED
                        ? attention(id, "admin.assistant.error.publicationRefreshFailed")
                        : AutomationModels.Result.success(
                                "admin.assistant.publish.success.completed");
        result.actionPath = "/article-edit?id=" + id;
        result.text = saved.title;
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
            if (!parameters.has(key)
                    || !parameters.get(key).isJsonPrimitive()
                    || !parameters.getAsJsonPrimitive(key).isNumber())
                throw new IllegalArgumentException();
            int number = parameters.get(key).getAsBigDecimal().intValueExact();
            if (number < minimum) throw new IllegalArgumentException();
            return number;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("automation.invalidParameters", error);
        }
    }

    @FunctionalInterface
    public interface Refresh {
        void run(TaskExecutionContext context) throws Exception;
    }
}
