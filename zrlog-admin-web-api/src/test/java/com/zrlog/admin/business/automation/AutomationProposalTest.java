package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AutomationProposalTest {
    @Test
    public void suggestionsNeverInheritIdsGrantsArticleVersionsOrRetryConfirmation() {
        var definition = definition();
        var article = field("articleId", "article"); definition.fields.add(article);
        var retry = field("retryUnconfirmed", "boolean"); retry.requiresConfirmation = true; definition.fields.add(retry);
        var channel = field("channel", "text"); channel.readOnly = true; definition.fields.add(channel);
        var input = proposal(); var candidate = input.plans.get(0);
        candidate.id = "another-task"; candidate.revision = 99L; candidate.enabled = true;
        candidate.authorizedActions = List.of("system.manage", "article.publish");
        candidate.parameters.addProperty("articleId", 35); candidate.parameters.addProperty("version", 10);
        candidate.parameters.addProperty("retryUnconfirmed", true); candidate.parameters.addProperty("channel", "preview");
        candidate.parameters.addProperty("hiddenCommand", "execute");
        var result = AutomationProposalService.sanitize(input, List.of(definition)).plans.get(0);
        assertNull(result.id); assertNull(result.revision); assertFalse(result.enabled); assertTrue(result.authorizedActions.isEmpty());
        assertEquals("Remember writing", result.parameters.get("text").getAsString());
        assertEquals(1, result.parameters.size());
        assertEquals("09:00", result.schedule.time);
    }

    @Test
    public void unsupportedCapabilitiesInvalidSchedulesAndWrongParameterTypesAreRejected() {
        var unknown = proposal(); unknown.plans.get(0).type = "shell.execute";
        assertThrows(IllegalArgumentException.class, () -> AutomationProposalService.sanitize(unknown, List.of(definition())));
        var schedule = proposal(); schedule.plans.get(0).schedule.timezone = "untrusted/timezone";
        assertThrows(IllegalArgumentException.class, () -> AutomationProposalService.sanitize(schedule, List.of(definition())));
        var parameters = proposal(); parameters.plans.get(0).parameters.add("text", new JsonObject());
        assertThrows(IllegalArgumentException.class, () -> AutomationProposalService.sanitize(parameters, List.of(definition())));
        var unavailable = definition(); unavailable.available = false;
        assertThrows(IllegalArgumentException.class, () -> AutomationProposalService.sanitize(proposal(), List.of(unavailable)));
    }

    @Test
    public void realRequestUsesAuthorizedCatalogAndDoesNotSaveOrExecuteTasks() throws Exception {
        try (var db = InMemoryZrLogDatabase.openWebApi()) {
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            var authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1)).restrictActions(List.of(
                    AccountAction.SESSION.getId(), AccountAction.ARTICLE_ASSIST.getId()));
            var privileged = definition(); privileged.type = "system.upgrade"; privileged.requiredActions = List.of(AccountAction.SYSTEM_MANAGE.getId());
            ScheduledTaskProvider provider = new ScheduledTaskProvider() {
                public List<AutomationModels.Definition> definitions() { return List.of(definition(), privileged); }
                public JsonObject prepare(String type, JsonObject params, AccountAccess account) { throw new AssertionError("proposal must not save"); }
                public AutomationModels.Result execute(String type, JsonObject params, TaskExecutionContext context) { throw new AssertionError("proposal must not execute"); }
            };
            var tasks = new AutomationService(store, authority, new TaskRegistry(() -> List.of(provider)), System::currentTimeMillis);
            AutomationAssistant assistant = new AutomationAssistant() {
                public boolean available(AccountAccess account) { return true; }
                public Proposal propose(AccountAccess account, Request request, List<AutomationModels.Definition> definitions, long now) {
                    assertEquals(1, definitions.size()); assertEquals("personal.reminder", definitions.get(0).type);
                    return proposal();
                }
            };
            var service = new AutomationProposalService(() -> assistant, tasks, authority);
            var request = new AutomationAssistant.Request(); request.input = "Remind me daily"; request.timezone = "UTC";
            assertEquals(1, service.propose(actor, request).plans.size());
            assertEquals(0, store.taskCount(actor.getUserId()));
            assertEquals(0, store.runCount(actor.getUserId(), null));
            var unavailable = new AutomationProposalService(() -> null, tasks, authority);
            assertFalse(unavailable.available(actor));
            assertThrows(IllegalStateException.class, () -> unavailable.propose(actor, request));
            assertEquals(1, tasks.definitions(actor).size());
        }
    }

    private static AutomationModels.Definition definition() {
        var definition = new AutomationModels.Definition(); definition.type = "personal.reminder";
        definition.titleKey = "admin.automation.reminder.title"; definition.descriptionKey = "admin.automation.reminder.description";
        definition.fields.add(field("text", "textarea")); return definition;
    }

    private static AutomationModels.Field field(String name, String type) {
        var field = new AutomationModels.Field(); field.name = name; field.type = type; field.labelKey = "admin.automation.reminder.text"; return field;
    }

    private static AutomationAssistant.Proposal proposal() {
        var result = new AutomationAssistant.Proposal(); result.explanation = "Review the reminder.";
        var plan = new AutomationModels.Plan(); plan.type = "personal.reminder"; plan.name = "Writing";
        plan.parameters.addProperty("text", "Remember writing");
        plan.schedule = new AutomationModels.Schedule(); plan.schedule.kind = "daily"; plan.schedule.time = "09:00";
        result.plans.add(plan); return result;
    }
}
