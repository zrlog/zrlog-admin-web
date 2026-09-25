package com.zrlog.admin.business.service;

import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import org.junit.Test;
import java.net.URI;
import java.util.*;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class OAuthServiceTest {
    @org.junit.runners.Parameterized.Parameters(name="database={0}")
    public static String[] databases() { return new String[]{"h2", "sqlite", "webapi"}; }
    private final String backend;
    public OAuthServiceTest(String backend) { this.backend = backend; }
    private InMemoryZrLogDatabase database() throws Exception { return "webapi".equals(backend) ? InMemoryZrLogDatabase.openWebApi() : "sqlite".equals(backend) ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open(); }
    private final OAuthService service=new OAuthService(()->"https://blog.example/sub");
    private final String verifier=OAuthService.random();
    private Client client() throws Exception {
        Client app=new Client();app.name="Test writer";app.redirectUris=List.of("http://127.0.0.1:3000/callback?app=1");return service.register(app);
    }
    private AuthorizationRequest request(Client app,String scopes) {
        AuthorizationRequest r=new AuthorizationRequest();r.client_id=app.clientId;r.redirect_uri=app.redirectUris.get(0);
        r.response_type="code";r.scope=scopes;r.resource=service.resource();r.state="state & 中文";r.code_challenge=OAuthService.hash(verifier);r.code_challenge_method="S256";return r;
    }
    private Consent consent(AuthorizationRequest r) throws Exception {
        URI page=URI.create(service.authorize(r));
        assertEquals("/sub/admin/user/applications/authorize",page.getPath());
        String id=OAuthInterceptor.parameters(page.getRawQuery()).get("request_id");return service.consent(id);
    }
    private Decision decision(Consent c) { Decision d=new Decision();d.requestId=c.requestId;d.csrf=c.csrf;d.approve=true;d.scopes=c.availableScopes;return d; }
    private TokenRequest code(Client app,String scopes) throws Exception {
        Redirect redirect=service.decide(decision(consent(request(app,scopes))));
        Map<String,String> p=OAuthInterceptor.parameters(URI.create(redirect.redirectUri).getRawQuery());
        assertEquals("state & 中文",p.get("state"));assertEquals(service.issuer(),p.get("iss"));
        TokenRequest t=new TokenRequest();t.grant_type="authorization_code";t.client_id=app.clientId;t.redirect_uri=app.redirectUris.get(0);t.code=p.get("code");t.code_verifier=verifier;t.resource=service.resource();return t;
    }
    @Test public void mcpAudienceHasReadOnlyScopesAndCannotCrossUseLegacyTokens() throws Exception {
        try(InMemoryZrLogDatabase db=database()) {
            Client app=client();
            AuthorizationRequest r=request(app,"articles:read articles:write"); r.resource=service.mcpResource();
            assertEquals("invalid_scope",assertThrows(OAuthException.class,()->service.authorize(r)).getOAuthError());
            r.scope="articles:read articles:read_private offline_access";
            Redirect redirect=service.decide(decision(consent(r)));
            TokenRequest t=new TokenRequest(); t.grant_type="authorization_code";t.client_id=app.clientId;t.redirect_uri=r.redirect_uri;t.code_verifier=verifier;t.resource=service.mcpResource();
            t.code=OAuthInterceptor.parameters(URI.create(redirect.redirectUri).getRawQuery()).get("code");
            TokenResponse token=service.token(t);
            assertEquals(1,service.authenticate(token.access_token,service.mcpResource(),Set.of("articles:read")).userId);
            assertThrows(OAuthException.class,()->service.authenticate(token.access_token,service.resource(),Set.of("articles:read")));
            TokenResponse legacy=service.token(code(app,"articles:read"));
            assertThrows(OAuthException.class,()->service.authenticate(legacy.access_token,service.mcpResource(),Set.of("articles:read")));
            assertEquals("https://blog.example/.well-known/oauth-protected-resource/sub/mcp",service.mcpResourceMetadataUrl());
            assertFalse(service.mcpResourceMetadata().scopes_supported.contains("articles:write"));
            service.revoke(token.access_token,app.clientId);
            assertThrows(OAuthException.class,()->service.authenticate(token.access_token,service.mcpResource(),Set.of("articles:read")));
        }
    }
    @Test public void authorizationRequiresExactRedirectPkceAudienceAndKnownScopes() throws Exception {
        try(InMemoryZrLogDatabase db=database()) {
            Client app=client();AuthorizationRequest r=request(app,"articles:read");
            r.redirect_uri+="&extra=1";assertThrows(OAuthException.class,()->service.authorize(r));
            r.redirect_uri=app.redirectUris.get(0);r.code_challenge_method="plain";assertThrows(OAuthException.class,()->service.authorize(r));
            r.code_challenge_method="S256";r.resource="https://evil.example";assertThrows(OAuthException.class,()->service.authorize(r));
            r.resource=service.resource();r.scope="site:admin";assertThrows(OAuthException.class,()->service.authorize(r));
            assertThrows(OAuthException.class,()->service.requireSameOrigin("https://evil.example"));
            service.requireSameOrigin("https://blog.example");
            assertEquals("https://blog.example/.well-known/oauth-protected-resource/sub/api/oauth",service.resourceMetadataUrl());
            assertThrows(OAuthException.class,()->OAuthInterceptor.parameters("code=one&code=two"));
        }
    }
    @Test public void consentIsBoundToAccountSessionCsrfAndRequestedSubset() throws Exception {
        try(InMemoryZrLogDatabase db=database()) {
            Client app=client();Consent c=consent(request(app,"articles:read articles:read_private"));Decision d=decision(c);
            d.csrf="wrong";assertThrows(OAuthException.class,()->service.decide(d));d.csrf=c.csrf;
            d.scopes=List.of("articles:read","articles:publish");assertThrows(OAuthException.class,()->service.decide(d));
            db.execute("insert into user(userId,userName,role) values(?,?,?)",2,"other","author");AccountAuthorizationTest.login(db,2,"author");
            assertThrows(OAuthException.class,()->service.consent(c.requestId));assertThrows(OAuthException.class,()->service.decide(d));
            AccountAuthorizationTest.login(db,1,"admin");d.approve=false;
            assertTrue(service.decide(d).redirectUri.contains("error=access_denied"));assertThrows(OAuthException.class,()->service.decide(d));
            AccountAuthorizationTest.login(db,2,"contributor");Consent limited=consent(request(app,"articles:read articles:all articles:publish"));
            assertEquals(List.of("articles:read"),limited.availableScopes);
        }
    }
    @Test public void exchangesStoreOnlyHashesAndCodeReuseRevokesIssuedTokens() throws Exception {
        try(InMemoryZrLogDatabase db=database()) {
            Client app=client();TokenRequest t=code(app,"articles:read offline_access");
            String good=t.code_verifier;t.code_verifier=OAuthService.random();assertThrows(OAuthException.class,()->service.token(t));t.code_verifier=good;
            TokenResponse token=service.token(t);assertNotNull(token.refresh_token);
            assertEquals(1,service.authenticate(token.access_token,service.resource(),Set.of("articles:read")).userId);
            assertEquals(0,((Number)db.scalar("select count(*) from oauth_credential where hash=?",token.access_token)).intValue());
            assertThrows(OAuthException.class,()->service.authenticate(token.access_token,"https://wrong.example",Set.of()));
            assertEquals(403,assertThrows(OAuthException.class,()->service.authenticate(token.access_token,service.resource(),Set.of("articles:write"))).getStatus());
            assertThrows(OAuthException.class,()->service.token(t));
            assertThrows(OAuthException.class,()->service.authenticate(token.access_token,service.resource(),Set.of()));
        }
    }
    @Test public void rotatedRefreshReuseRevokesEntireGrantAndScopeCannotExpand() throws Exception {
        try(InMemoryZrLogDatabase db=database()) {
            Client app=client();TokenResponse token=service.token(code(app,"articles:read articles:write offline_access"));
            TokenRequest refresh=new TokenRequest();refresh.grant_type="refresh_token";refresh.client_id=app.clientId;refresh.resource=service.resource();refresh.refresh_token=token.refresh_token;refresh.scope="articles:publish";
            assertThrows(OAuthException.class,()->service.token(refresh));refresh.scope="articles:read";TokenResponse next=service.token(refresh);
            assertNotEquals(token.refresh_token,next.refresh_token);assertEquals("articles:read",next.scope);
            assertThrows(OAuthException.class,()->service.token(refresh));assertThrows(OAuthException.class,()->service.authenticate(next.access_token,service.resource(),Set.of()));
        }
    }
    @Test public void revokeDisableAndAccountVersionChangeInvalidateAccessImmediately() throws Exception {
        try(InMemoryZrLogDatabase db=database()) {
            Client app=client();TokenResponse one=service.token(code(app,"articles:read"));
            service.revoke(one.access_token,"wrong-client");service.authenticate(one.access_token,service.resource(),Set.of());
            service.revoke(one.access_token,app.clientId);assertThrows(OAuthException.class,()->service.authenticate(one.access_token,service.resource(),Set.of()));
            TokenResponse two=service.token(code(app,"articles:read"));db.execute("update user set authVersion=authVersion+1 where userId=1");
            assertThrows(OAuthException.class,()->service.authenticate(two.access_token,service.resource(),Set.of()));
            AccountAuthorizationTest.login(db,1,"admin");assertTrue(service.page().grants.stream().allMatch(g -> g.revoked));
            TokenResponse three=service.token(code(app,"articles:read"));service.disableClient(app.clientId);
            assertThrows(OAuthException.class,()->service.authenticate(three.access_token,service.resource(),Set.of()));
        }
    }
    @Test public void expiredAndDisabledCredentialsCannotBeUsedAndGrantsArePrivate() throws Exception {
        try(InMemoryZrLogDatabase db=database()) {
            Client app=client();TokenRequest t=code(app,"articles:read");
            db.execute("update oauth_credential set expiresAt=? where hash=?",1L,OAuthService.hash(t.code));
            assertThrows(OAuthException.class,()->service.token(t));
            TokenResponse valid=service.token(code(app,"articles:read"));String grantId=service.page().grants.get(0).id;
            db.execute("insert into user(userId,userName,role) values(?,?,?)",2,"other","author");
            AccountAuthorizationTest.login(db,2,"author");assertTrue(service.page().grants.isEmpty());
            service.revokeGrant(grantId);service.authenticate(valid.access_token,service.resource(),Set.of());
            db.execute("update user set enabled=? where userId=1",false);
            assertThrows(OAuthException.class,()->service.authenticate(valid.access_token,service.resource(),Set.of()));
        }
    }

    @Test public void simultaneousCodeExchangeNeverProducesTwoLiveGrants() throws Exception {
        try(InMemoryZrLogDatabase db=database()) {
            Client app=client();TokenRequest t=code(app,"articles:read");
            java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
            try {
                java.util.concurrent.Callable<TokenResponse> task=()->{start.await();try{return service.token(t);}catch(OAuthException e){return null;}};
                var a=pool.submit(task);var b=pool.submit(task);start.countDown();TokenResponse first=a.get(),second=b.get();
                assertTrue((first==null) != (second==null));TokenResponse issued=first==null?second:first;
                assertThrows(OAuthException.class,()->service.authenticate(issued.access_token,service.resource(),Set.of()));
            } finally {pool.shutdownNow();}
        }
    }
}
