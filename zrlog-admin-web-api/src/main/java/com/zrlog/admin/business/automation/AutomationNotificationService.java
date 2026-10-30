package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The durable run is also the inbox source, so finishing a task cannot lose a separate notification write. */
public final class AutomationNotificationService {
    public static final String PREFIX = "server.assistant.";
    private static final Set<String> ATTENTION = Set.of("failed", "needs_attention", "uncertain");
    private final AutomationStore store;
    private final TaskAuthority authority;

    @Inject
    public AutomationNotificationService(AutomationStore store, TaskAuthority authority) {
        this.store = store;
        this.authority = authority;
    }

    public List<MessageCenterNoticeResponse> list(AccountAccess actor) throws SQLException {
        actor = authority.current(actor);
        List<MessageCenterNoticeResponse> notices = new ArrayList<>();
        // Same bounded recent-history window as retention; no unbounded scan on a FaaS request.
        long oldest = System.currentTimeMillis() - java.time.Duration.ofDays(90).toMillis();
        for (int offset = 0; offset < 1000 && notices.size() < 50; offset += 100) {
            var rows = store.runs(actor.getUserId(), null, 100, offset);
            for (var row : rows) {
                AutomationModels.Run run = row.value;
                if (run.ownerId != actor.getUserId() || !isNotice(run) || run.noticeReadAt != null
                        || run.finishedAt < oldest) continue;
                AutomationViews.Run view = AutomationDashboard.run(run);
                MessageCenterNoticeResponse.OperationTaskPayload payload = new MessageCenterNoticeResponse.OperationTaskPayload();
                payload.setTitle(run.taskName);
                String description = Objects.toString(view.summary, "");
                if (run.result != null && run.result.text != null && !run.result.text.isBlank()) {
                    description += (description.isEmpty() ? "" : "\n") + run.result.text;
                }
                payload.setDescription(description.length() > 1000 ? description.substring(0, 1000) + "…" : description);
                payload.setSource(I18nUtil.getAdminBackendStringFromRes("admin.automation.title"));
                payload.setActionLabel(I18nUtil.getAdminBackendStringFromRes("admin.automation.notice.detail"));
                payload.setActionPath("/assistant?runId=" + run.id);
                payload.setClosable(true);
                String status = "failed".equals(run.status) ? "error" : ATTENTION.contains(run.status) ? "warning" : "notice";
                notices.add(MessageCenterNoticeResponse.of(PREFIX + run.id, "operationTask", status, run.finishedAt, payload));
                if (notices.size() == 50) break;
            }
            if (rows.size() < 100) break;
        }
        return notices;
    }

    public boolean markRead(AccountAccess actor, String taskKey) throws SQLException {
        actor = authority.current(actor);
        if (taskKey == null || !taskKey.startsWith(PREFIX)) throw new IllegalArgumentException("automation.invalidId");
        String id = taskKey.substring(PREFIX.length());
        AutomationStore.requireRunId(id);
        // Check ownership before looking up another account's row.
        if (!id.startsWith(AutomationStore.ownerPrefix(actor.getUserId()))) throw new PermissionErrorException();
        for (int attempt = 0; attempt < 3; attempt++) {
            var row = store.run(id);
            if (row == null) return true; // A retained notice may expire before it is dismissed.
            if (row.value.ownerId != actor.getUserId()) throw new PermissionErrorException();
            if (!isNotice(row.value)) return false;
            if (row.value.noticeReadAt != null) return true;
            var next = row.copy(AutomationModels.Run.class);
            next.noticeReadAt = System.currentTimeMillis();
            if (store.writeAuthorized(row.key, row, next, actor)) return true;
            actor = authority.current(actor);
        }
        throw new IllegalStateException("automation.runChanged");
    }

    private static boolean isNotice(AutomationModels.Run run) {
        return run.finishedAt != null && !"running".equals(run.status)
                && (ATTENTION.contains(run.status) || run.result != null && run.result.notifyOwner);
    }
}
