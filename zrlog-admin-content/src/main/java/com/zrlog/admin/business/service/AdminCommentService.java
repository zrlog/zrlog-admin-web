package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.ReadCommentRequest;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.data.dto.CommentDTO;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.Comment;

import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public class AdminCommentService {
    private final jakarta.inject.Provider<Comment> commentProvider;

    private final AccountIdentityService identities;
    private final AccountPermissionService permissionService;

    @Inject
    public AdminCommentService(
            AccountPermissionService permissionService,
            AccountIdentityService identities,
            MessageCenterStateService messageCenterStateService,
            jakarta.inject.Provider<Comment> commentProvider) {
        this.messageCenterStateService = messageCenterStateService;
        this.commentProvider = commentProvider;

        this.permissionService = permissionService;
        this.identities = identities;
    }

    private final MessageCenterStateService messageCenterStateService;

    public DeleteResponse delete(AccountAccess actor, String[] ids) throws SQLException {
        actor = authorize(actor);
        List<Integer> commentIds = parseCommentIds(ids);
        if (commentIds.isEmpty()) {
            return new DeleteResponse(false);
        }
        for (Integer id : commentIds) checkArticle(actor, id);
        String placeholders = placeholders(commentIds.size());
        Comment comment = this.commentProvider.get();
        Object matchedCount = comment.queryFirstObj(
                "select count(1) from " + Comment.TABLE_NAME + " where commentId in (" + placeholders + ")",
                commentIds.toArray());
        boolean allExists = matchedCount instanceof Number && ((Number) matchedCount).intValue() == commentIds.size();
        boolean deleted = allExists && mutate(actor,
                "delete from " + Comment.TABLE_NAME + " where commentId in (" + placeholders + ")",
                commentIds.toArray());
        if (deleted) {
            messageCenterStateService.markChanged();
        }
        return new DeleteResponse(deleted);
    }

    private void checkArticle(AccountAccess actor, long commentId) {
        try {
            if (actor.isAdministrator()) return;
            Object id =
                    this.commentProvider
                            .get()
                            .queryFirstObj(
                                    "select logId from comment where commentId=?", commentId);
            if (!(id instanceof Number)) throw new PermissionErrorException();
            permissionService.readArticle(actor, ((Number) id).longValue());
        } catch (SQLException e) {
            throw new PermissionErrorException();
        }
    }

    List<Integer> parseCommentIds(String[] ids) {
        Set<Integer> idSet = new LinkedHashSet<>();
        for (String id : ids) {
            if (Objects.isNull(id)) {
                continue;
            }
            String trimmed = id.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            idSet.add(Integer.parseInt(trimmed));
        }
        return new ArrayList<>(idSet);
    }

    String placeholders(int size) {
        StringJoiner sj = new StringJoiner(",");
        for (int i = 0; i < size; i++) {
            sj.add("?");
        }
        return sj.toString();
    }

    public UpdateRecordResponse read(AccountAccess actor, ReadCommentRequest commentRequest) throws SQLException {
        if (Objects.isNull(commentRequest)) {
            return new UpdateRecordResponse(false);
        }
        actor = authorize(actor);
        checkArticle(actor, commentRequest.getId());
        mutate(actor, "update comment set have_read=? where commentId=?", true, commentRequest.getId());
        messageCenterStateService.markChanged();
        return new UpdateRecordResponse();
    }

    public PageData<CommentDTO> page(AccountAccess actor, PageRequest pageable) throws SQLException {
        actor = authorize(actor);
        return this.commentProvider
                .get()
                .find(pageable, actor.isAdministrator() ? null : actor.getUserId());
    }

    public int countUnread() throws SQLException {
        Object count =
                this.commentProvider
                        .get()
                        .queryFirstObj(
                                "select count(1) from "
                                        + Comment.TABLE_NAME
                                        + " where have_read = ?",
                                false);
        if (count instanceof Number) {
            return ((Number) count).intValue();
        }
        return 0;
    }

    public void readAll(AccountAccess actor) throws SQLException {
        actor = authorize(actor);
        boolean updated = mutate(actor, "update comment set have_read=? where have_read=?", true, false);
        if (updated) {
            messageCenterStateService.markChanged();
        }
    }

    private AccountAccess authorize(AccountAccess actor) {
        actor = identities.revalidate(actor);
        permissionService.require(actor, AccountAction.COMMENT_MANAGE);
        return actor;
    }

    private boolean mutate(AccountAccess actor, String sql, Object... parameters) throws SQLException {
        identities.revalidate(actor);
        List<Object> values = new ArrayList<>(Arrays.asList(parameters));
        if (!actor.isAdministrator()) {
            sql += " and logId in (select logId from log where privacy=false or userId=?)";
            values.add(actor.getUserId());
        }
        // Keep revocation authoritative even when WebAPI executes a later independent statement.
        sql += " and exists (select 1 from user actor where actor.userId=? and actor.role=?"
                + " and actor.enabled=? and actor.authVersion=?)";
        values.add(actor.getUserId());
        values.add(actor.getRole());
        values.add(true);
        values.add(actor.getAuthVersion());
        boolean changed = commentProvider.get().execute(sql, values.toArray());
        if (!changed) identities.revalidate(actor);
        return changed;
    }
}
