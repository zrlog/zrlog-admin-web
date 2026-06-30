package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.admin.business.rest.request.ReadCommentRequest;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.data.dto.CommentDTO;
import com.zrlog.model.Comment;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public class AdminCommentService {

    private final MessageCenterStateService messageCenterStateService = new MessageCenterStateService();

    public DeleteResponse delete(String[] ids) throws SQLException {
        List<Integer> commentIds = parseCommentIds(ids);
        if (commentIds.isEmpty()) {
            return new DeleteResponse(false);
        }
        String placeholders = placeholders(commentIds.size());
        Comment comment = new Comment();
        Object matchedCount = comment.queryFirstObj(
                "select count(1) from " + Comment.TABLE_NAME + " where commentId in (" + placeholders + ")",
                commentIds.toArray());
        boolean allExists = matchedCount instanceof Number && ((Number) matchedCount).intValue() == commentIds.size();
        boolean deleted = allExists && comment.execute(
                "delete from " + Comment.TABLE_NAME + " where commentId in (" + placeholders + ")",
                commentIds.toArray());
        if (deleted) {
            messageCenterStateService.markChanged();
        }
        return new DeleteResponse(deleted);
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

    public UpdateRecordResponse read(ReadCommentRequest commentRequest) {
        if (Objects.isNull(commentRequest)) {
            return new UpdateRecordResponse(false);
        }
        new Comment().doRead(commentRequest.getId());
        messageCenterStateService.markChanged();
        return new UpdateRecordResponse();
    }

    public PageData<CommentDTO> page(PageRequest pageable) throws SQLException {
        return new Comment().find(pageable);
    }

    public int countUnread() throws SQLException {
        Object count = new Comment().queryFirstObj(
                "select count(1) from " + Comment.TABLE_NAME + " where have_read = ?",
                false);
        if (count instanceof Number) {
            return ((Number) count).intValue();
        }
        return 0;
    }

    public void readAll() throws SQLException {
        boolean updated = new Comment().execute("update " + Comment.TABLE_NAME + " set have_read = ? where have_read = ?", true, false);
        if (updated) {
            messageCenterStateService.markChanged();
        }
    }
}
