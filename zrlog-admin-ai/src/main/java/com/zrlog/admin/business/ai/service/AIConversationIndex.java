package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.model.AIChatModels;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.security.SecurityStore;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.model.WebSite;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Small private directory rows; listing never reads message or checkpoint payloads. */
public final class AIConversationIndex {
    private final SecurityStore db;
    private final Gson json = new Gson();

    @Inject
    public AIConversationIndex(SecurityStore db) {
        this.db = db;
    }

    private String prefix(int userId) {
        if (userId <= 0) throw new PermissionErrorException();
        return "ai_session_u" + userId + "_";
    }

    private String context(Long articleId, String taskId) {
        String scope = AIConversationScope.key(articleId, taskId);
        return taskId == null
                ? scope
                : "task_"
                        + java.util.UUID.nameUUIDFromBytes(
                                scope.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String key(int userId, Long articleId, String taskId, String sessionId) {
        AIConversationScope.validateSession(sessionId, taskId);
        return prefix(userId)
                + context(articleId, taskId)
                + ":"
                + Objects.toString(sessionId, "default");
    }

    private WebSite row(int userId, Long articleId, String taskId, String sessionId)
            throws SQLException {
        return db.withSession(
                c ->
                        db.firstEntity(
                                c,
                                WebSite.class,
                                "WHERE name=? LIMIT 1",
                                key(userId, articleId, taskId, sessionId)));
    }

    public void check(int userId, Long articleId, String taskId, String sessionId)
            throws SQLException {
        WebSite row = row(userId, articleId, taskId, sessionId);
        if (row != null && row.getValue() == null) throw new PermissionErrorException();
    }

    public void touch(
            int userId,
            Long articleId,
            String taskId,
            String sessionId,
            String title,
            boolean activity)
            throws SQLException {
        WebSite row = row(userId, articleId, taskId, sessionId);
        if (row != null) {
            if (row.getValue() == null) throw new PermissionErrorException();
            if (!activity) return;
            // Updating only the directory preserves a title renamed by another tab.
            db.withSession(
                    c ->
                            db.repository(c, WebSite.class)
                                    .updateWhere(
                                            WebSiteRepository.entry(null, row.getValue(), null),
                                            List.of("value"),
                                            "name=? AND value IS NOT NULL AND remark IS NOT NULL",
                                            row.getName()));
            return;
        }
        AIChatModels.Session item = new AIChatModels.Session();
        item.sessionId = sessionId;
        item.articleId = articleId;
        item.taskId = taskId;
        String name = key(userId, articleId, taskId, sessionId);
        String label = Objects.toString(title, "").strip().replaceAll("\\s+", " ");
        label = label.substring(0, Math.min(120, label.length()));
        WebSite created = WebSiteRepository.entry(name, json.toJson(item), label);
        try {
            db.withSession(c -> db.repository(c, WebSite.class).insert(created));
        } catch (SQLException error) {
            if (row(userId, articleId, taskId, sessionId) == null) throw error;
            check(userId, articleId, taskId, sessionId);
        }
    }

    public AIChatModels.SessionPage list(
            int userId,
            Long articleId,
            boolean filterArticle,
            String query,
            Long before,
            String beforeKey)
            throws SQLException {
        String prefix = prefix(userId) + (filterArticle ? context(articleId, null) + ":" : "");
        String where = "WHERE name LIKE ? ESCAPE '!' AND value IS NOT NULL AND remark IS NOT NULL";
        List<Object> args = new ArrayList<>();
        args.add(escape(prefix) + "%");
        if (query != null && !query.isBlank()) {
            if (query.length() > 120) throw new ArgsException();
            where += " AND LOWER(remark) LIKE ? ESCAPE '!'";
            args.add("%" + escape(query.strip().toLowerCase(java.util.Locale.ROOT)) + "%");
        }
        if (before != null) {
            if (beforeKey == null || !beforeKey.startsWith(prefix) || beforeKey.length() > 256)
                throw new ArgsException();
            where += " AND (updatedAt<? OR (updatedAt=? AND name<?))";
            args.add(new Timestamp(before));
            args.add(new Timestamp(before));
            args.add(beforeKey);
        }
        String clauses = where + " ORDER BY updatedAt DESC,name DESC LIMIT 21";
        List<WebSite> rows =
                db.withSession(c -> db.entities(c, WebSite.class, clauses, args.toArray()));
        AIChatModels.SessionPage page = new AIChatModels.SessionPage();
        page.hasMore = rows.size() > 20;
        for (WebSite row : rows.stream().limit(20).collect(java.util.stream.Collectors.toList())) {
            AIChatModels.Session item = json.fromJson(row.getValue(), AIChatModels.Session.class);
            item.title = row.getRemark();
            item.updatedAt = row.getUpdatedAt().getTime();
            item.createdAt = row.getCreatedAt().getTime();
            page.items.add(item);
            page.before = item.updatedAt;
            page.beforeKey = row.getName();
        }
        return page;
    }

    public void rename(int userId, AIChatModels.SessionChange change) throws SQLException {
        WebSite row = row(userId, change.articleId, change.taskId, change.sessionId);
        if (row == null || row.getValue() == null || change.title == null)
            throw new ArgsException();
        update(row, WebSiteRepository.entry(null, null, change.title.strip()), List.of("remark"));
    }

    public void remove(int userId, Long articleId, String taskId, String sessionId)
            throws SQLException {
        if (taskId != null) throw new ArgsException(); // A task owns one continuing conversation.
        WebSite row = row(userId, articleId, null, sessionId);
        if (row == null) return;
        update(row, WebSiteRepository.entry(null, null, null), List.of("value", "remark"));
    }

    /** Copy the directory even when the first turn is still waiting for article creation. */
    public void copyDraft(int userId, String sessionId, Long articleId) throws SQLException {
        WebSite draft = row(userId, 0L, null, sessionId);
        if (draft != null && draft.getValue() != null && draft.getRemark() != null)
            touch(userId, articleId, null, sessionId, draft.getRemark(), false);
    }

    public void removeArticle(Long articleId) throws SQLException {
        db.withSession(
                c ->
                        db.repository(c, WebSite.class)
                                .updateWhere(
                                        WebSiteRepository.entry(null, null, null),
                                        List.of("value", "remark"),
                                        "name LIKE ? ESCAPE '!'",
                                        "ai!_session!_u%!_" + articleId + ":%"));
    }

    public void hideDraft(int userId, String sessionId) throws SQLException {
        WebSite row = row(userId, 0L, null, sessionId);
        if (row == null) return;
        update(row, WebSiteRepository.entry(null, null, null), List.of("remark"));
    }

    public List<AIChatModels.Session> draftSessions(int userId) throws SQLException {
        String pattern = escape(prefix(userId) + "0:") + "%";
        return db
                .withSession(
                        c ->
                                db.entities(
                                        c,
                                        WebSite.class,
                                        "WHERE name LIKE ? ESCAPE '!' AND value IS NOT NULL AND remark IS NOT NULL",
                                        pattern))
                .stream()
                .map(row -> json.fromJson(row.getValue(), AIChatModels.Session.class))
                .collect(java.util.stream.Collectors.toList());
    }

    private void update(WebSite expected, WebSite changes, List<String> columns)
            throws SQLException {
        if (db.withSession(
                        c ->
                                db.repository(c, WebSite.class)
                                        .updateWhere(
                                                changes,
                                                columns,
                                                "name=? AND version=?",
                                                expected.getName(),
                                                expected.getVersion()))
                != 1) throw new com.zrlog.admin.business.ai.exception.AIMessageSaveException();
    }

    private static String escape(String value) {
        return value.replace("!", "!!").replace("_", "!_").replace("%", "!%");
    }
}
