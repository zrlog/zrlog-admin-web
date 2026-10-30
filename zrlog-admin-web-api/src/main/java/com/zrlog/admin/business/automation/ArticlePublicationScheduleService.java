package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.content.ArticlePublicationScheduler;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.data.security.AccountAccess;
import jakarta.inject.Inject;

/** Article publication and the Assistant edit the same personal plan. */
public final class ArticlePublicationScheduleService implements ArticlePublicationScheduler {
    private final AutomationService tasks;
    private final AutomationStore store;
    private final AdminAuditService audit;

    @Inject
    public ArticlePublicationScheduleService(AutomationService tasks, AutomationStore store, AdminAuditService audit) {
        this.tasks = tasks; this.store = store; this.audit = audit;
    }

    @Override
    public void cancel(AccountAccess actor, long articleId, AdminAuditContext context) throws Exception {
        var parameters = new com.google.gson.JsonObject(); parameters.addProperty("articleId", articleId);
        var row = store.task(tasks.instanceId(actor, "article.publish", parameters));
        if (row != null && row.value.enabled && row.value.activeRun == null) {
            tasks.setEnabled(actor, row.value.id, row.value.revision, false);
            audit.record(context, AdminAuditAction.PAUSE_ASSISTANT_TASK, row.value.name);
        }
    }

    @Override
    public Schedule schedule(AccountAccess actor, long articleId, int version, String title, long publishAt,
                             AdminAuditContext context) throws Exception {
        var plan = new AutomationModels.Plan();
        plan.type = "article.publish";
        plan.name = title.length() > 160 ? title.substring(0, 160) : title;
        plan.parameters.addProperty("articleId", articleId); plan.parameters.addProperty("version", version);
        plan.schedule = new AutomationModels.Schedule(); plan.schedule.at = publishAt;
        var previous = store.task(tasks.instanceId(actor, plan.type, plan.parameters));
        if (previous != null) { plan.id = previous.value.id; plan.revision = previous.value.revision; }
        var saved = tasks.save(actor, plan);
        audit.record(context, AdminAuditAction.CONFIGURE_ASSISTANT_TASK, saved.name);
        var result = new Schedule(); result.state = "scheduled"; result.taskId = saved.id;
        result.taskRevision = saved.revision; result.publishAt = publishAt; result.articleVersion = version;
        return result;
    }
}
