package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.model.WebSite;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AIConversationServiceDatabaseTest {

    @Test
    public void shouldIsolateAccountsAndArticlesAndCaptureIdentityForAsyncWrites() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AIConversationService current =
                    new AIConversationService(
                            AccountServicesFixture.component(db.dataSource()).security(),
                            ApplicationServicesFixture.websiteKv(db.dataSource()),
                            () -> new WebSite(db.dataSource()),
                            new AIFileService(),
                            AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()));
            AIConversationService firstAccount = current.captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()));
            assertTrue(current.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).saveAIMessage(List.of(new AIResponseEntry.AIContentEntry("user", "first account")), 7L));
            AdminTokenThreadLocal.getUser().setUserId(2);
            assertTrue(current.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).getAiMessageInfoByArticleId(7L).getAiMessages().isEmpty());
            assertTrue(current.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).saveAIMessage(List.of(new AIResponseEntry.AIContentEntry("user", "second account")), 7L));
            CompletableFuture.runAsync(
                            () -> {
                                try {
                                    assertTrue(
                                            firstAccount.appendAIMessageEntries(
                                                    List.of(
                                                            new AIResponseEntry.AIContentEntry(
                                                                    "assistant", "async reply")),
                                                    7L));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .get();
            assertEquals("second account", current.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).exportAIMessage(7L).getMessages().get(0).getContent());
            assertTrue(current.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).getAiMessageInfoByArticleId(8L).getAiMessages().isEmpty());
            AdminTokenThreadLocal.getUser().setUserId(1);
            assertEquals(
                    List.of("first account", "async reply"),
                    new AIConversationService(
                                    AccountServicesFixture.component(db.dataSource()).security(),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource()),
                                    new AIFileService(),
                                    AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()))
                            .exportAIMessage(7L).getMessages().stream()
                                    .filter(entry -> !"system".equals(entry.getRole()))
                                    .map(AIResponseEntry.AIContentEntry::getContent)
                                    .collect(Collectors.toList()));
        }
    }

    @Test
    public void shouldReadLegacyHistoryAndKeepClearedHistoryEmpty() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            String legacy = "[{\"role\":\"user\",\"content\":\"old conversation\"}]";
            db.putWebsite("ai_chat_message_7", legacy);
            AIConversationService service =
                    new AIConversationService(
                            AccountServicesFixture.component(db.dataSource()).security(),
                            ApplicationServicesFixture.websiteKv(db.dataSource()),
                            () -> new WebSite(db.dataSource()),
                            new AIFileService(),
                            AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()));
            assertEquals("old conversation", service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).exportAIMessage(7L).getMessages().get(0).getContent());
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).appendAIMessageEntries(List.of(new AIResponseEntry.AIContentEntry("assistant", "new reply")), 7L));
            assertEquals(2, service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).exportAIMessage(7L).getMessages().stream().filter(entry -> !"system".equals(entry.getRole())).count());
            assertEquals(legacy, db.scalar("select value from website where name=?", "ai_chat_message_7"));
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).clearAIMessage(7L));
            assertTrue(
                    new AIConversationService(
                                    AccountServicesFixture.component(db.dataSource()).security(),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource()),
                                    new AIFileService(),
                                    AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()))
                            .exportAIMessage(7L)
                            .getMessages()
                            .isEmpty());
            db.putWebsite("ai_chat_message_-1", legacy);
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).migrateDraftAIMessageToArticle(8L, -1L));
            assertEquals("old conversation", service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).exportAIMessage(8L).getMessages().get(0).getContent());
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).exportAIMessage(-1L).getMessages().isEmpty());
        }
    }

    @Test
    public void shouldNotWriteSharedHistoryWithoutAnAccount() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AdminTokenThreadLocal.remove();
            org.junit.Assert.assertThrows(
                    PermissionErrorException.class,
                    () ->
                            new AIConversationService(
                                            AccountServicesFixture.component(db.dataSource()).security(),
                                            ApplicationServicesFixture.websiteKv(db.dataSource()),
                                            () -> new WebSite(db.dataSource()),
                                            new AIFileService(),
                                            AIServiceDependencies.approvals(db.dataSource()))
                                    .saveAIMessage(
                                            List.of(
                                                    new AIResponseEntry.AIContentEntry(
                                                            "user", "private")),
                                            7L));
            assertEquals(0L, ((Number) db.scalar("select count(*) from website where name like 'ai_chat_message_%'")).longValue());
        }
    }

    @Test
    public void shouldPersistAiMessagesThroughWebsiteKvTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("ai_provider", "OPEN_AI");
            db.putWebsite("ai_model", "gpt-test");
            db.putWebsite("ai_api_key", "key");
            db.putWebsite("ai_prompt", "system");
            AIConversationService service =
                    new AIConversationService(
                            AccountServicesFixture.component(db.dataSource()).security(),
                            ApplicationServicesFixture.websiteKv(db.dataSource()),
                            () -> new WebSite(db.dataSource()),
                            new AIFileService(),
                            AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()));
            List<AIResponseEntry.AIContentEntry> messages =
                    List.of(new AIResponseEntry.AIContentEntry("user", "hello"));

            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).saveAIMessage(messages, 7L));
            AIWebSiteInfoWithAIMessages info = service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).getAiMessageInfoByArticleId(7L);

            assertEquals("OPEN_AI", info.getAi_provider().name());
            assertEquals("gpt-test", info.getAi_model());
            assertEquals(1, info.getAiMessages().size());
            assertEquals("user", info.getAiMessages().get(0).getRole());
            assertFalse(info.getAiMessages().get(0).getMessageId().isEmpty());
            assertEquals(1L, ((Number) db.scalar("select count(1) from website where name=?", "ai_chat_message_u1_7")).longValue());
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).clearAIMessage(7L));
            assertEquals("[]", db.queryOne("select value from website where name=?", "ai_chat_message_u1_7").get("value"));
        }
    }

    @Test
    public void shouldAppendAiMessagesWithoutOverwritingNewerEntriesAndDeduplicateMessageIds() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("ai_prompt", "system prompt");
            AIConversationService service =
                    new AIConversationService(
                            AccountServicesFixture.component(db.dataSource()).security(),
                            ApplicationServicesFixture.websiteKv(db.dataSource()),
                            () -> new WebSite(db.dataSource()),
                            new AIFileService(),
                            AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()));
            AIResponseEntry.AIContentEntry initial = new AIResponseEntry.AIContentEntry("user", "initial");
            initial.setMessageId("initial-id");
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).saveAIMessage(List.of(initial), 8L));

            AIResponseEntry.AIContentEntry newer = new AIResponseEntry.AIContentEntry("user", "newer");
            newer.setMessageId("newer-id");
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).appendAIMessageEntries(List.of(newer), 8L));

            AIResponseEntry.AIContentEntry late = new AIResponseEntry.AIContentEntry("assistant", "late response");
            late.setMessageId("late-id");
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).appendAIMessageEntries(List.of(late), 8L));
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).appendAIMessageEntries(List.of(late), 8L));

            List<AIResponseEntry.AIContentEntry> stored = service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).getAiMessageInfoByArticleId(8L).getAiMessages();
            assertEquals(4, stored.size());
            assertEquals("system", stored.get(0).getRole());
            assertEquals("initial", stored.get(1).getContent());
            assertEquals("newer", stored.get(2).getContent());
            assertEquals("late response", stored.get(3).getContent());
        }
    }

    @Test
    public void shouldMigrateDraftAiMessagesAndRejectOtherAccountsDrafts() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AIConversationService service =
                    new AIConversationService(
                            AccountServicesFixture.component(db.dataSource()).security(),
                            ApplicationServicesFixture.websiteKv(db.dataSource()),
                            () -> new WebSite(db.dataSource()),
                            new AIFileService(),
                            AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()));
            List<AIResponseEntry.AIContentEntry> messages =
                    List.of(new AIResponseEntry.AIContentEntry("user", "draft"));
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).saveAIMessage(messages, 0L));

            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).migrateDraftAIMessageToArticle(12L));
            assertEquals(1, service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).getAiMessageInfoByArticleId(12L).getAiMessages().size());
            assertEquals(0, service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).getAiMessageInfoByArticleId(0L).getAiMessages().size());
            assertFalse(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).migrateDraftAIMessageToArticle(0L));
            assertFalse(service.removeAIMessage(0L));
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).saveAIMessage(messages, -1L));
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).saveAIMessage(messages, -2L));
            assertTrue(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).clearAIMessage(-1L));
            assertEquals(0, service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).getAiMessageInfoByArticleId(-1L).getAiMessages().size());
            assertFalse(service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).clearAIMessage(-2L));
            assertEquals(1, service.captureAccount(java.util.Objects.requireNonNullElse(com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId(), 0)).getAiMessageInfoByArticleId(-2L).getAiMessages().size());
        }
    }
}
