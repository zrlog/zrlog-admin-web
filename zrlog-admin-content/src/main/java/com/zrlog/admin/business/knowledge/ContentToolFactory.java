package com.zrlog.admin.business.knowledge;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Set;

/** Binds an explicit account and source to an individual AI or MCP operation. */
@Singleton
public final class ContentToolFactory {
    private final ContentToolProvider.Factory tools;
    private final KnowledgeService.Factory knowledge;
    private final AccountIdentityService identities;

    @Inject
    public ContentToolFactory(
            ContentToolProvider.Factory tools,
            KnowledgeService.Factory knowledge,
            AccountIdentityService identities) {
        this.tools = tools;
        this.knowledge = knowledge;
        this.identities = identities;
    }

    public ContentToolProvider create(AccountAccess actor, Set<String> scopes, HttpRequest request,
                                      String language, AdminAuditContext audit, AdminTokenVO credential) {
        identities.revalidate(actor);
        return tools.create(actor, scopes, request, language, audit, credential, knowledge(actor, scopes, language));
    }

    public KnowledgeService knowledge(AccountAccess actor, Set<String> scopes, String language) {
        identities.revalidate(actor);
        return knowledge.create(() -> identities.revalidate(actor), scopes, language);
    }
}
