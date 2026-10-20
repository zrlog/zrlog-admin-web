package com.zrlog.admin.business.knowledge;

import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.google.gson.JsonObject;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.knowledge.KnowledgeModels.Tool;
import com.zrlog.admin.business.service.ContentToolService;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.ZrLogUtil;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.*;
import java.util.function.Supplier;

/** Shared tool dispatch; callers bind a trusted, revalidated account before each operation. */
public final class ContentToolProvider implements ToolProvider {
    private final AccountIdentityService identities;
    private final AccountAccess actor;
    private final AdminAuditContext audit;
    private final AdminTokenVO credential;

    private final Set<String> scopes;
    private final HttpRequest request;
    private final String language;
    private final ContentToolService content;
    private final Supplier<AttachmentStorage> storage;
    private final KnowledgeService knowledge;

    @AssistedInject
    public ContentToolProvider(
            @Assisted AccountAccess actor,
            @Assisted AdminAuditContext audit,
            @Assisted AdminTokenVO credential,
            @Assisted Set<String> scopes,
            @Assisted HttpRequest request,
            @Assisted String language,
            ContentToolService content,
            Supplier<AttachmentStorage> storage,
            @Assisted KnowledgeService knowledge,
            AccountIdentityService identities) {
        this.identities = identities;
        this.actor = actor;
        this.audit = audit;
        this.credential = copyCredential(credential);

        this.scopes = Set.copyOf(scopes);
        this.request = request;
        this.language = language;
        this.content = content;
        this.storage = storage;
        this.knowledge = knowledge;
    }

    @AssistedFactory
    public interface Factory {
        ContentToolProvider create(
                AccountAccess actor,
                Set<String> scopes,
                HttpRequest request,
                String language,
                AdminAuditContext audit,
                AdminTokenVO credential,
                KnowledgeService knowledge);
    }

    @Override public List<Tool> definitions(String language) throws Exception {
        AccountAccess account = identities.revalidate(actor);
        List<Tool> tools = new ArrayList<>();
        for (Tool tool : ContentToolCatalog.tools(language)) if (allowed(tool.name, account)) tools.add(tool);
        return tools;
    }
    @Override public Object call(String name, JsonObject arguments) throws Exception {
        AccountAccess account = identities.revalidate(actor);
        if (!allowed(name,account)) throw new PermissionErrorException();
        JsonObject args = arguments == null ? new JsonObject() : arguments;
        if (name.equals("search_articles") || name.equals("read_article")) {
            return knowledge.call(name,args);
        }
        Tool tool = ContentToolCatalog.tools(language).stream().filter(t -> t.name.equals(name)).findFirst().orElseThrow(PermissionErrorException::new);
        ContentToolCatalog.validate(tool,args,language);
        switch (name) {
            case "get_article": return content.details(account,args.get("id").getAsInt(),scopes);
            case "list_categories": case "list_tags":
                return content.taxonomy(account,name.equals("list_categories"),args.has("offset")?args.get("offset").getAsInt():0,args.has("limit")?args.get("limit").getAsInt():50);
            case "create_article": case "update_article": case "publish_article":
                return content.save(account,name,args,scopes,request,audit);
            case "upload_attachment": return upload(account, args);
            default: throw new PermissionErrorException();
        }
    }
    private boolean allowed(String name, AccountAccess account) {
        switch (name) {
            case "search_articles": case "read_article": case "get_article": return AccountAction.ARTICLE_READ.allowed(account);
            case "list_categories": case "list_tags": return AccountAction.TAXONOMY_READ.allowed(account);
            case "create_article": return AccountAction.ARTICLE_CREATE.allowed(account);
            case "update_article": return AccountAction.ARTICLE_UPDATE.allowed(account);
            case "publish_article": return AccountAction.ARTICLE_UPDATE.allowed(account) && AccountAction.ARTICLE_PUBLISH.allowed(account);
            case "upload_attachment": return AccountAction.ASSET_UPLOAD.allowed(account) && storage.get() != null;
            default: return false;
        }
    }
    private Object upload(AccountAccess account, JsonObject args) throws Exception {
        if (ZrLogUtil.isPreviewMode()) throw new PermissionErrorException();
        String filename=args.get("filename").getAsString();
        if (filename.contains("/") || filename.contains("\\") || filename.codePoints().anyMatch(Character::isISOControl)
            || !filename.matches("[^?#]+\\.[A-Za-z0-9]{1,16}")) throw invalid();
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(args.get("data").getAsString()); }
        catch (IllegalArgumentException e) { throw invalid(); }
        if (bytes.length == 0 || bytes.length > ContentToolCatalog.MAX_ATTACHMENT_BYTES) throw invalid();
        AttachmentStorage target = storage.get();
        if (target == null) throw new PermissionErrorException();
        ContentToolModels.Attachment result = new ContentToolModels.Attachment();
        result.url=target.saveAttachment(account, bytes, filename, request, credential);result.filename=filename;result.size=bytes.length;
        return result;
    }
    private static AdminTokenVO copyCredential(AdminTokenVO credential) {
        if (credential == null) return null;
        AdminTokenVO copy = new AdminTokenVO();
        copy.setUserId(credential.getUserId()); copy.setAuthVersion(credential.getAuthVersion());
        copy.setSessionId(credential.getSessionId()); copy.setProtocol(credential.getProtocol());
        copy.setCreatedDate(credential.getCreatedDate()); copy.setExpiresAt(credential.getExpiresAt());
        return copy;
    }

    private IllegalArgumentException invalid() { return new IllegalArgumentException(new KnowledgeMessages(language).get("admin.mcp.validation.attachment")); }
}
