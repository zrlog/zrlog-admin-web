package com.zrlog.admin.business.knowledge;

import com.google.gson.JsonObject;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.knowledge.KnowledgeModels.Tool;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.OAuthModels.Identity;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.business.service.ContentToolService;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.ZrLogUtil;
import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.Callable;

/** Reauthenticates each operation and binds the same account actions used by ordinary admin APIs. */
public final class McpContentService implements ToolProvider {
    private final Supplier<Identity> authenticate;
    private final HttpRequest request;
    private final String language;
    private final ContentToolService content;
    private final Supplier<AttachmentStorage> storage;
    public McpContentService(Supplier<Identity> authenticate, HttpRequest request, String language) {
        this(authenticate,request,language,new ContentToolService(),AttachmentStorage::current);
    }
    public McpContentService(Supplier<Identity> authenticate, HttpRequest request, String language,
                             ContentToolService content, Supplier<AttachmentStorage> storage) {
        this.authenticate=authenticate;this.request=request;this.language=language;this.content=content;this.storage=storage;
    }
    @Override public List<Tool> definitions(String language) throws Exception {
        Identity identity = authenticate.get();
        return asAccount(identity, () -> {
            AccountAccess account = AccountPermissionService.current();
            List<Tool> tools = new ArrayList<>();
            for (Tool tool : McpToolCatalog.tools(language)) if (allowed(tool.name, account)) tools.add(tool);
            return tools;
        });
    }
    @Override public Object call(String name, JsonObject arguments) throws Exception {
        Identity identity = authenticate.get();
        return asAccount(identity, () -> {
            AccountAccess account = AccountPermissionService.current();
            if (!allowed(name,account)) throw new PermissionErrorException();
            Set<String> scopes = Set.copyOf(identity.scopes);
            JsonObject args = arguments == null ? new JsonObject() : arguments;
            if (name.equals("search_articles") || name.equals("read_article")) {
                return new KnowledgeService(() -> AccountPermissionService.current(),scopes,BackendServerUrl::configured,language).call(name,args);
            }
            Tool tool = McpToolCatalog.tools(language).stream().filter(t -> t.name.equals(name)).findFirst().orElseThrow(PermissionErrorException::new);
            McpToolCatalog.validate(tool,args,language);
            switch (name) {
                case "get_article": return content.details(args.get("id").getAsInt(),scopes);
                case "list_categories": case "list_tags":
                    return content.taxonomy(name.equals("list_categories"),args.has("offset")?args.get("offset").getAsInt():0,args.has("limit")?args.get("limit").getAsInt():50);
                case "create_article": case "update_article": case "publish_article": return content.save(name,args,scopes,request);
                case "upload_attachment": return upload(args);
                default: throw new PermissionErrorException();
            }
        });
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
    private Object upload(JsonObject args) throws Exception {
        if (ZrLogUtil.isPreviewMode()) throw new PermissionErrorException();
        String filename=args.get("filename").getAsString();
        if (filename.contains("/") || filename.contains("\\") || filename.codePoints().anyMatch(Character::isISOControl)
                || !filename.matches("[^?#]+\\.[A-Za-z0-9]{1,16}")) throw invalid();
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(args.get("data").getAsString()); }
        catch (IllegalArgumentException e) { throw invalid(); }
        if (bytes.length == 0 || bytes.length > McpToolCatalog.MAX_ATTACHMENT_BYTES) throw invalid();
        AttachmentStorage target = storage.get();
        if (target == null) throw new PermissionErrorException();
        ContentToolModels.Attachment result = new ContentToolModels.Attachment();
        result.url=target.saveAttachment(bytes,filename,request);result.filename=filename;result.size=bytes.length;
        return result;
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException(new KnowledgeMessages(language).get("admin.mcp.validation.attachment")); }
    private <T> T asAccount(Identity identity, Callable<T> action) throws Exception {
        AdminTokenVO token=new AdminTokenVO();token.setUserId(identity.userId);token.setAuthVersion(identity.authVersion);
        token.setProtocol(URI.create(BackendServerUrl.configured()).getScheme());
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
