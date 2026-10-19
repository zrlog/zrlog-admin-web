package com.zrlog.admin.business.knowledge;

import com.google.gson.JsonObject;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.knowledge.KnowledgeModels.Tool;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.OAuthModels.Identity;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAction;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import jakarta.inject.Named;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/** Reauthenticates each operation and binds the same account actions used by ordinary admin APIs. */
public final class McpContentService implements ToolProvider {
    private final Supplier<Identity> authenticate;
    private final HttpRequest request;
    private final String language;
    private final ContentToolFactory tools;
    private final Supplier<String> issuer;

    @AssistedInject
    public McpContentService(@Assisted Supplier<Identity> authenticate, @Assisted HttpRequest request,
                             @Assisted String language, Supplier<ContentToolFactory> tools,
                             @Named("mcpIssuer") Supplier<String> issuer) {
        this.authenticate = authenticate;
        this.request = request;
        this.language = language;
        this.tools = Objects.requireNonNull(tools.get());
        this.issuer = Objects.requireNonNull(issuer);
    }

    @AssistedFactory
    public interface Factory {
        McpContentService create(Supplier<Identity> authenticate, HttpRequest request, String language);
    }
    @Override public List<Tool> definitions(String language) throws Exception {
        Identity identity = authenticate.get();
        return asAccount(identity, () -> tools(identity).definitions(language));
    }
    @Override public Object call(String name, JsonObject arguments) throws Exception {
        Identity identity = authenticate.get();
        return asAccount(identity, () -> tools(identity).call(name, arguments));
    }
    private ContentToolProvider tools(Identity identity) {
        return tools.create(Set.copyOf(identity.scopes), request, language);
    }
    private <T> T asAccount(Identity identity, Callable<T> action) throws Exception {
        AdminTokenVO token=new AdminTokenVO();token.setUserId(identity.userId);token.setAuthVersion(identity.authVersion);
        token.setProtocol(URI.create(issuer.get()).getScheme());
        token.setSessionId("mcp-"+identity.userId);
        Set<String> permissions=new HashSet<>();
        if (identity.permissionMode != null) permissions.addAll(identity.permissions);
        else {
            for (AccountAction entry : AccountAction.values()) if (entry.getScope()!=null && identity.scopes.contains(entry.getScope())) permissions.add(entry.getId());
            if (identity.scopes.contains("taxonomy:read")) permissions.add(AccountAction.TAXONOMY_READ.getId());
        }
        return AdminTokenThreadLocal.withUser(token, () -> DelegatedAccess.withPermissions(permissions,"inherit".equals(identity.permissionMode), () -> {
            try (AdminLanguageContext ignored=AdminLanguageContext.open(language)) { return action.call(); }
        }));
    }
}
