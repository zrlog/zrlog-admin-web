package com.zrlog.admin.business.automation;

import com.zrlog.data.security.AccountAccess;
import java.sql.SQLException;

/** Resolves a task for a conversation using the task's current access rules. */
public interface AssistantTaskContextProvider {
    AutomationModels.Plan require(AccountAccess actor, String taskId) throws SQLException;

    /** Bounded summary projection, fetched only when the user sends a message. */
    default java.util.List<AutomationModels.Run> recentRuns(AccountAccess actor, String taskId)
            throws SQLException {
        return java.util.List.of();
    }

    /** Lightweight, permission-checked feedback; no plan snapshots or child result bodies. */
    default FeedbackPage feedback(AccountAccess actor, String taskId, Long before, String beforeId)
            throws SQLException {
        return new FeedbackPage();
    }

    class FeedbackPage {
        public java.util.List<AutomationModels.Run> items = new java.util.ArrayList<>();
        public Long before;
        public String beforeId;
        public boolean hasMore;
    }
}
