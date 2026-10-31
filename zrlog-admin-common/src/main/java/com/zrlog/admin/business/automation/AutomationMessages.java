package com.zrlog.admin.business.automation;

/** Translates durable execution codes without changing the scheduler protocol. */
public final class AutomationMessages {
    private AutomationMessages() { }

    public static String errorKey(String code) {
        if (code == null) return "admin.common.request.error";
        switch (code) {
            case "automation.invalidPlan": return "admin.assistant.validation.invalidPlan";
            case "automation.invalidParameters": return "admin.assistant.validation.invalidParameters";
            case "automation.invalidSchedule": return "admin.assistant.validation.invalidSchedule";
            case "automation.taskChanged": return "admin.assistant.error.taskChanged";
            case "automation.timePassed": return "admin.assistant.validation.timePassed";
            case "automation.taskUnavailable": return "admin.assistant.error.taskUnavailable";
            case "automation.unknownTask": return "admin.assistant.error.unknownTask";
            case "automation.settingsChanged": return "admin.assistant.error.settingsChanged";
            case "automation.tokenRequired": return "admin.assistant.validation.tokenRequired";
            case "automation.permissionChanged": return "admin.assistant.error.permissionChanged";
            case "automation.reviewRequired": return "admin.assistant.error.reviewRequired";
            case "automation.executionUncertain": return "admin.assistant.error.executionUncertain";
            case "automation.executionInterrupted": return "admin.assistant.error.executionInterrupted";
            case "automation.executionExpired": return "admin.assistant.error.executionExpired";
            case "automation.insufficientBudget": return "admin.assistant.error.insufficientBudget";
            case "automation.invalidDefinition": return "admin.assistant.validation.invalidDefinition";
            case "automation.invalidId": return "admin.assistant.validation.invalidId";
            case "automation.invalidOwner": return "admin.assistant.validation.invalidOwner";
            case "automation.invalidResult": return "admin.assistant.validation.invalidResult";
            case "automation.invalidStoredRecord": return "admin.assistant.validation.invalidStoredRecord";
            case "automation.invalidTime": return "admin.assistant.validation.invalidTime";
            case "automation.invalidTrigger": return "admin.assistant.validation.invalidTrigger";
            case "automation.ownerMismatch": return "admin.assistant.validation.ownerMismatch";
            case "automation.runChanged": return "admin.assistant.error.runChanged";
            case "automation.runMismatch": return "admin.assistant.validation.runMismatch";
            case "automation.articleChanged": return "admin.assistant.error.articleChanged";
            case "automation.articleNotDraft": return "admin.assistant.validation.articleNotDraft";
            case "automation.publicationRefreshFailed": return "admin.assistant.error.publicationRefreshFailed";
            case "automation.invalidBudget": return "admin.assistant.validation.invalidBudget";
            case "automation.invalidProposalRequest": return "admin.assistant.validation.invalidProposalRequest";
            case "automation.aiUnavailable": return "admin.assistant.error.aiUnavailable";
            case "automation.proposalFailed": return "admin.assistant.error.proposalFailed";
            case "automation.invalidProposal": return "admin.assistant.validation.invalidProposal";
            case "automation.systemTaskManaged": return "admin.assistant.error.systemTaskManaged";
            case "automation.taskAlreadyExists": return "admin.assistant.validation.taskAlreadyExists";
            case "automation.upgrade.channelChanged": return "admin.assistant.upgrade.status.channelChanged";
            case "automation.upgrade.pending": return "admin.assistant.upgrade.status.pending";
            case "automation.upgrade.retryUnavailable": return "admin.assistant.upgrade.status.retryUnavailable";
            case "automation.upgrade.targetUnknown": return "admin.assistant.upgrade.status.targetUnknown";
            case "automation.upgrade.unconfirmed": return "admin.assistant.upgrade.status.unconfirmed";
            default: return "admin.common.request.error";
        }
    }
}
