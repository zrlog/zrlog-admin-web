package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Set;

@RunWith(Parameterized.class)
public class ContentToolIdentityTest {
    @Parameterized.Parameters(name = "sqlite={0}")
    public static Boolean[] databases() { return new Boolean[]{false, true}; }

    private final boolean sqlite;

    public ContentToolIdentityTest(boolean sqlite) { this.sqlite = sqlite; }

    @Test
    public void explicitToolActorKeepsOwnershipDelegationAndWriteTimeRevocation() throws Exception {
        try (var db = sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            db.execute("insert into log(logId,userId,typeId,title,privacy,rubbish) values(10,1,1,'Private',1,1)");
            db.loadAdminWebModules();
            var accounts = db.component(AccountComponent.class);
            var tools = db.component(ContentComponent.class).contentTools();
            var credentials = token(2);
            var author = accounts.identities().account(credentials);
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            assertThrows(PermissionErrorException.class, () -> tools.details(author, 10, author.scopes()));
            AdminTokenThreadLocal.remove();
            var input = JsonParser.parseString(
                    "{\"title\":\"Explicit draft\",\"typeId\":1,\"content\":\"<p>Draft body</p>\"}").getAsJsonObject();
            var source = new AdminAuditContext(2, "192.0.2.10", "/mcp", "test-client/1");
            var saved = tools.save(author, "create_article", input, author.scopes(), null, source);
            assertEquals("draft", saved.status);
            assertEquals(2, ((Number) db.scalar("select userId from log where logId=?", saved.id)).intValue());
            assertEquals("Explicit draft", tools.details(author, (int) saved.id, author.scopes()).title);
            var audit = JsonParser.parseString(db.scalar(
                    "select value from website where name='admin_audit_log'").toString()).getAsJsonArray();
            assertEquals(2, audit.get(0).getAsJsonObject().get("actorUserId").getAsInt());
            var readOnly = author.restrictActions(Set.of(AccountAction.ARTICLE_READ.getId()));
            assertThrows(PermissionErrorException.class,
                    () -> tools.save(readOnly, "create_article", input, author.scopes(), null, source));
            db.execute("update user set authVersion=1 where userId=2");
            assertThrows(PermissionErrorException.class,
                    () -> tools.save(author, "create_article", input, author.scopes(), null, source));
            assertEquals(1, ((Number) db.scalar("select count(*) from log where userId=2")).intValue());
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    private static AdminTokenVO token(int userId) {
        var token = new AdminTokenVO();
        token.setUserId(userId);
        return token;
    }
}
