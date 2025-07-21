package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.admin.business.rest.request.ReadCommentRequest;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.data.dto.CommentDTO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.model.Comment;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AdminCommentService {

    public DeleteResponse delete(String[] ids) throws SQLException {
        boolean deleted = Arrays.stream(ids).allMatch(e -> {
            try {
                return new Comment().deleteById(Integer.parseInt(e));
            } catch (SQLException ex) {
                throw new DAOException(ex);
            }
        });
        return new DeleteResponse(deleted);
    }

    public UpdateRecordResponse read(ReadCommentRequest commentRequest) {
        if (Objects.isNull(commentRequest)) {
            return new UpdateRecordResponse(false);
        }
        new Comment().doRead(commentRequest.getId());
        return new UpdateRecordResponse();
    }

    public PageData<CommentDTO> page(PageRequest pageable) throws SQLException {
        return new Comment().find(pageable);
    }

    public int countUnread() throws SQLException {
        return new Comment().findHaveReadIsFalse().size();
    }

    public void readAll() throws SQLException {
        List<Map<String, Object>> unreadComments = new Comment().findHaveReadIsFalse();
        for (Map<String, Object> unreadComment : unreadComments) {
            Object id = unreadComment.get("id");
            if (id instanceof Number) {
                new Comment().doRead(((Number) id).longValue());
            }
        }
    }
}
