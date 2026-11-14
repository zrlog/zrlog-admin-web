package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The durable run is also the inbox source, so finishing a task cannot lose a separate notification
 * write.
 */
public final class AutomationNotificationService {
    public static final String PREFIX = "server.assistant.";
    private static final Set<String> ATTENTION = Set.of("failed", "needs_attention", "uncertain");
    private final AutomationStore store;
    private final TaskAuthority authority;
    private final AutomationService tasks;

    AutomationNotificationService(AutomationStore store, TaskAuthority authority) {
        this(store, authority, null);
    }

    @Inject
    public AutomationNotificationService(
            AutomationStore store, TaskAuthority authority, AutomationService tasks) {
        this.store = store;
        this.authority = authority;
        this.tasks = tasks;
    }

    public List<MessageCenterNoticeResponse> list(AccountAccess actor) throws SQLException {
        actor = authority.current(actor);
        TaskAuthority.require(actor, AccountAction.SESSION.getId());
        List<String> systemIds = tasks == null ? List.of() : tasks.systemTaskIds(actor);
        List<MessageCenterNoticeResponse> notices = new ArrayList<>();
        for (var row : store.unreadNotices(actor.getUserId(), systemIds, 50)) {
            AutomationModels.Run run = row.value;
            AutomationViews.Run view = AutomationDashboard.run(run);
            MessageCenterNoticeResponse.OperationTaskPayload payload =
                    new MessageCenterNoticeResponse.OperationTaskPayload();
            payload.setTitle(view.taskName);
            String description = Objects.toString(view.summary, "");
            if (run.result != null && run.result.text != null && !run.result.text.isBlank()) {
                description += (description.isEmpty() ? "" : "\n") + run.result.text;
            }
            payload.setDescription(
                    description.length() > 1000
                            ? description.substring(0, 1000) + "…"
                            : description);
            payload.setSource(I18nUtil.getAdminBackendStringFromRes("admin.assistant.manage"));
            payload.setActionLabel(
                    I18nUtil.getAdminBackendStringFromRes("admin.assistant.notice.detail"));
            payload.setActionPath("/assistant/history?runId=" + run.id);
            payload.setClosable(true);
            String status =
                    "failed".equals(run.status)
                            ? "error"
                            : ATTENTION.contains(run.status) ? "warning" : "notice";
            notices.add(
                    MessageCenterNoticeResponse.of(
                            PREFIX + run.id, "operationTask", status, run.finishedAt, payload));
            if (notices.size() == 50) break;
        }
        return notices;
    }

    public boolean markRead(AccountAccess actor, String taskKey) throws SQLException {
        actor = authority.current(actor);
        TaskAuthority.require(actor, AccountAction.SESSION.getId());
        if (taskKey == null || !taskKey.startsWith(PREFIX))
            throw new IllegalArgumentException("automation.invalidId");
        String id = taskKey.substring(PREFIX.length());
        AutomationStore.requireRunId(id);
        List<String> systemIds = tasks == null ? List.of() : tasks.systemTaskIds(actor);
        var row = store.run(id);
        if (row == null) return true;
        if (row.value.ownerId != actor.getUserId()
                && !(row.value.ownerId == 0 && systemIds.contains(row.value.taskId)))
            throw new PermissionErrorException();
        if (!isNotice(row.value)) return false;
        if (!store.markRunRead(row, actor))
            throw new IllegalStateException("automation.runChanged");
        return true;
    }

    private static boolean isNotice(AutomationModels.Run run) {
        return run.finishedAt != null
                && !"running".equals(run.status)
                && (ATTENTION.contains(run.status)
                        || run.result != null && (run.result.notifyOwner || run.result.recovered));
    }
}
