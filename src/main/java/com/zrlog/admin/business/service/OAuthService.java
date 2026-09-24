package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels.*;
import com.zrlog.admin.business.security.SecurityStore;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.util.ZrLogUtil;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Authorization-code provider with pre-registered public clients and mandatory S256 PKCE. */
public final class OAuthService {
    public static final List<String> SCOPES = List.of("articles:read", "articles:read_drafts", "articles:read_private", "articles:all", "articles:write", "articles:publish", "articles:delete", "assets:write", "offline_access");
    private static final long ACCESS_TTL = 600_000L;
    private static final long REFRESH_TTL = 30L * 24 * 3600 * 1000;
    private static final Gson JSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecurityStore store = new SecurityStore();
    private final Supplier<String> configuredIssuer;
    public OAuthService() { this(OAuthService::configuredSiteIssuer); }
    private static String configuredSiteIssuer() {
        String host = Objects.toString(ZrLogUtil.getBlogHostByWebSite(), "").trim().replaceAll("/+$", "");
        if (!host.contains("://")) {
            URI parsed = URI.create("https://" + host);
            boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(Objects.toString(parsed.getHost(), ""));
            host = (local ? "http://" : "https://") + host;
        }
        return host + Objects.toString(com.zrlog.common.Constants.zrLogConfig.getServerConfig().getContextPath(), "");
    }
    public OAuthService(Supplier<String> issuer) { configuredIssuer = issuer; }

