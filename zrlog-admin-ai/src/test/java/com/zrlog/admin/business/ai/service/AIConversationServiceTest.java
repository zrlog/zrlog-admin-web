package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.model.WebSite;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AIConversationServiceTest {

    @Test
    public void shouldFillAiMessagesFromSerializedCacheValue() throws Exception {
        AIConversationService service =
                new AIConversationService(
                        AccountServicesFixture.component().security(),
                        ApplicationServicesFixture.websiteKv(),
                        WebSite::new,
                        new AIFileService(),
                        AIServiceDependencies.approvals());
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();

        service.fillAiMessages(info, Map.of("ai_chat_message_7",
                "[{\"role\":\"user\",\"content\":\"hello\",\"messageId\":\"m1\"}]"), "ai_chat_message_7");

        assertEquals(1, info.getAiMessages().size());
        assertEquals("user", info.getAiMessages().get(0).getRole());
        assertEquals("hello", info.getAiMessages().get(0).getContent());
        assertEquals("m1", info.getAiMessages().get(0).getMessageId());

        service.fillAiMessages(info, Map.of(), "missing");
        assertEquals(List.of(), info.getAiMessages());
    }

    @Test
    public void shouldEnsureSystemMessageOnlyWhenMissing() {
        AIConversationService service =
                new AIConversationService(
                        AccountServicesFixture.component().security(),
                        ApplicationServicesFixture.websiteKv(),
                        WebSite::new,
                        new AIFileService(),
                        AIServiceDependencies.approvals());
        List<AIResponseEntry.AIContentEntry> messages = new ArrayList<>();
        messages.add(new AIResponseEntry.AIContentEntry("user", "question"));

        service.ensureSystemMessage(messages, "system prompt");
        service.ensureSystemMessage(messages, "ignored");

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).getRole());
        assertEquals("system prompt", messages.get(0).getContent());
        assertEquals("user", messages.get(1).getRole());
    }

    @Test
    public void shouldFillMissingAiMessageIdsAndBuildCacheKeys() throws Exception {
        AIConversationService service =
                new AIConversationService(
                        AccountServicesFixture.component().security(),
                        ApplicationServicesFixture.websiteKv(),
                        WebSite::new,
                        new AIFileService(),
                        AIServiceDependencies.approvals());
        AIResponseEntry.AIContentEntry existing = new AIResponseEntry.AIContentEntry("user", "existing");
        existing.setMessageId("existing-id");
        AIResponseEntry.AIContentEntry missing = new AIResponseEntry.AIContentEntry("assistant", "missing");
        List<AIResponseEntry.AIContentEntry> messages = List.of(existing, missing);

        service.fillMissingMessageIds(messages);

        assertEquals("existing-id", existing.getMessageId());
        assertNotNull(missing.getMessageId());
        assertFalse(missing.getMessageId().isEmpty());
        assertEquals("ai_chat_message_42", AIConversationService.buildCacheKey(42L));
        assertEquals("", service.emptyToBlank(null));
        assertEquals("value", service.emptyToBlank("value"));
    }
}
