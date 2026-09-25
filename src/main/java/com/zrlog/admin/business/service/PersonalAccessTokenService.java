package com.zrlog.admin.business.service;

import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels.Identity;
import com.zrlog.admin.business.security.PersonalTokenModels.*;
import com.zrlog.admin.business.security.SecurityStore;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.util.ZrLogUtil;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/** Account-owned, finite-lived credentials for the read-only MCP resource. */
public final class PersonalAccessTokenService {
    public static final String PREFIX = "zrmcp_";
    public static final List<String> SCOPES = List.of("articles:read", "articles:read_drafts", "articles:read_private", "articles:all");
    private final SecurityStore store = new SecurityStore();
    private final String resource;

    public PersonalAccessTokenService(String resource) { this.resource = resource; }

    public static List<String> availableScopes(AccountAccess account) {
        return SCOPES.stream().filter(account.scopes()::contains).collect(Collectors.toList());
    }

    public Created create(Create request) throws SQLException {
        AccountAccess account = AccountPermissionService.current();
        if (ZrLogUtil.isPreviewMode()) throw new OAuthException("access_denied", 403);
        if (request == null) throw new com.zrlog.common.exception.ArgsException();
        request.doValid();
        Set<String> selected = new LinkedHashSet<>(request.scopes);
        if (!selected.contains("articles:read") || !availableScopes(account).containsAll(selected)) throw new OAuthException("invalid_scope");
        List<String> ordered = SCOPES.stream().filter(selected::contains).collect(Collectors.toList());
        Created result = new Created();
        result.token = PREFIX + OAuthService.random();
        Info info = new Info();
        info.id = OAuthService.random(); info.userId = account.getUserId(); info.name = request.name.trim();
        info.scopes = ordered; info.resource = resource;
        info.createdAt = System.currentTimeMillis(); info.expiresAt = info.createdAt + request.expiresInDays * 86_400_000L;
        store.transaction(c -> store.update(c,
                "insert into user_access_token(id,userId,name,tokenHash,scope,resource,authVersion,createdAt,expiresAt,revoked) values(?,?,?,?,?,?,?,?,?,?)",
                info.id, info.userId, info.name, OAuthService.hash(result.token), String.join(" ", ordered), resource,
                account.getAuthVersion(), info.createdAt, info.expiresAt, false));
        result.info = info;
        return result;
    }

    public List<Info> list(AccountAccess account) throws SQLException {
        return store.transaction(c -> {
            List<Info> result = new ArrayList<>();
            for (Map<String,Object> row : store.list(c,
                    "select id,userId,name,scope,resource,authVersion,createdAt,expiresAt,revoked from user_access_token where userId=? order by createdAt desc,id",
                    account.getUserId())) result.add(info(row, account));
            return result;
        });
    }

    private Info info(Map<String,Object> row, AccountAccess account) {
        Info info = new Info(); info.id = (String) row.get("id"); info.userId = ((Number) row.get("userId")).intValue();
        info.name = (String) row.get("name"); info.resource = (String) row.get("resource");
        info.scopes = Arrays.asList(((String) row.get("scope")).split(" "));
        info.createdAt = ((Number) row.get("createdAt")).longValue(); info.expiresAt = ((Number) row.get("expiresAt")).longValue();
        info.revoked = AccountAccess.truth(row.get("revoked")); info.expired = info.expiresAt <= System.currentTimeMillis();
        info.invalidated = !account.isEnabled() || ((Number) row.get("authVersion")).intValue() != account.getAuthVersion()
                || !resource.equals(info.resource);
        return info;
    }

    public void revoke(String id) throws SQLException {
        AccountAccess account = AccountPermissionService.current();
        store.transaction(c -> store.update(c, "update user_access_token set revoked=? where id=? and userId=?", true, id, account.getUserId()));
    }

    public Identity authenticate(String token, String requestedResource, Set<String> required) throws SQLException {
        if (token == null || !token.matches("zrmcp_[A-Za-z0-9_-]{43}") || !resource.equals(requestedResource)) throw new OAuthException("invalid_token", 401);
        return store.transaction(c -> {
            Map<String,Object> row = store.one(c, "select * from user_access_token where tokenHash=?", OAuthService.hash(token));
            if (row == null || AccountAccess.truth(row.get("revoked"))
                    || ((Number) row.get("expiresAt")).longValue() <= System.currentTimeMillis()
                    || !resource.equals(row.get("resource"))) throw new OAuthException("invalid_token", 401);
            AccountAccess account = AccountAccess.from(store.one(c, "select * from user where userId=?", row.get("userId")));
            if (!account.isEnabled() || account.getAuthVersion() != ((Number) row.get("authVersion")).intValue()) throw new OAuthException("invalid_token", 401);
            Set<String> scopes = new LinkedHashSet<>(Arrays.asList(((String) row.get("scope")).split(" ")));
            scopes.retainAll(availableScopes(account));
            if (!scopes.containsAll(required)) throw new OAuthException("insufficient_scope", 403);
            Identity identity = new Identity(); identity.userId = account.getUserId(); identity.role = account.getRole();
            identity.scopes = new ArrayList<>(scopes);
            return identity;
        });
    }
}
