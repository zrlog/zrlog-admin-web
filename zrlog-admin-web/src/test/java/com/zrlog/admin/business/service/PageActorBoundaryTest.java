package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.ui.AdminUiComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.plugin.BaseStaticSitePlugin;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Proxy;
import java.util.Set;

@RunWith(Parameterized.class)
public class PageActorBoundaryTest {
    @Parameterized.Parameters(name = "sqlite={0}")
    public static Boolean[] databases() { return new Boolean[]{false, true}; }
    private final boolean sqlite;
    public PageActorBoundaryTest(boolean sqlite) { this.sqlite = sqlite; }

    @Test
    public void rendererUsesExplicitActorSessionAndPreferencesWithoutThreadIdentity() throws Exception {
        try (var db = database()) {
            var actor = AccessActorBoundaryTest.actor(db, 2);
            var accounts = db.component(AccountComponent.class);
            accounts.preferences().updateBody(actor, "{\"language\":\"en_US\",\"appearance\":{\"darkMode\":true}}");
            var renderer = db.component(AdminUiComponent.class).pages();
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            var page = renderer.serverSide(actor, "writer-session", "/admin/index", request(false), "writer payload", "Writer title");
            assertEquals(Integer.valueOf(2), page.getUser().getUserId());
            assertEquals("writer", page.getUser().getUserName());
            assertEquals("writer-session", page.getKey());
            assertEquals("writer payload", page.getData());
            assertEquals("Writer title", page.getDocumentTitle());
            assertEquals(Boolean.TRUE, page.getResourceInfo().getAdmin_darkMode());
            AdminTokenThreadLocal.remove();
            assertEquals(Integer.valueOf(2), renderer.serverSide(actor, "worker-session", "/admin/index",
                    request(false), null, "Writer").getUser().getUserId());
            assertNull(AdminTokenThreadLocal.getUser());
            for (String revocation : new String[]{"enabled=false", "role='editor'", "authVersion=1"}) {
                db.execute("update user set enabled=true,role='author',authVersion=0 where userId=2");
                db.execute("update user set " + revocation + " where userId=2");
                assertThrows(PermissionErrorException.class, () -> renderer.serverSide(actor, "session", "/admin/index", request(false), null, "Writer"));
            }
        }
    }

    @Test
    public void publicStaticAndDelegatedMetadataCannotExposeAdministratorStatus() throws Exception {
        try (var db = database()) {
            var actor = AccessActorBoundaryTest.actor(db, 1);
            var pages = db.component(AccountComponent.class).pages();
            assertTrue(pages.metadata(actor, "/admin/index").getMessageCenter().isHasUnread());
            var limited = actor.restrictActions(Set.of("account.self"));
            assertFalse(pages.metadata(limited, "/admin/index").getMessageCenter().isHasUnread());
            AdminTokenThreadLocal.remove();
            assertTrue(pages.metadata(actor, "/admin/index").getMessageCenter().isHasUnread());
            assertFalse(pages.metadata(null, "/admin/index").getMessageCenter().isHasUnread());
            var renderer = db.component(AdminUiComponent.class).pages();
            var anonymous = renderer.serverSide(null, "should be ignored", "/admin/index", request(false), "private payload", "Private title");
            var generated = renderer.serverSide(actor, "should be ignored", "/admin/index", request(true), "private payload", "Private title");
            for (var response : new com.zrlog.admin.business.rest.response.ServerSideDataResponse<?>[]{anonymous, generated}) {
                assertNull(response.getUser());
                assertNull(response.getKey());
                assertNull(response.getData());
                assertFalse(response.getMessageCenter().isHasUnread());
                assertEquals(0, response.getMessageCenter().getRevision());
            }
        }
    }

    private InMemoryZrLogDatabase database() throws Exception {
        var db = sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
        try {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            db.putWebsite(WebsiteCacheService.KEY_PREFIX + "message_center_status", "{\"revision\":5,\"hasUnread\":true}");
            db.loadAdminWebModules();
            return db;
        } catch (Exception | Error error) {
            db.close();
            throw error;
        }
    }

    private static HttpRequest request(boolean generated) {
        return (HttpRequest) Proxy.newProxyInstance(PageActorBoundaryTest.class.getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUri": return "/admin/index";
                        case "getContextPath": return "/sub";
                        case "getScheme": return "http";
                        case "getHeader": return "Host".equals(args[0]) ? "localhost:18080"
                                : "User-Agent".equals(args[0]) && generated ? BaseStaticSitePlugin.STATIC_USER_AGENT : null;
                        default: return null;
                    }
                });
    }
}
