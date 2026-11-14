package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.*;

import com.zrlog.admin.business.automation.AssistantTaskContextProvider.FeedbackPage;
import com.zrlog.admin.business.automation.AutomationModels;
import com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.repository.WebSiteRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class TaskFeedbackConversationTest {
    private AIConversationService session(InMemoryZrLogDatabase db, int userId) {
        return new AIConversationService(
                        AccountServicesFixture.component(db.dataSource()).security(),
                        ApplicationServicesFixture.websiteKv(db.dataSource()),
                        () -> new WebSiteRepository(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new com.zrlog.admin.business.ai.service.AIConversationIndex(
                                AccountServicesFixture.component(db.dataSource()).security()))
                .captureAccount(userId)
                .captureTask("system.refresh");
    }

    private FeedbackPage page(String id, long version, String status, long at) {
        var run = new AutomationModels.Run();
        run.id = id;
        run.version = version;
        run.updatedAt = at;
        run.finishedAt = at;
        run.status = status;
        run.result = AutomationModels.Result.success("admin.assistant.feedback." + status);
        var page = new FeedbackPage();
        page.items = List.of(run);
        page.before = at;
        page.beforeId = id;
        return page;
    }

    @Test
    public void persistsFeedbackAndUpdatesTheSameMessageWithoutSharingPrivateChats()
            throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var first = session(db, 1);
            var submitted = page("run-1", 1, "submitted", 1000);
            first.synchronizeTaskFeedback(submitted, null, null);
            first.synchronizeTaskFeedback(submitted, null, null);
            assertEquals(1, first.exportAIMessage(null).getMessageCount());
            assertEquals(
                    "submitted",
                    first.exportAIMessage(null).getMessages().get(0).getTaskResult().status);
            var privateMessage = new AIContentEntry("user", "Only in my conversation");
            privateMessage.setMessageId("private-message");
            first.appendAIMessageEntries(List.of(privateMessage), null);
            var success = page("run-1", 2, "success", 2000);
            first.synchronizeTaskFeedback(success, null, null);
            // A delayed page cannot overwrite the newer execution confirmation.
            first.synchronizeTaskFeedback(submitted, null, null);
            var messages = session(db, 1).exportAIMessage(null).getMessages();
            assertEquals(2, messages.size());
            assertEquals(1, messages.stream().filter(m -> m.getTaskResult() != null).count());
            assertEquals(
                    "success",
                    messages.stream()
                            .filter(m -> m.getTaskResult() != null)
                            .findFirst()
                            .orElseThrow()
                            .getTaskResult()
                            .status);
            var second = session(db, 2);
            second.synchronizeTaskFeedback(success, null, null);
            assertEquals(1, second.exportAIMessage(null).getMessageCount());
            assertFalse(
                    second.exportAIMessage(null).getMessages().stream()
                            .anyMatch(m -> "private-message".equals(m.getMessageId())));
        }
    }

    @Test
    public void clearDoesNotReimportOldFeedbackButAcceptsALaterConfirmation() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var session = session(db, 1);
            var old = page("run-1", 1, "submitted", 1000);
            session.synchronizeTaskFeedback(old, null, null);
            assertTrue(session.clearAIMessage(null));
            session.synchronizeTaskFeedback(old, null, null);
            assertEquals(0, session.exportAIMessage(null).getMessageCount());
            session.synchronizeTaskFeedback(
                    page("run-1", 2, "success", System.currentTimeMillis() + 1), null, null);
            assertEquals(1, session.exportAIMessage(null).getMessageCount());
        }
    }

    @Test
    public void simultaneousChatAppendAndFeedbackSynchronizationPreserveBothMessages()
            throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var session = session(db, 1);
            var message = new AIContentEntry("user", "What happened?");
            message.setMessageId("question");
            var write =
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    session.appendAIMessageEntries(List.of(message), null);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
            session.synchronizeTaskFeedback(page("run-1", 1, "failed", 1000), null, null);
            write.get();
            assertEquals(2, session.exportAIMessage(null).getMessageCount());
        }
    }

    @Test
    public void refreshDoesNotResetTheEarlierMessageCursor() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var session = session(db, 1);
            var latest = page("run-2", 1, "success", 2000);
            latest.hasMore = true;
            session.synchronizeTaskFeedback(latest, null, null);
            var earlier = page("run-1", 1, "failed", 1000);
            session.synchronizeTaskFeedback(earlier, 2000L, "run-2");
            session.synchronizeTaskFeedback(latest, null, null);
            var state =
                    AIConversationService.feedbackState(
                            session.getAiMessageInfoByArticleId(null).getAiMessages());
            assertEquals(Long.valueOf(1000), state.before);
            assertFalse(state.hasMore);
        }
    }
}
