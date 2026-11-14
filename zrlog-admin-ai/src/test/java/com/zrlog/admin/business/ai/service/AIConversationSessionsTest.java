package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.*;

import com.zrlog.admin.business.ai.model.AIChatModels;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AIConversationSessionsTest {
    @Parameterized.Parameters(name = "{0}")
    public static Object[] databases() {
        return new Object[] {"h2", "sqlite", "webapi"};
    }

    private final String database;

    public AIConversationSessionsTest(String database) {
        this.database = database;
    }

    private InMemoryZrLogDatabase open() throws Exception {
        return "sqlite".equals(database)
                ? InMemoryZrLogDatabase.openSqlite()
                : "webapi".equals(database)
                        ? InMemoryZrLogDatabase.openWebApi()
                        : InMemoryZrLogDatabase.open();
    }

    @Test
    public void conversationsAndCheckpointsAreIsolatedBySessionAndAccount() throws Exception {
        try (var db = open()) {
            var conversations =
                    AIServiceDependencies.conversations(db.dataSource()).captureAccount(1);
            String firstId = UUID.randomUUID().toString(), secondId = UUID.randomUUID().toString();
            var first = conversations.captureSession(firstId);
            var second = conversations.captureSession(secondId);
            first.appendAIMessageEntries(
                    List.of(new AIContentEntry("user", "First subject")), null);
            second.appendAIMessageEntries(
                    List.of(new AIContentEntry("user", "Second subject")), null);
            assertEquals(
                    "First subject", first.exportAIMessage(null).getMessages().get(0).getContent());
            assertEquals(
                    "Second subject",
                    second.exportAIMessage(null).getMessages().get(0).getContent());
            assertTrue(first.captureAccount(2).exportAIMessage(null).getMessages().isEmpty());
            assertTrue(first.exportAIMessage(7L).getMessages().isEmpty());
            assertTrue(conversations.exportAIMessage(null).getMessages().isEmpty());
            var run = new AIChatModels.Run();
            run.userId = 1;
            run.sessionId = firstId;
            var checkpoints = AIServiceDependencies.approvals(db.dataSource());
            checkpoints.begin(run);
            assertEquals(run.id, checkpoints.read(1, null, null, firstId).id);
            assertNull(checkpoints.read(1, null, null, secondId));
            assertNull(checkpoints.read(2, null, null, firstId));
            assertFalse(first.clearAIMessage(null));
            assertTrue(second.clearAIMessage(null));
            assertEquals(1, first.exportAIMessage(null).getMessageCount());
        }
    }

    @Test
    public void directoryIsBoundedSearchableAndDeletionDoesNotResurrectMessages() throws Exception {
        try (var db = open()) {
            var conversations =
                    AIServiceDependencies.conversations(db.dataSource()).captureAccount(1);
            var index = conversations.index();
            String firstId = UUID.randomUUID().toString();
            var first = conversations.captureSession(firstId);
            first.appendAIMessageEntries(
                    List.of(new AIContentEntry("user", "Initial title")), null);
            var rename = new AIChatModels.SessionChange();
            rename.sessionId = firstId;
            rename.title = "Renamed subject";
            index.rename(1, rename);
            first.appendAIMessageEntries(List.of(new AIContentEntry("assistant", "Reply")), null);
            assertEquals(
                    "Renamed subject",
                    index.list(1, null, false, "renamed", null, null).items.get(0).title);
            assertTrue(index.list(2, null, false, "", null, null).items.isEmpty());
            for (int i = 0; i < 23; i++)
                index.touch(1, null, null, UUID.randomUUID().toString(), "Other " + i, false);
            var page = index.list(1, null, false, "", null, null);
            assertEquals(20, page.items.size());
            assertTrue(page.hasMore);
            var older = index.list(1, null, false, "", page.before, page.beforeKey);
            assertEquals(4, older.items.size());
            assertFalse(older.hasMore);
            assertTrue(first.clearAIMessage(null));
            index.remove(1, null, null, firstId);
            assertTrue(index.list(1, null, false, "renamed", null, null).items.isEmpty());
            assertThrows(
                    PermissionErrorException.class,
                    () ->
                            first.appendAIMessageEntries(
                                    List.of(new AIContentEntry("assistant", "Late reply")), null));
            assertEquals(0, first.exportAIMessage(null).getMessageCount());
        }
    }

    @Test
    public void savingAnArticleKeepsEachWritingSessionSeparate() throws Exception {
        try (var db = open()) {
            var conversations =
                    AIServiceDependencies.conversations(db.dataSource()).captureAccount(1);
            String firstId = UUID.randomUUID().toString(), secondId = UUID.randomUUID().toString();
            conversations
                    .captureSession(firstId)
                    .appendAIMessageEntries(
                            List.of(new AIContentEntry("user", "First writing")), -1L);
            conversations
                    .captureSession(secondId)
                    .appendAIMessageEntries(
                            List.of(new AIContentEntry("user", "Second writing")), -1L);
            conversations.migrateDraftAIMessageToArticle(7L, -1L);
            assertEquals(
                    "First writing",
                    conversations
                            .captureSession(firstId)
                            .exportAIMessage(7L)
                            .getMessages()
                            .get(0)
                            .getContent());
            assertEquals(
                    "Second writing",
                    conversations
                            .captureSession(secondId)
                            .exportAIMessage(7L)
                            .getMessages()
                            .get(0)
                            .getContent());
            assertEquals(2, conversations.index().list(1, 7L, true, "", null, null).items.size());
            assertTrue(conversations.index().list(1, 0L, true, "", null, null).items.isEmpty());
            conversations.migrateDraftAIMessageToArticle(7L, -1L);
            assertEquals(
                    1, conversations.captureSession(firstId).exportAIMessage(7L).getMessageCount());
        }
    }

    @Test
    public void savingDuringFirstTurnMovesTheDirectoryAndPreservesItsTitle() throws Exception {
        try (var db = open()) {
            var conversations =
                    AIServiceDependencies.conversations(db.dataSource()).captureAccount(1);
            String id = UUID.randomUUID().toString();
            conversations.index().touch(1, 0L, null, id, "Create a draft", true);
            var rename = new AIChatModels.SessionChange();
            rename.articleId = 0L;
            rename.sessionId = id;
            rename.title = "My writing project";
            conversations.index().rename(1, rename);
            conversations.migrateDraftAIMessageToArticle(7L, -1L);
            conversations
                    .captureSession(id)
                    .appendAIMessageEntries(
                            List.of(new AIContentEntry("user", "Create a draft")), 7L);
            assertTrue(conversations.index().list(1, 0L, true, "", null, null).items.isEmpty());
            assertEquals(
                    "My writing project",
                    conversations.index().list(1, 7L, true, "", null, null).items.get(0).title);
        }
    }

    @Test
    public void articleDeletionRemovesAllItsSessionsButKeepsOtherContexts() throws Exception {
        try (var db = open()) {
            var conversations =
                    AIServiceDependencies.conversations(db.dataSource()).captureAccount(1);
            String id = UUID.randomUUID().toString();
            var session = conversations.captureSession(id);
            session.appendAIMessageEntries(List.of(new AIContentEntry("user", "Delete this")), 7L);
            session.captureAccount(2)
                    .appendAIMessageEntries(List.of(new AIContentEntry("user", "Also delete")), 7L);
            session.appendAIMessageEntries(List.of(new AIContentEntry("user", "Keep this")), 17L);
            session.appendAIMessageEntries(List.of(new AIContentEntry("user", "General")), null);
            conversations.removeAIMessage(7L);
            assertTrue(session.exportAIMessage(7L).getMessages().isEmpty());
            assertTrue(session.captureAccount(2).exportAIMessage(7L).getMessages().isEmpty());
            assertTrue(conversations.index().list(1, 7L, true, "", null, null).items.isEmpty());
            assertEquals(1, session.exportAIMessage(17L).getMessageCount());
            assertEquals(1, session.exportAIMessage(null).getMessageCount());
        }
    }

    @Test
    public void openingAnEmptySessionDoesNotCreateAHistoryEntry() throws Exception {
        try (var db = open()) {
            var conversations =
                    AIServiceDependencies.conversations(db.dataSource()).captureAccount(1);
            var empty = conversations.captureSession(UUID.randomUUID().toString());
            assertTrue(empty.getAiMessageInfoByArticleId(null).getAiMessages().isEmpty());
            assertTrue(conversations.index().list(1, null, false, "", null, null).items.isEmpty());
            assertThrows(
                    com.zrlog.common.exception.ArgsException.class,
                    () ->
                            conversations
                                    .captureTask("task-1")
                                    .captureSession(UUID.randomUUID().toString()));
        }
    }
}
