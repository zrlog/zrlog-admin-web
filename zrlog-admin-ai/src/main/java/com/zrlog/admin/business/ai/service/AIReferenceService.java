package com.zrlog.admin.business.ai.service;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.ai.model.AIChatModels.ChatRequest;
import com.zrlog.admin.business.ai.model.AIChatModels.ResolvedReference;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.knowledge.KnowledgeModels.ArticleResult;
import com.zrlog.admin.business.knowledge.KnowledgeModels.SearchResult;
import com.zrlog.admin.business.knowledge.KnowledgeService;
import com.zrlog.admin.business.rest.base.UserPreferences;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.business.service.UserPreferenceService;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.I18nUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Explicit references use the same account and preference boundary as assistant knowledge tools. */
public final class AIReferenceService {
    private final com.zrlog.admin.business.knowledge.ContentToolFactory tools;

    public AIReferenceService(com.zrlog.admin.business.knowledge.ContentToolFactory tools) { this.tools = tools; }

    private KnowledgeService knowledge(UserPreferences.Assistant settings) {
        if (!AccountAction.ARTICLE_ASSIST.allowed(AccountPermissionService.current())
                || "off".equals(settings.knowledgeScope)) throw new PermissionErrorException();
        return java.util.Objects.requireNonNull(tools, "Content tools are required for article references")
                .knowledge(AIChatService.scopes(settings), I18nUtil.getCurrentLocale());
    }

    public SearchResult search(String query) throws SQLException {
        if (query == null || query.length() > 200) throw new ArgsException();
        if (!AccountAction.ARTICLE_ASSIST.allowed(AccountPermissionService.current())) throw new PermissionErrorException();
        UserPreferences.Assistant settings = new UserPreferenceService().assistant(AdminTokenThreadLocal.getUser());
        if ("off".equals(settings.knowledgeScope) || !AccountAction.ARTICLE_READ.allowed(AccountPermissionService.current()))
            return new SearchResult();
        JsonObject arguments = new JsonObject();
        arguments.addProperty("query", query);
        arguments.addProperty("limit", 8);
        return (SearchResult) knowledge(settings).call("search_articles", arguments);
    }

    public List<ResolvedReference> resolve(ChatRequest input, UserPreferences.Assistant settings) throws SQLException {
        List<ResolvedReference> result = new ArrayList<>();
        for (var reference : input.references) {
            ResolvedReference resolved = new ResolvedReference();
            resolved.type = reference.type;
            if ("article".equals(reference.type)) {
                JsonObject arguments = new JsonObject();
                arguments.addProperty("id", reference.articleId);
                arguments.addProperty("length", 4000);
                resolved.article = (ArticleResult) knowledge(settings).call("read_article", arguments);
            } else {
                resolved.fileIndex = reference.fileIndex;
                resolved.fileName = input.files.get(reference.fileIndex).name;
            }
            result.add(resolved);
        }
        return result;
    }
}
