package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.service.DbFileService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.model.WebSite;

/** Explicit collaborators for focused AI tests; request/client doubles remain supplied by each test. */
public final class AIServiceDependencies {
    private AIServiceDependencies() {}

    public static AIConfigService config() {
        return new AIConfigService(WebSite::new);
    }

    public static AIApprovalStore approvals() {
        return new AIApprovalStore(AccountServicesFixture.component().security());
    }

    public static AIConversationService conversations() {
        return new AIConversationService(
                AccountServicesFixture.component().security(),
                ApplicationServicesFixture.websiteKv(),
                WebSite::new,
                new AIFileService(),
                approvals());
    }

    public static DbFileService dbFiles() {
        return new DbFileService(ApplicationServicesFixture.websiteKv(), WebSite::new);
    }

    public static AIToolService tools() {
        return new AIToolService(config());
    }

    public static AIImageService images() {
        return new AIImageService(
                AccountServicesFixture.component().preferences(),
                config(),
                dbFiles(),
                conversations());
    }

    public static AIWritingSkillService.Factory writing() {
        return store -> new AIWritingSkillService(store, tools(), images());
    }

    public static AIPublishCheckService publishing() {
        return new AIPublishCheckService(
                ApplicationServicesFixture.settings(),
                ApplicationServicesFixture.operations(),
                writing(),
                conversations());
    }

    public static AIWritingToolProvider.Factory writingTools() {
        return (run, skills) -> new AIWritingToolProvider(run, skills, publishing());
    }

    public static AIReferenceService references(ContentToolFactory tools) {
        return new AIReferenceService(
                tools,
                AccountServicesFixture.component().permissions(),
                AccountServicesFixture.component().preferences());
    }
}
