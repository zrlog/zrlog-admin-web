package com.zrlog.admin.business.service;

import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels.Identity;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.PersonalTokenModels.*;
import com.zrlog.admin.business.security.SecurityStore;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.ZrLogUtil;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/** Account-owned credentials using the account action catalog; OAuth scopes remain a protocol adapter. */
public final class PersonalAccessTokenService {
    public static final String PREFIX = "zrpat_";
    public static final String LEGACY_PREFIX = "zrmcp_";
    public static final List<String> SCOPES = List.of("articles:read", "articles:read_drafts", "articles:read_private", "articles:all");
    private static final String INHERIT = "v2:inherit";
    private static final String CUSTOM = "v2:custom ";
    private final SecurityStore store = new SecurityStore();
    private final String resource;

    public PersonalAccessTokenService(String mcpResource) { this.resource = mcpResource; }
    private String siteResource() { return resource.substring(0, resource.length() - "/mcp".length()); }
    public static boolean isPersonalToken(String token) {
        return token != null && (token.startsWith(PREFIX) || token.startsWith(LEGACY_PREFIX));
    }
    public static List<String> availableScopes(AccountAccess account) {
        return SCOPES.stream().filter(account.scopes()::contains).collect(Collectors.toList());
    }
    public static List<String> availablePermissions(AccountAccess account) {
        return Arrays.stream(AccountAction.values()).filter(action -> action.allowed(account))
                .map(AccountAction::getId).collect(Collectors.toList());
    }

    public Created create(Create request) throws SQLException {
        AccountAccess account = AccountPermissionService.current();
        if (ZrLogUtil.isPreviewMode()) throw new OAuthException("access_denied", 403);
        if (request == null) throw new com.zrlog.common.exception.ArgsException();
        request.doValid();
        String storedPermissions;
        boolean legacy = request.permissionMode == null;
        if (legacy) {
            Set<String> selected = new LinkedHashSet<>(request.scopes);
            if (!selected.contains("articles:read") || !availableScopes(account).containsAll(selected)) throw new OAuthException("invalid_scope");
            storedPermissions = String.join(" ", SCOPES.stream().filter(selected::contains).collect(Collectors.toList()));
        } else if ("inherit".equals(request.permissionMode)) {
            // A limited credential cannot mint one that inherits its owner's broader rights.
            if (!request.permissions.isEmpty() || DelegatedAccess.restricted()) throw new OAuthException("invalid_scope");
            storedPermissions = INHERIT;
        } else {
            Set<String> selected = new LinkedHashSet<>(request.permissions);
            if (selected.isEmpty() || !availablePermissions(account).containsAll(selected)) throw new OAuthException("invalid_scope");
            storedPermissions = CUSTOM + String.join(" ", availablePermissions(account).stream().filter(selected::contains).collect(Collectors.toList()));
        }
        if (storedPermissions.length() > 512) throw new OAuthException("invalid_scope");
        Created result = new Created();
        result.token = (legacy ? LEGACY_PREFIX : PREFIX) + OAuthService.random();
        Info info = new Info();
        info.id = OAuthService.random(); info.userId = account.getUserId(); info.name = request.name.trim();
        info.resource = legacy ? resource : siteResource();
        describePermissions(info, storedPermissions, account);
        info.createdAt = System.currentTimeMillis(); info.expiresAt = info.createdAt + request.expiresInDays * 86_400_000L;
        store.withSession(c -> store.update(c,
                "insert into user_access_token(id,userId,name,tokenHash,scope,resource,authVersion,createdAt,expiresAt,revoked) values(?,?,?,?,?,?,?,?,?,?)",
                info.id, info.userId, info.name, OAuthService.hash(result.token), storedPermissions, info.resource,
                account.getAuthVersion(), info.createdAt, info.expiresAt, false));
        result.info = info;
        return result;
    }

    public List<Info> list(AccountAccess account) throws SQLException {
        return store.withSession(c -> {
            List<Info> result = new ArrayList<>();
            for (Map<String,Object> row : store.list(c,
                    "select id,userId,name,scope,resource,authVersion,createdAt,expiresAt,revoked from user_access_token where userId=? order by createdAt desc,id",
                    account.getUserId())) result.add(info(row, account));
            return result;
        });
    }

