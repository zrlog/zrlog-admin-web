package com.zrlog.admin.web.di;

import static org.junit.Assert.*;

import com.hibegin.common.dao.DAO;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.cookie.Cookie;
import com.zrlog.admin.business.ai.service.AIConversationService;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry;
import com.zrlog.admin.business.service.AdminDashboardService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.account.DaggerAccountComponent;
import com.zrlog.admin.web.di.ai.AIComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.di.DaggerServiceComponent;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.data.di.DaggerDataComponent;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class AdminComponentTest {
    @Test
    public void sharedTokensKeepAccountPreferencesAndApplicationSecretsIsolated() throws Exception {
        try (InMemoryZrLogDatabase first = InMemoryZrLogDatabase.open();
                InMemoryZrLogDatabase second = InMemoryZrLogDatabase.open()) {
            AccountComponent firstGraph = accountGraph(first);
            AccountComponent secondGraph = accountGraph(second);
            first.execute("update user set secretKey=?, preferences=? where userId=1",
                    "first-app-secret", "{\"session\":{\"timeoutMinutes\":11}}");
            first.execute("insert into user(userId,userName,role,secretKey) values(2,?,?,?)",
                    "author", "author", "author-secret");
            second.execute("update user set secretKey=? where userId=1", "second-app-secret");
            org.junit.Assert.assertNull(DAO.getDefaultDataSource());
            TokenService firstTokens = firstGraph.tokens();
            TokenService secondTokens = secondGraph.tokens();
            assertSame(firstTokens, firstGraph.tokens());
            assertSame(secondTokens, secondGraph.tokens());
            assertNotSame(firstTokens, secondTokens);

            firstTokens.updateSessionTimeout(19);
            secondTokens.updateSessionTimeout(31);
            Cookie owner = issue(firstTokens, 1, "first-app-secret");
            Cookie author = issue(firstTokens, 2, "author-secret");
            Cookie otherApp = issue(secondTokens, 1, "second-app-secret");
            assertSession(firstTokens, owner, 1, 11);
            assertSession(firstTokens, author, 2, 19);
            assertSession(secondTokens, otherApp, 1, 31);
            assertNull(firstTokens.getAdminTokenVO(tokenRequest(otherApp)));
            assertNull(secondTokens.getAdminTokenVO(tokenRequest(owner)));
            assertSession(firstTokens, owner, 1, 11);
            assertSession(secondTokens, otherApp, 1, 31);

            first.execute("update user set preferences=? where userId=1",
                    "{\"session\":{\"timeoutMinutes\":13}}");
            assertSession(firstGraph.tokens(),
                    issue(firstTokens, 1, "first-app-secret"), 1, 13);
            assertSession(firstTokens, author, 2, 19);
            first.execute("update user set authVersion=1 where userId=1");
            assertNull(firstTokens.getAdminTokenVO(tokenRequest(owner)));
            assertSession(secondTokens, otherApp, 1, 31);
        }
    }

    private static AccountComponent accountGraph(InMemoryZrLogDatabase database) {
        var data = DaggerDataComponent.factory().create(database::dataSource, () -> true);
        var services = DaggerServiceComponent.factory()
                .create(data, () -> true, () -> null, () -> null, Collections::emptyList);
        return DaggerAccountComponent.factory().create(
                data, services, () -> true, () -> null, Collections::emptySet,
                database.cacheService()::getPublicWebSiteInfo, () -> "test", () -> 18080, () -> "");
    }

    private static Cookie issue(TokenService tokens, int userId, String secret) throws Exception {
        AtomicReference<Cookie> cookie = new AtomicReference<>();
        HttpResponse response = (HttpResponse) Proxy.newProxyInstance(
                AdminComponentTest.class.getClassLoader(), new Class<?>[] {HttpResponse.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addCookie")) cookie.set((Cookie) args[0]);
                    return null;
                });
        tokens.setAdminToken(userId, secret, "session-" + userId, "http", tokenRequest(null), response);
        return cookie.get();
    }

    private static HttpRequest tokenRequest(Cookie cookie) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminComponentTest.class.getClassLoader(), new Class<?>[] {HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getCookies": return cookie == null ? new Cookie[0] : new Cookie[] {cookie};
                        case "getContextPath": return "";
                        case "getUri": return "/admin";
                        case "getHeader": return null;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static void assertSession(TokenService tokens, Cookie cookie, int userId, long minutes) {
        AdminFullTokenVO token = tokens.getAdminTokenVO(tokenRequest(cookie));
        assertNotNull(token);
        assertEquals(userId, token.getUserId());
        assertEquals("session-" + userId, token.getSessionId());
        assertEquals(minutes * 60000, token.getExpiresAt() - token.getCreatedDate());
    }

    @Test
    public void independentGraphsKeepChecklistChangesOnTheirOwnDatabase() throws Exception {
        try (InMemoryZrLogDatabase first = InMemoryZrLogDatabase.open()) {
            AdminComponent firstGraph = graph(first);
            new WebsiteKvService(first.dataSource())
                    .putString(AdminDashboardService.FIRST_USE_CHECKLIST_KEY, "pending");
            try (InMemoryZrLogDatabase second = InMemoryZrLogDatabase.open()) {
                AdminComponent secondGraph = graph(second);
                org.junit.Assert.assertNull(DAO.getDefaultDataSource());
                new WebsiteKvService(second.dataSource())
                        .putString(AdminDashboardService.FIRST_USE_CHECKLIST_KEY, "pending");

                assertEquals("pending", firstGraph.dashboard().getFirstUseChecklist().getStatus());
                assertEquals(
                        "dismissed",
                        firstGraph.dashboard().dismissFirstUseChecklist(1).getStatus());
                assertNull(firstGraph.dashboard().getFirstUseChecklist());
                assertEquals("pending", secondGraph.dashboard().getFirstUseChecklist().getStatus());
                assertEquals(
                        "dismissed",
                        new WebsiteKvService(first.dataSource())
                                .getString(AdminDashboardService.FIRST_USE_CHECKLIST_KEY));
                assertEquals(
                        "pending",
                        new WebsiteKvService(second.dataSource())
                                .getString(AdminDashboardService.FIRST_USE_CHECKLIST_KEY));

                // Sharing a service must not share the mutable request state of a Controller.
                assertSame(firstGraph.dashboard(), firstGraph.dashboard());
                assertNotSame(firstGraph.dashboard(), secondGraph.dashboard());
                assertNotSame(firstGraph.adminController(), firstGraph.adminController());
            }
        }
    }

    @Test
    public void permissionsAndAsyncConversationsRemainOnTheirApplicationDatabase()
            throws Exception {
        try (InMemoryZrLogDatabase first = InMemoryZrLogDatabase.open();
                InMemoryZrLogDatabase second = InMemoryZrLogDatabase.open()) {
            graph(first);
            graph(second);
            first.execute("update user set role='owner' where userId=1");
            second.execute("update user set role='author' where userId=1");
            var firstBindings = first.config().getServerConfig().getBindings().build();
            var secondBindings = second.config().getServerConfig().getBindings().build();
            org.junit.Assert.assertNull(DAO.getDefaultDataSource());
            var firstPermissions = firstBindings.get(AccountComponent.class).permissions();
            var secondPermissions = secondBindings.get(AccountComponent.class).permissions();
            assertTrue(firstPermissions.current().isAdministrator());
            assertFalse(secondPermissions.current().isAdministrator());
            second.execute("update user set authVersion=1 where userId=1");
            assertThrows(PermissionErrorException.class, secondPermissions::current);
            assertTrue(firstPermissions.current().isAdministrator());

            AIConversationService captured =
                    firstBindings.get(AIComponent.class).conversations().captureAccount();
            AdminTokenThreadLocal.getUser().setUserId(2);
            CompletableFuture.runAsync(
                            () -> {
                                assertNull(AdminTokenThreadLocal.getUser());
                                try {
                                    assertTrue(
                                            captured.appendAIMessageEntries(
                                                    List.of(
                                                            new AIContentEntry(
                                                                    "assistant", "App one answer")),
                                                    7L));
                                } catch (Exception error) {
                                    throw new RuntimeException(error);
                                }
                            })
                    .get();
            assertTrue(
                    first.scalar("select value from website where name='ai_chat_message_u1_7'")
                            .toString()
                            .contains("App one answer"));
            assertNull(first.scalar("select value from website where name='ai_chat_message_u2_7'"));
            assertNull(
                    second.scalar("select value from website where name='ai_chat_message_u1_7'"));
        }
    }

    private static AdminComponent graph(InMemoryZrLogDatabase database) {
        database.loadAdminWebModules();
        return database.config().getServerConfig().getBindings().build().get(AdminComponent.class);
    }
}
