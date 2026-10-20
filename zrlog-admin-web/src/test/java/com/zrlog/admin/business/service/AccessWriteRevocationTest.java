package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.security.GrantAuthority;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.access.AccessComponent;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccessWriteRevocationTest {
    @Test
    public void webApiStatementRechecksAccountBeforeIssuingOrApprovingCredentials() throws Exception {
        for (String operation : List.of("personal-token", "client", "consent", "decision", "grant")) {
            for (String revocation : List.of("enabled=false", "role='author'", "authVersion=1")) {
                try (var db = InMemoryZrLogDatabase.openWebApi()) {
                    db.execute("insert into user(userId,userName,role) values(2,'operator','admin')");
                    db.loadAdminWebModules();
                    var accounts = db.component(AccountComponent.class);
                    var oauth = db.component(AccessComponent.class).oauth();
                    var actor = AccessActorBoundaryTest.actor(db, 2);
                    var authority = new GrantAuthority(actor, true);
                    // This isolated service shares the application's real account graph and storage.
                    var personalTokens = new PersonalAccessTokenService(
                            oauth.mcpResource(), accounts.identities(), accounts.permissions(), accounts.security());
                    var client = oauth.register(actor, AccessActorBoundaryTest.client());
                    var request = AccessActorBoundaryTest.request(oauth, client, "article.read");
                    String pending = AccessActorBoundaryTest.parameters(oauth.authorize(request)).get("request_id");
                    var consent = oauth.consent(authority, "session", pending);
                    AdminTokenThreadLocal.remove();
                    var input = new PersonalTokenModels.Create();
                    input.name = "Explicit credential";
                    input.permissionMode = "custom";
                    input.permissions = List.of("article.read");
                    String prefix;
                    switch (operation) {
                        case "personal-token": prefix = "insert into user_access_token"; break;
                        case "client": prefix = "insert into oauth_client"; break;
                        case "consent": prefix = "update oauth_credential set payload=?"; break;
                        case "decision": prefix = "update oauth_credential set used=?"; break;
                        case "grant": prefix = "insert into oauth_grant"; break;
                        default: throw new AssertionError(operation);
                    }
                    var revoked = new AtomicBoolean();
                    db.beforeWebApiUpdate((sql, params) -> {
                        if (sql.startsWith(prefix) && revoked.compareAndSet(false, true)) {
                            db.execute("update user set " + revocation + " where userId=2");
                        }
                    });
                    assertThrows(operation + ": " + revocation, AbstractAdminBusinessException.class, () -> {
                        switch (operation) {
                            case "personal-token": personalTokens.create(authority, input); break;
                            case "client": oauth.register(actor, AccessActorBoundaryTest.client()); break;
                            case "consent": oauth.consent(authority, "session", pending); break;
                            case "decision":
                            case "grant": oauth.decide(authority, "session", AccessActorBoundaryTest.decision(consent)); break;
                            default: throw new AssertionError(operation);
                        }
                    });
                    assertTrue(revoked.get());
                    assertEquals(0, ((Number) db.scalar("select count(*) from user_access_token")).intValue());
                    assertEquals(1, ((Number) db.scalar("select count(*) from oauth_client")).intValue());
                    assertEquals(0, ((Number) db.scalar("select count(*) from oauth_grant")).intValue());
                    assertEquals(0, ((Number) db.scalar("select count(*) from oauth_credential where kind='code'")).intValue());
                    assertEquals("grant".equals(operation) ? 1 : 0,
                            ((Number) db.scalar("select used from oauth_credential where kind='pending'")).intValue());
                    assertNull(AdminTokenThreadLocal.getUser());
                }
            }
        }
    }
}
