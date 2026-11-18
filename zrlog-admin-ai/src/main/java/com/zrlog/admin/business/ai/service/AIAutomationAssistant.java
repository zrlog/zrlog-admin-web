package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.model.AIProviderRequests;
import com.zrlog.admin.business.ai.prompt.AIInstructionPrompt;
import com.zrlog.admin.business.automation.AutomationAssistant;
import com.zrlog.admin.business.automation.AutomationModels;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.business.service.UserPreferenceService;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Reuses the assistant's configured provider and protocol adapter without giving the model tools.
 */
@Singleton
public final class AIAutomationAssistant implements AutomationAssistant {
    private final AIConfigService config;
    private final AIChatModelClient model;
    private final AccountIdentityService identities;
    private final UserPreferenceService preferences;
    private final Gson json = new Gson();

    @Inject
    public AIAutomationAssistant(
            AIConfigService config,
            AIChatModelClient model,
            AccountIdentityService identities,
            UserPreferenceService preferences) {
        this.config = config;
        this.model = model;
        this.identities = identities;
        this.preferences = preferences;
    }

    @Override
    public boolean available(AccountAccess actor) {
        if (!AccountAction.ARTICLE_ASSIST.allowed(identities.revalidate(actor))) return false;
        try {
            AIService.checkAiConfig(config.ai());
            return true;
        } catch (com.zrlog.common.exception.ArgsException error) {
            return false;
        }
    }

    @Override
    public Proposal propose(
            AccountAccess actor,
            Request request,
            List<AutomationModels.Definition> definitions,
            long now)
            throws Exception {
        request.doValid();
        actor = identities.revalidate(actor);
        if (!AccountAction.ARTICLE_ASSIST.allowed(actor)) throw new PermissionErrorException();
        var info = config.ai();
        AIService.checkAiConfig(info);
        String language = preferences.effective(actor).language;
        String instructions =
                AIInstructionPrompt.AUTOMATION_PROPOSAL.text()
                        + "\nCurrent time: "
                        + Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
                        + "; server timezone: "
                        + ZoneId.systemDefault().getId()
                        + "; language: "
                        + language
                        + "\nAvailable capabilities: "
                        + json.toJson(definitions);
        var choice =
                model.completeTurn(
                        actor,
                        info,
                        List.of(
                                new AIProviderRequests.Message("system", instructions),
                                new AIProviderRequests.Message("user", request.input)),
                        List.of(),
                        (type, text) -> {});
        var message = choice.getMessage();
        if (message == null
                || message.getContent() == null
                || message.getContent().length() > 24000
                || message.toolCalls != null && !message.toolCalls.isEmpty()) {
            throw new AIResponseException("Invalid automation proposal");
        }
        try {
            return json.fromJson(message.getContent().trim(), Proposal.class);
        } catch (com.google.gson.JsonParseException error) {
            throw new AIResponseException("Invalid automation proposal");
        }
    }
}
