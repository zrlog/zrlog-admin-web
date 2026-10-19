package com.zrlog.admin.business.knowledge;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.service.ContentToolService;

import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Binds application services to an individual AI or MCP operation. */
public final class ContentToolFactory {
    private final ContentToolService content;
    private final Supplier<AttachmentStorage> storage;
    private final Supplier<String> backendUrl;
    private final BooleanSupplier staticHtml;

    public ContentToolFactory(ContentToolService content, Supplier<AttachmentStorage> storage, Supplier<String> backendUrl, BooleanSupplier staticHtml) {
        this.content = Objects.requireNonNull(content);
        this.storage = Objects.requireNonNull(storage);
        this.backendUrl = Objects.requireNonNull(backendUrl);
        this.staticHtml = Objects.requireNonNull(staticHtml);
    }

    public ContentToolProvider create(Set<String> scopes, HttpRequest request, String language) {
        return new ContentToolProvider(scopes, request, language, content, storage, knowledge(scopes, language));
    }

    public KnowledgeService knowledge(Set<String> scopes, String language) {
        return new KnowledgeService(com.zrlog.admin.business.service.AccountPermissionService::current, scopes, backendUrl, staticHtml, language);
    }
}
