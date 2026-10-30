package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** All model output is untrusted. Only editable plans are returned; saving uses the normal task service. */
public final class AutomationProposalService {
    private final Supplier<AutomationAssistant> assistant;
    private final AutomationService tasks;
    private final TaskAuthority authority;

    @Inject
    public AutomationProposalService(Supplier<AutomationAssistant> assistant, AutomationService tasks, TaskAuthority authority) {
        this.assistant = assistant;
        this.tasks = tasks;
        this.authority = authority;
    }

    public boolean available(AccountAccess actor) {
        var source = assistant.get();
        return source != null && source.available(authority.current(actor));
    }

    public AutomationAssistant.Proposal propose(AccountAccess actor, AutomationAssistant.Request input) throws Exception {
        input.doValid();
        actor = authority.current(actor);
        TaskAuthority.require(actor, AccountAction.ARTICLE_ASSIST.getId());
        var source = assistant.get();
        if (source == null || !source.available(actor)) throw new IllegalStateException("automation.aiUnavailable");
        List<AutomationModels.Definition> definitions = tasks.definitions(actor).stream().filter(item -> item.available && !item.systemTask)
                .map(definition -> {
                    var copy = AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(definition), AutomationModels.Definition.class);
                    copy.title = com.zrlog.util.I18nUtil.getAdminBackendStringFromRes(copy.titleKey);
                    copy.description = com.zrlog.util.I18nUtil.getAdminBackendStringFromRes(copy.descriptionKey);
                    for (var field : copy.fields) field.label = com.zrlog.util.I18nUtil.getAdminBackendStringFromRes(field.labelKey);
                    return copy;
                }).collect(java.util.stream.Collectors.toList());
        AutomationAssistant.Proposal proposal;
        try { proposal = source.propose(actor, input, definitions, System.currentTimeMillis()); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw error; }
        catch (Exception error) { throw new IllegalStateException("automation.proposalFailed", error); }
        // Revocation while the provider was responding also invalidates the returned proposal.
        actor = authority.current(actor);
        TaskAuthority.require(actor, AccountAction.ARTICLE_ASSIST.getId());
        return sanitize(proposal, tasks.definitions(actor));
    }

    static AutomationAssistant.Proposal sanitize(AutomationAssistant.Proposal proposal, List<AutomationModels.Definition> definitions) {
        if (proposal == null || proposal.explanation == null || proposal.explanation.length() > 4000
                || proposal.plans == null || proposal.plans.size() > 3) throw invalid();
        AutomationAssistant.Proposal result = new AutomationAssistant.Proposal();
        result.explanation = proposal.explanation;
        for (var candidate : proposal.plans) {
            if (candidate == null) throw invalid();
            var definition = definitions.stream().filter(item -> item.available && !item.systemTask && item.type.equals(candidate.type))
                    .findFirst().orElseThrow(AutomationProposalService::invalid);
            if (candidate.name == null || candidate.name.isBlank() || candidate.name.length() > 160
                    || candidate.parameters == null || candidate.parameters.toString().length() > 16000) throw invalid();
            try { TaskSchedule.validate(candidate.schedule); }
            catch (IllegalArgumentException error) { throw invalid(); }
            if (TaskSchedule.nextAfter(candidate.schedule, System.currentTimeMillis()) == null) throw invalid();
            var plan = new AutomationModels.Plan();
            plan.type = definition.type;
            plan.name = candidate.name;
            plan.enabled = false;
            plan.schedule = AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(candidate.schedule), AutomationModels.Schedule.class);
            plan.parameters = new JsonObject();
            for (var field : definition.fields) {
                if (field.readOnly || field.requiresConfirmation || "article".equals(field.type)) continue;
                var value = candidate.parameters.get(field.name);
                if (value == null || value.isJsonNull()) continue;
                if (!value.isJsonPrimitive() || !validValue(field, value.getAsJsonPrimitive())) throw invalid();
                plan.parameters.add(field.name, value.deepCopy());
            }
            result.plans.add(plan);
        }
        return result;
    }

    private static boolean validValue(AutomationModels.Field field, JsonPrimitive value) {
        if ("boolean".equals(field.type)) return value.isBoolean();
        if ("number".equals(field.type)) {
            if (!value.isNumber()) return false;
            try {
                long number = value.getAsBigDecimal().longValueExact();
                return (field.minimum == null || number >= field.minimum) && (field.maximum == null || number <= field.maximum);
            } catch (ArithmeticException | NumberFormatException error) { return false; }
        }
        return value.isString() && value.getAsString().length() <= ("textarea".equals(field.type) ? 4000 : 2000);
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("automation.invalidProposal"); }
}