    public String issuer() {
        String value = Objects.toString(configuredIssuer.get(), "").replaceAll("/+$", "");
        URI uri = safeUri(value);
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) throw new OAuthException("invalid_request");
        return value;
    }
    public String resource() { return issuer() + "/api/oauth"; }
    public Metadata metadata() {
        Metadata m = new Metadata(); m.issuer = issuer(); m.authorization_endpoint = m.issuer + "/oauth/authorize";
        m.token_endpoint = m.issuer + "/oauth/token"; m.revocation_endpoint = m.issuer + "/oauth/revoke"; m.scopes_supported = SCOPES;
        return m;
    }
    public ResourceMetadata resourceMetadata() {
        ResourceMetadata m = new ResourceMetadata(); m.resource = resource(); m.authorization_servers = List.of(issuer()); return m;
    }
    public String resourceMetadataUrl() {
        URI uri = URI.create(issuer());
        return uri.getScheme() + "://" + uri.getRawAuthority() + "/.well-known/oauth-protected-resource" + uri.getRawPath() + "/api/oauth";
    }
    public static String random() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String hash(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static boolean equal(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
    private static URI safeUri(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null || value.contains("\\")) throw new IllegalArgumentException();
            boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            if (!"https".equals(uri.getScheme()) && !(local && "http".equals(uri.getScheme()))) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException e) { throw new OAuthException("invalid_request"); }
    }
    public void requireSameOrigin(String origin) {
        URI expected = URI.create(issuer());
        URI supplied = safeUri(Objects.toString(origin, ""));
        if (!Objects.equals(expected.getScheme(), supplied.getScheme()) || !Objects.equals(expected.getRawAuthority(), supplied.getRawAuthority())
                || !(supplied.getRawPath().isEmpty() || supplied.getRawPath().equals("/")) || supplied.getRawQuery() != null) throw new OAuthException("access_denied", 403);
    }
    public Client register(Client client) throws SQLException {
        AccountPermissionService.administrator();
        if (ZrLogUtil.isPreviewMode()) throw new OAuthException("access_denied", 403);
        if (client == null || client.name == null || client.name.isBlank() || client.name.length() > 128
                || client.redirectUris == null || client.redirectUris.isEmpty() || client.redirectUris.size() > 10) throw new OAuthException("invalid_client_metadata");
        for (String redirect : client.redirectUris) { if (redirect == null || redirect.length() > 2048) throw new OAuthException("invalid_client_metadata"); safeUri(redirect); }
        client.clientId = random(); client.enabled = true;
        store.transaction(c -> store.update(c, "insert into oauth_client (clientId,name,redirectUris,enabled) values (?,?,?,?)",
                client.clientId, client.name.trim(), JSON.toJson(client.redirectUris), true));
        return client;
    }
    private Client client(Connection c, String id) throws SQLException {
        Map<String,Object> row = store.one(c, "select * from oauth_client where clientId=?", id);
        if (row == null || !AccountAccess.truth(row.get("enabled"))) throw new OAuthException("invalid_client");
        Client client = new Client(); client.clientId = id; client.name = (String) row.get("name");
        client.redirectUris = Arrays.asList(JSON.fromJson((String) row.get("redirectUris"), String[].class)); return client;
    }
    private static Set<String> scopes(String scope) {
        if (scope == null || scope.isBlank() || scope.length() > 512) throw new OAuthException("invalid_scope");
        Set<String> values = new LinkedHashSet<>(Arrays.asList(scope.split(" +")));
        if (!SCOPES.containsAll(values) || Collections.disjoint(values, Set.of("articles:read", "articles:write", "articles:publish", "articles:delete", "assets:write"))) throw new OAuthException("invalid_scope");
        return values;
    }
    public String authorize(AuthorizationRequest request) throws SQLException {
        if (request == null || !"code".equals(request.response_type)) throw new OAuthException("unsupported_response_type");
        if (!resource().equals(request.resource)) throw new OAuthException("invalid_target");
        scopes(request.scope);
        if (!"S256".equals(request.code_challenge_method) || request.code_challenge == null || !request.code_challenge.matches("[A-Za-z0-9_-]{43}")) throw new OAuthException("invalid_request");
        if (request.state != null && request.state.length() > 2048) throw new OAuthException("invalid_request");
        return store.transaction(c -> {
            store.update(c, "update oauth_client set enabled=enabled where clientId=?", request.client_id);
            Client app = client(c, request.client_id);
            if (!app.redirectUris.contains(request.redirect_uri)) throw new OAuthException("invalid_request");
            Pending pending = new Pending(); pending.request = request;
            // Bound unauthenticated, abandoned requests.
            store.update(c, "delete from oauth_credential where kind=? and expiresAt<?", "pending", System.currentTimeMillis());
            Number pendingCount = (Number) store.one(c, "select count(*) as total from oauth_credential where kind=?", "pending").get("total");
            if (pendingCount.longValue() >= 1000) throw new OAuthException("temporarily_unavailable", 503);
            store.update(c, "delete from oauth_credential where expiresAt<?", System.currentTimeMillis() - REFRESH_TTL);
            String id = issue(c, "pending", null, pending, 600_000L);
            return issuer() + "/admin/oauth/authorize?request_id=" + encode(id);
        });
    }
    public Consent consent(String requestId) throws SQLException {
        AccountAccess account = AccountPermissionService.current();
        return store.transaction(c -> {
            lockPending(c, requestId);
            Map<String,Object> row = credential(c, requestId, "pending");
            Pending pending = JSON.fromJson((String) row.get("payload"), Pending.class);
            String session = hash(AdminTokenThreadLocal.getUser().getSessionId());
            if (pending.userId != 0 && (pending.userId != account.getUserId() || !equal(session, pending.sessionHash))) throw new OAuthException("access_denied", 403);
            String csrf = random(); pending.csrfHash = hash(csrf); pending.userId = account.getUserId(); pending.sessionHash = session;
            store.update(c, "update oauth_credential set payload=? where hash=? and used=?", JSON.toJson(pending), hash(requestId), false);
            Consent result = new Consent(); result.requestId = requestId; result.csrf = csrf;
            result.clientName = client(c, pending.request.client_id).name; result.redirectUri = pending.request.redirect_uri;
            result.resource = pending.request.resource; result.scopes = new ArrayList<>(scopes(pending.request.scope));
            result.availableScopes = result.scopes.stream().filter(s -> s.equals("offline_access") || account.scopes().contains(s)).collect(Collectors.toList());
            return result;
        });
    }
    public Redirect decide(Decision decision) throws SQLException {
        AccountAccess account = AccountPermissionService.current();
        if (decision == null || decision.csrf == null) throw new OAuthException("invalid_request");
        return store.transaction(c -> {
            lockPending(c, decision.requestId);
            Map<String,Object> row = credential(c, decision.requestId, "pending");
            Pending pending = JSON.fromJson((String) row.get("payload"), Pending.class);
            if (pending.userId != account.getUserId() || !equal(pending.sessionHash, hash(AdminTokenThreadLocal.getUser().getSessionId()))
                    || !equal(pending.csrfHash, hash(decision.csrf))) throw new OAuthException("access_denied", 403);
            client(c, pending.request.client_id);
            consume(c, decision.requestId);
            if (!decision.approve) return redirect(pending.request, "error", "access_denied");
            Set<String> selected = scopes(decision.scopes == null ? "" : String.join(" ", decision.scopes));
            if (!scopes(pending.request.scope).containsAll(selected) || selected.stream().anyMatch(s -> !s.equals("offline_access") && !account.scopes().contains(s))) throw new OAuthException("invalid_scope");
            String grant = random();
            store.update(c, "insert into oauth_grant (id,userId,clientId,scope,resource,authVersion,createdAt,revoked) values (?,?,?,?,?,?,?,?)",
                    grant, account.getUserId(), pending.request.client_id, String.join(" ", selected), pending.request.resource,
                    account.getAuthVersion(), System.currentTimeMillis(), false);
            String code = issue(c, "code", grant, pending.request, 120_000L);
            return redirect(pending.request, "code", code);
        });
    }
    private Redirect redirect(AuthorizationRequest request, String key, String value) {
        String uri = request.redirect_uri + (request.redirect_uri.contains("?") ? "&" : "?") + key + "=" + encode(value);
        if (request.state != null) uri += "&state=" + encode(request.state);
        return new Redirect(uri + "&iss=" + encode(issuer()));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String issue(Connection c, String kind, String grant, Object payload, long ttl) throws SQLException {
        String value = random();
        store.update(c, "insert into oauth_credential (hash,kind,grantId,payload,expiresAt,used) values (?,?,?,?,?,?)",
                hash(value), kind, grant, JSON.toJson(payload), System.currentTimeMillis() + ttl, false);
        return value;
    }
    private Map<String,Object> credential(Connection c, String value, String kind) throws SQLException {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) throw new OAuthException("invalid_grant");
        Map<String,Object> row = store.one(c, "select * from oauth_credential where hash=? and kind=?", hash(value), kind);
        if (row == null || AccountAccess.truth(row.get("used")) || ((Number) row.get("expiresAt")).longValue() <= System.currentTimeMillis()) throw new OAuthException("invalid_grant");
        return row;
    }
    private void lockPending(Connection c, String value) throws SQLException {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) throw new OAuthException("invalid_grant");
        store.update(c, "update oauth_credential set used=used where hash=? and kind=?", hash(value), "pending");
    }
    private void consume(Connection c, String value) throws SQLException {
        if (store.update(c, "update oauth_credential set used=? where hash=? and used=? and expiresAt>?", true, hash(value), false, System.currentTimeMillis()) != 1) throw new OAuthException("invalid_grant");
    }
    private Map<String,Object> liveGrant(Connection c, String id) throws SQLException {
        Map<String,Object> grant = store.one(c, "select * from oauth_grant where id=?", id);
        if (grant == null || AccountAccess.truth(grant.get("revoked"))) throw new OAuthException("invalid_grant");
        AccountAccess account = AccountAccess.from(store.one(c, "select * from user where userId=?", grant.get("userId")));
        if (!account.isEnabled() || account.getAuthVersion() != ((Number) grant.get("authVersion")).intValue()) throw new OAuthException("invalid_grant");
        client(c, (String) grant.get("clientId"));
        return grant;
    }
    public TokenResponse token(TokenRequest request) throws SQLException {
        if (request == null || !("authorization_code".equals(request.grant_type) || "refresh_token".equals(request.grant_type))) throw new OAuthException("unsupported_grant_type");
        if (!resource().equals(request.resource)) throw new OAuthException("invalid_target");
        boolean refresh = "refresh_token".equals(request.grant_type);
        String value = refresh ? request.refresh_token : request.code;
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) throw new OAuthException("invalid_grant");
        TokenResponse result = store.transaction(c -> {
            // Acquire a write lock before any read, including on SQLite with deferred transactions.
            store.update(c, "update oauth_credential set used=used where hash=? and kind=?", hash(value), refresh ? "refresh" : "code");
            client(c, request.client_id);
            Map<String,Object> record = store.one(c, "select * from oauth_credential where hash=? and kind=?", hash(value), refresh ? "refresh" : "code");
            if (record == null) throw new OAuthException("invalid_grant");
            String grantId = (String) record.get("grantId");
            // Serialize exchanges within the grant across processes, including refresh-token reuse detection.
            store.update(c, "update oauth_grant set revoked=revoked where id=?", grantId);
            Map<String,Object> grant = liveGrant(c, grantId);
            if (!Objects.equals(grant.get("clientId"), request.client_id) || !Objects.equals(grant.get("resource"), request.resource)) throw new OAuthException("invalid_grant");
            if (!refresh) {
                AuthorizationRequest original = JSON.fromJson((String) record.get("payload"), AuthorizationRequest.class);
                if (!Objects.equals(original.redirect_uri, request.redirect_uri) || request.code_verifier == null
                        || !request.code_verifier.matches("[A-Za-z0-9._~-]{43,128}") || !equal(original.code_challenge, hash(request.code_verifier))) throw new OAuthException("invalid_grant");
            }
            record = store.one(c, "select * from oauth_credential where hash=?", hash(value));
            if (AccountAccess.truth(record.get("used"))) {
                store.update(c, "update oauth_grant set revoked=? where id=?", true, grantId);
                return null; // commit the revocation before reporting the error
            }
            if (((Number) record.get("expiresAt")).longValue() <= System.currentTimeMillis()) throw new OAuthException("invalid_grant");
            Set<String> authorized = scopes((String) grant.get("scope"));
            Set<String> selected = request.scope == null ? authorized : scopes(request.scope);
            if (!authorized.containsAll(selected)) throw new OAuthException("invalid_scope");
            consume(c, value);
            TokenResponse token = new TokenResponse(); token.expires_in = ACCESS_TTL / 1000;
            token.scope = String.join(" ", selected);
            token.access_token = issue(c, "access", grantId, token.scope, ACCESS_TTL);
            if (authorized.contains("offline_access")) token.refresh_token = issue(c, "refresh", grantId, "", REFRESH_TTL);
            return token;
        });
        if (result == null) throw new OAuthException("invalid_grant");
        return result;
    }
    public Identity authenticate(String bearer, String resource, Set<String> required) throws SQLException {
        try {
            return store.transaction(c -> {
                Map<String,Object> record = credential(c, bearer, "access");
                Map<String,Object> grant = liveGrant(c, (String) record.get("grantId"));
                if (!Objects.equals(resource, grant.get("resource"))) throw new OAuthException("invalid_token", 401);
                AccountAccess account = AccountAccess.from(store.one(c, "select * from user where userId=?", grant.get("userId")));
                Set<String> scopes = scopes(JSON.fromJson((String) record.get("payload"), String.class));
                scopes.retainAll(account.scopes());
                if (!scopes.containsAll(required)) throw new OAuthException("insufficient_scope", 403);
                Identity identity = new Identity(); identity.userId = account.getUserId(); identity.role = account.getRole();
                identity.clientId = (String) grant.get("clientId"); identity.scopes = new ArrayList<>(scopes); return identity;
            });
        } catch (OAuthException e) { if (e.getStatus() == 403) throw e; throw new OAuthException("invalid_token", 401); }
    }
    public void revoke(String token, String clientId) throws SQLException {
        if (token == null || token.length() > 512) return;
        store.transaction(c -> {
            Map<String,Object> row = store.one(c, "select grantId from oauth_credential where hash=? and (kind=? or kind=?)", hash(token), "access", "refresh");
            if (row != null) store.update(c, "update oauth_grant set revoked=? where id=? and clientId=?", true, row.get("grantId"), clientId);
            return null;
        });
    }
    public void revokeGrant(String id) throws SQLException {
        AccountAccess account = AccountPermissionService.current();
        store.transaction(c -> store.update(c, "update oauth_grant set revoked=? where id=? and userId=?", true, id, account.getUserId()));
    }
    public void disableClient(String id) throws SQLException {
        AccountPermissionService.administrator();
        store.transaction(c -> {
            store.update(c, "update oauth_client set enabled=? where clientId=?", false, id);
            store.update(c, "update oauth_grant set revoked=? where clientId=?", true, id); return null;
        });
    }
    public Page page() throws SQLException {
        AccountAccess account = AccountPermissionService.current();
        return store.transaction(c -> {
            Page page = new Page(); page.administrator = account.isAdministrator(); page.issuer = issuer(); page.resource = resource();
            page.clients = new ArrayList<>();
            if (page.administrator) for (Map<String,Object> row : store.list(c, "select * from oauth_client where enabled=?", true)) page.clients.add(client(c, (String) row.get("clientId")));
            page.grants = new ArrayList<>();
            for (Map<String,Object> row : store.list(c, "select g.*,c.name,c.enabled as clientEnabled from oauth_grant g inner join oauth_client c on c.clientId=g.clientId where g.userId=? order by g.createdAt desc", account.getUserId())) {
                Grant g = new Grant(); g.id=(String)row.get("id"); g.userId=account.getUserId(); g.clientId=(String)row.get("clientId");
                g.clientName=(String)row.get("name"); g.scope=(String)row.get("scope"); g.resource=(String)row.get("resource");
                g.createdAt=((Number)row.get("createdAt")).longValue();
                g.revoked=AccountAccess.truth(row.get("revoked")) || !AccountAccess.truth(row.get("clientEnabled"))
                        || ((Number)row.get("authVersion")).intValue() != account.getAuthVersion();
                page.grants.add(g);
            }
            return page;
        });
    }
}
