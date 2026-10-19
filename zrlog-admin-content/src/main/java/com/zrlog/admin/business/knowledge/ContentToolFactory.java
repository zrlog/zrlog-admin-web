package com.zrlog.admin.business.knowledge;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.service.AccountPermissionService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Set;

/** Binds application services to an individual AI or MCP operation. */
@Singleton
public final class ContentToolFactory {
    private final ContentToolProvider.Factory tools;
    private final KnowledgeService.Factory knowledge;
    private final AccountPermissionService permissions;

    @Inject
    public ContentToolFactory(
            ContentToolProvider.Factory tools,
            KnowledgeService.Factory knowledge,
            AccountPermissionService permissions) {
        this.tools = tools;
        this.knowledge = knowledge;
        this.permissions = permissions;
    }

    public ContentToolProvider create(Set<String> scopes, HttpRequest request, String language) {
        return tools.create(scopes, request, language, knowledge(scopes, language));
    }

    public KnowledgeService knowledge(Set<String> scopes, String language) {
        return knowledge.create(permissions::current, scopes, language);
    }
}
