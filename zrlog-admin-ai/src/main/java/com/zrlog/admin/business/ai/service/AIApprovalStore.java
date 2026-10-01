package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.model.AIChatModels.Run;
import com.zrlog.admin.business.ai.model.AIChatModels.RunView;
import com.zrlog.admin.business.security.SecurityStore;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One private checkpoint per account/editor, with atomic claims on JDBC and Web API databases. */
final class AIApprovalStore {
    static final long APPROVAL_TTL = 30 * 60_000L;
    static final long RUN_TIMEOUT = 20 * 60_000L;
    private static final int MAX_CHECKPOINT_LENGTH = 16 * 1024 * 1024;
    private final Gson json = new Gson();
    private final SecurityStore db = new SecurityStore();

    static final class Changed extends RuntimeException { }

    private String key(int userId, long articleId) { return "ai_pending_u" + userId + "_" + articleId; }

    Run read(int userId, long articleId) throws SQLException {
        Map<String, Object> row = db.withSession(c -> db.one(c,
                "select value,remark from website where name=?", key(userId, articleId)));
        if (row == null || row.get("value") == null) return null;
        Run run = json.fromJson(row.get("value").toString(), Run.class);
        if (run == null || run.userId != userId || run.articleId != articleId
                || !Objects.equals(run.revision, row.get("remark"))) throw new SQLException("Invalid assistant checkpoint");
        return run;
    }

    static boolean active(Run run) {
        if (run == null) return false;
        String status = view(run).status;
        return "awaiting_approval".equals(status) || "awaiting_input".equals(status) || "running".equals(status) || "executing".equals(status);
    }

    void pause(Run run) throws SQLException {
        String waiting = run.interaction == null ? "awaiting_approval" : "awaiting_input";
        if (run.revision != null) { save(run, waiting); return; }
        Run old = read(run.userId, run.articleId);
        if (active(old)) throw new Changed();
        run.status = waiting;
        run.updatedAt = System.currentTimeMillis();
        run.revision = UUID.randomUUID().toString();
        String value;
        try { value = serialize(run); }
        catch (SQLException e) { run.revision = null; throw e; }
        if (old == null) {
            try {
                db.withSession(c -> db.update(c, "insert into website(name,value,remark) values(?,?,?)",
                        key(run.userId, run.articleId), value, run.revision));
            } catch (SQLException e) {
                run.revision = null;
                if (read(run.userId, run.articleId) != null) throw new Changed();
                throw e;
            }
        } else {
            try {
                if (db.withSession(c -> db.update(c,
                        "update website set value=?,remark=? where name=? and remark=?",
                        value, run.revision, key(run.userId, run.articleId), old.revision)) != 1) throw new Changed();
            } catch (SQLException | RuntimeException e) {
                run.revision = null;
                throw e;
            }
        }
    }

    void save(Run run, String status) throws SQLException {
        String expected = run.revision;
        if (expected == null) return; // Read-only turns have no checkpoint.
        String previousStatus = run.status;
        long previousUpdatedAt = run.updatedAt;
        String next = UUID.randomUUID().toString();
        run.status = status;
        run.updatedAt = System.currentTimeMillis();
        run.revision = next;
        try {
            String value = serialize(run);
            if (db.withSession(c -> db.update(c,
                    "update website set value=?,remark=? where name=? and remark=?",
                    value, next, key(run.userId, run.articleId), expected)) != 1) throw new Changed();
        } catch (SQLException | RuntimeException e) {
            run.revision = expected;
            run.status = previousStatus;
            run.updatedAt = previousUpdatedAt;
            throw e;
        }
    }

    void check(Run run) throws SQLException {
        if (run.revision == null) return;
        Map<String, Object> current = db.withSession(c -> db.one(c,
                "select remark from website where name=?", key(run.userId, run.articleId)));
        if (current == null || !Objects.equals(current.get("remark"), run.revision)) throw new Changed();
    }

    private String serialize(Run run) throws SQLException {
        String value = json.toJson(run);
        if (value.length() > MAX_CHECKPOINT_LENGTH) throw new SQLException("Assistant checkpoint is too large");
        return value;
    }

    static RunView view(Run run) {
        if (run == null) return null;
        RunView view = new RunView();
        view.runId = run.id; view.articleId = run.articleId; view.input = run.input;
        view.status = run.status; view.approval = run.approval; view.error = run.error;
        view.interaction = run.interaction; view.skillMessages = run.skillMessages;
        view.answer = run.answer; view.articleUpdates = run.articleUpdates;
        if ("awaiting_approval".equals(view.status) && run.approval.expiresAt <= System.currentTimeMillis()) view.status = "expired";
        if ("awaiting_input".equals(view.status) && run.interaction.expiresAt <= System.currentTimeMillis()) view.status = "expired";
        if (("running".equals(view.status) || "executing".equals(view.status))
                && run.updatedAt + RUN_TIMEOUT <= System.currentTimeMillis()) view.status = "uncertain";
        return view;
    }
}
