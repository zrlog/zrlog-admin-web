package com.zrlog.admin.business.ai.service;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.service.DbFileService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.model.WebSite;

/** Explicit collaborators for focused AI tests; request/client doubles remain supplied by each test. */
public final class AIServiceDependencies {
    private AIServiceDependencies() {}

    public static com.zrlog.data.security.AccountAccess actor(DataSourceWrapper source) {
        if (source == null) return com.zrlog.data.security.AccountAccess.from(java.util.Map.of(
                "userId", 1, "role", "owner", "enabled", true, "authVersion", 0));
        return AccountServicesFixture.component(source).requestIdentity().current();
    }

    public static com.zrlog.admin.business.audit.AdminAuditContext audit(DataSourceWrapper source) {
        return new com.zrlog.admin.business.audit.AdminAuditContext(actor(source).getUserId(),
                "127.0.0.1", "/api/admin/article/ai", null);
    }

    public static AIConfigService config(DataSourceWrapper source) {
        return new AIConfigService(() -> new WebSite(source));
    }

    public static AIApprovalStore approvals(DataSourceWrapper source) {
        return new AIApprovalStore(AccountServicesFixture.component(source).security());
    }

    public static AIConversationService conversations(DataSourceWrapper source) {
        return new AIConversationService(
                AccountServicesFixture.component(source).security(),
                ApplicationServicesFixture.websiteKv(source),
                () -> new WebSite(source),
                new AIFileService(),
                approvals(source));
    }

    public static DbFileService dbFiles(DataSourceWrapper source) {
        return new DbFileService(ApplicationServicesFixture.websiteKv(source), () -> new WebSite(source));
    }

    public static AIToolService tools(DataSourceWrapper source) {
        return new AIToolService(config(source));
    }

    public static AIImageService images(DataSourceWrapper source) {
        return new AIImageService(
                AccountServicesFixture.component(source).preferences(),
                config(source),
                dbFiles(source),
                conversations(source),
                        AccountServicesFixture.component(source).identities());
    }

    public static AIWritingSkillService.Factory writing(DataSourceWrapper source) {
        return (actor, store) -> new AIWritingSkillService(actor, store, tools(source), images(source),
                        com.zrlog.admin.support.AccountServicesFixture.component(source).identities(),
                        com.zrlog.admin.support.AccountServicesFixture.component(source).permissions());
    }

    public static AIPublishCheckService publishing(DataSourceWrapper source) {
        return new AIPublishCheckService(
                ApplicationServicesFixture.settings(source),
                ApplicationServicesFixture.operations(source),
                writing(source),
                conversations(source),
                        com.zrlog.admin.support.AccountServicesFixture.component(source).identities(),
                        com.zrlog.admin.support.AccountServicesFixture.component(source).permissions());
    }

    public static AIWritingToolProvider.Factory writingTools(DataSourceWrapper source) {
        return (run, skills) -> new AIWritingToolProvider(run, skills, publishing(source));
    }

    public static AIReferenceService references(ContentToolFactory tools, DataSourceWrapper source) {
        return new AIReferenceService(tools,
                                AccountServicesFixture.component(source).identities(),
                                AccountServicesFixture.component(source).preferences());
    }
}
