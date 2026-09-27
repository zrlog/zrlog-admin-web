package com.zrlog.admin.business.ai.service;


import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.request.AddArticleAIContextRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AIConversationServiceDatabaseTest {

    @Test
    public void shouldIsolateAccountsAndArticlesAndCaptureIdentityForAsyncWrites() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AIConversationService current = new AIConversationService();
            AIConversationService firstAccount = current.captureAccount();
            assertTrue(current.saveAIMessage(List.of(new AIResponseEntry.AIContentEntry("user", "first account")), 7L));
            com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser().setUserId(2);
            assertTrue(current.getAiMessageInfoByArticleId(7L).getAiMessages().isEmpty());
            assertTrue(current.saveAIMessage(List.of(new AIResponseEntry.AIContentEntry("user", "second account")), 7L));
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    assertTrue(firstAccount.appendAIMessageEntries(List.of(new AIResponseEntry.AIContentEntry("assistant", "async reply")), 7L));
                } catch (Exception e) { throw new RuntimeException(e); }
            }).get();
            assertEquals("second account", current.exportAIMessage(7L).getMessages().get(0).getContent());
            assertTrue(current.getAiMessageInfoByArticleId(8L).getAiMessages().isEmpty());
            com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser().setUserId(1);
            assertEquals(List.of("first account", "async reply"), new AIConversationService().exportAIMessage(7L).getMessages().stream()
                    .filter(entry -> !"system".equals(entry.getRole())).map(AIResponseEntry.AIContentEntry::getContent).collect(java.util.stream.Collectors.toList()));
        }
    }

    @Test
    public void shouldReadLegacyHistoryAndKeepClearedHistoryEmpty() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            String legacy = "[{\"role\":\"user\",\"content\":\"old conversation\"}]";
            db.putWebsite("ai_chat_message_7", legacy);
            AIConversationService service = new AIConversationService();
            assertEquals("old conversation", service.exportAIMessage(7L).getMessages().get(0).getContent());
            assertTrue(service.appendAIMessageEntries(List.of(new AIResponseEntry.AIContentEntry("assistant", "new reply")), 7L));
            assertEquals(2, service.exportAIMessage(7L).getMessages().stream().filter(entry -> !"system".equals(entry.getRole())).count());
            assertEquals(legacy, db.scalar("select value from website where name=?", "ai_chat_message_7"));
            assertTrue(service.clearAIMessage(7L));
            assertTrue(new AIConversationService().exportAIMessage(7L).getMessages().isEmpty());
            db.putWebsite("ai_chat_message_-1", legacy);
            assertTrue(service.migrateDraftAIMessageToArticle(8L, -1L));
            assertEquals("old conversation", service.exportAIMessage(8L).getMessages().get(0).getContent());
            assertTrue(service.exportAIMessage(-1L).getMessages().isEmpty());
        }
    }

    @Test
    public void shouldNotWriteSharedHistoryWithoutAnAccount() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            com.zrlog.admin.web.token.AdminTokenThreadLocal.remove();
            org.junit.Assert.assertThrows(com.zrlog.admin.business.exception.PermissionErrorException.class,
                    () -> new AIConversationService().saveAIMessage(List.of(new AIResponseEntry.AIContentEntry("user", "private")), 7L));
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
            AIConversationService service = new AIConversationService();
            List<AIResponseEntry.AIContentEntry> messages =
                    List.of(new AIResponseEntry.AIContentEntry("user", "hello"));

            assertTrue(service.saveAIMessage(messages, 7L));
            AIWebSiteInfoWithAIMessages info = service.getAiMessageInfoByArticleId(7L);

            assertEquals("OPEN_AI", info.getAi_provider().name());
            assertEquals("gpt-test", info.getAi_model());
            assertEquals(1, info.getAiMessages().size());
            assertEquals("user", info.getAiMessages().get(0).getRole());
            assertFalse(info.getAiMessages().get(0).getMessageId().isEmpty());
            assertEquals(1L, ((Number) db.scalar("select count(1) from website where name=?", "ai_chat_message_u1_7")).longValue());
            assertTrue(service.clearAIMessage(7L));
            assertEquals("[]", db.queryOne("select value from website where name=?", "ai_chat_message_u1_7").get("value"));
        }
    }

    @Test
    public void shouldAppendAiMessagesWithoutOverwritingNewerEntriesAndDeduplicateMessageIds() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("ai_prompt", "system prompt");
            AIConversationService service = new AIConversationService();
            AIResponseEntry.AIContentEntry initial = new AIResponseEntry.AIContentEntry("user", "initial");
            initial.setMessageId("initial-id");
            assertTrue(service.saveAIMessage(List.of(initial), 8L));

            AIResponseEntry.AIContentEntry newer = new AIResponseEntry.AIContentEntry("user", "newer");
            newer.setMessageId("newer-id");
            assertTrue(service.appendAIMessageEntries(List.of(newer), 8L));

            AIResponseEntry.AIContentEntry late = new AIResponseEntry.AIContentEntry("assistant", "late response");
            late.setMessageId("late-id");
            assertTrue(service.appendAIMessageEntries(List.of(late), 8L));
            assertTrue(service.appendAIMessageEntries(List.of(late), 8L));

            List<AIResponseEntry.AIContentEntry> stored = service.getAiMessageInfoByArticleId(8L).getAiMessages();
            assertEquals(4, stored.size());
            assertEquals("system", stored.get(0).getRole());
            assertEquals("initial", stored.get(1).getContent());
            assertEquals("newer", stored.get(2).getContent());
            assertEquals("late response", stored.get(3).getContent());
        }
    }

    @Test
    public void shouldAppendArticleContextToPersistedAiMessages() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("ai_prompt", "system prompt");
            AIConversationService service = new AIConversationService();
            AddArticleAIContextRequest context = new AddArticleAIContextRequest();
            context.setTitle("Article");
            context.setMarkdown("Markdown");
            context.setArticleVersion(2);

            List<AIResponseEntry.AIContentEntry> messages = service.appendArticleContextMessage(9L, context);
            Map<String, Object> row = db.queryOne("select value from website where name=?", "ai_chat_message_u1_9");

            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).getRole());
            assertEquals("user", messages.get(1).getRole());
            assertEquals("articleContext", messages.get(1).getMessageType());
            assertTrue(String.valueOf(row.get("value")).contains("Article context snapshot."));
        }
    }

    @Test
    public void shouldMigrateDraftAiMessagesAndRejectOtherAccountsDrafts() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AIConversationService service = new AIConversationService();
            List<AIResponseEntry.AIContentEntry> messages =
                    List.of(new AIResponseEntry.AIContentEntry("user", "draft"));
            assertTrue(service.saveAIMessage(messages, 0L));

            assertTrue(service.migrateDraftAIMessageToArticle(12L));
            assertEquals(1, service.getAiMessageInfoByArticleId(12L).getAiMessages().size());
            assertEquals(0, service.getAiMessageInfoByArticleId(0L).getAiMessages().size());
            assertFalse(service.migrateDraftAIMessageToArticle(0L));
            assertFalse(service.removeAIMessage(0L));
            assertTrue(service.saveAIMessage(messages, -1L));
            assertTrue(service.saveAIMessage(messages, -2L));
            assertTrue(service.clearAIMessage(-1L));
            assertEquals(0, service.getAiMessageInfoByArticleId(-1L).getAiMessages().size());
            assertFalse(service.clearAIMessage(-2L));
            assertEquals(1, service.getAiMessageInfoByArticleId(-2L).getAiMessages().size());
        }
    }
}
