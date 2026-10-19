package com.zrlog.admin.web.di;

import static org.junit.Assert.*;

import com.hibegin.common.dao.DAO;
import com.zrlog.admin.business.ai.service.AIConversationService;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry;
import com.zrlog.admin.business.service.AdminDashboardService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.ai.AIComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.service.WebsiteKvService;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AdminComponentTest {
    @Test
    public void independentGraphsKeepChecklistChangesOnTheirOwnDatabase() throws Exception {
        try (InMemoryZrLogDatabase first = InMemoryZrLogDatabase.open()) {
            AdminComponent firstGraph = graph(first);
            new WebsiteKvService(first.dataSource())
                    .putString(AdminDashboardService.FIRST_USE_CHECKLIST_KEY, "pending");
            try (InMemoryZrLogDatabase second = InMemoryZrLogDatabase.open()) {
                AdminComponent secondGraph = graph(second);
                DAO.setDs(null);
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
            DAO.setDs(null);
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