    private void describePermissions(Info info, String stored, AccountAccess account) {
        if (INHERIT.equals(stored)) {
            info.permissionMode = "inherit";
            info.permissions = availablePermissions(account);
            info.scopes = new ArrayList<>(account.scopes());
        } else if (stored.startsWith(CUSTOM)) {
            info.permissionMode = "custom";
            info.permissions = Arrays.asList(stored.substring(CUSTOM.length()).split(" "));
            info.scopes = new ArrayList<>(account.restrictActions(info.permissions).scopes());
        } else {
            info.permissionMode = "legacy";
            info.permissions = List.of(AccountAction.ARTICLE_READ.getId());
            info.scopes = Arrays.asList(stored.split(" "));
        }
    }

    private Info info(Map<String,Object> row, AccountAccess account) {
        Info info = new Info(); info.id = (String) row.get("id"); info.userId = ((Number) row.get("userId")).intValue();
        info.name = (String) row.get("name"); info.resource = (String) row.get("resource");
        describePermissions(info, (String) row.get("scope"), account);
        info.createdAt = ((Number) row.get("createdAt")).longValue(); info.expiresAt = ((Number) row.get("expiresAt")).longValue();
        info.revoked = AccountAccess.truth(row.get("revoked")); info.expired = info.expiresAt <= System.currentTimeMillis();
        info.invalidated = !account.isEnabled() || ((Number) row.get("authVersion")).intValue() != account.getAuthVersion()
                || !("legacy".equals(info.permissionMode) ? resource : siteResource()).equals(info.resource);
        return info;
    }

    public void revoke(String id) throws SQLException {
        AccountAccess account = AccountPermissionService.current();
        store.withSession(c -> store.update(c, "update user_access_token set revoked=? where id=? and userId=?", true, id, account.getUserId()));
    }

    public Identity authenticate(String token, String requestedResource, Set<String> required) throws SQLException {
        boolean legacy = token != null && token.startsWith(LEGACY_PREFIX);
        if (token == null || !token.matches("(?:zrpat_|zrmcp_)[A-Za-z0-9_-]{43}")) throw new OAuthException("invalid_token", 401);
        if (legacy ? !resource.equals(requestedResource)
                : !Set.of(resource, siteResource() + "/api/admin", siteResource() + WebhookService.MESSAGE_CENTER_NOTICE_ENDPOINT).contains(requestedResource)) {
            throw new OAuthException("invalid_token", 401);
        }
        String audience = legacy ? resource : siteResource();
        return store.withSession(c -> {
            Map<String,Object> row = store.one(c,
                    "select u.userId,u.role,u.enabled,u.authVersion,t.scope from user_access_token t inner join user u on u.userId=t.userId "
                            + "where t.tokenHash=? and t.revoked=? and t.expiresAt>? and t.resource=? and u.enabled=? and u.authVersion=t.authVersion",
                    OAuthService.hash(token), false, System.currentTimeMillis(), audience, true);
            if (row == null) throw new OAuthException("invalid_token", 401);
            AccountAccess account = AccountAccess.from(row);
            Info info = new Info(); describePermissions(info, (String) row.get("scope"), account);
            if (legacy != "legacy".equals(info.permissionMode)) throw new OAuthException("invalid_token", 401);
            Set<String> scopes = new LinkedHashSet<>(info.scopes);
            scopes.retainAll(account.scopes());
            if (legacy) scopes.retainAll(SCOPES);
            if (!scopes.containsAll(required)) throw new OAuthException("insufficient_scope", 403);
            Identity identity = new Identity(); identity.userId = account.getUserId(); identity.authVersion = account.getAuthVersion(); identity.role = account.getRole();
            identity.scopes = new ArrayList<>(scopes); identity.permissionMode = info.permissionMode;
            identity.permissions = availablePermissions(account.restrictActions(info.permissions));
            return identity;
        });
    }
}
