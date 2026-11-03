package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.data.dto.CommentDTO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.repository.CommentRepository;
import com.zrlog.data.util.DateValueFormatUtils;
import com.zrlog.model.Comment;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Comment moderation queries; callers enforce account permissions. */
public class AdminCommentRepository extends CommentRepository {

    @Inject
    public AdminCommentRepository(DataSourceWrapper dataSource) {
        super(dataSource);
    }

    public PageData<CommentDTO> find(PageRequest page) {
        return find(page, null);
    }

    public PageData<CommentDTO> find(PageRequest page, Integer privateOwnerId) {
        String sql = "select commentId as id,userComment,header,commTime,userMail,userHome,userIp,userName,hide,logId from " + tableName + (privateOwnerId == null ? "" : " where logId in (select logId from log where privacy=false or userId=?)") + " order by commTime desc";
        PageData<CommentDTO> commentDTOPageData = queryPageData(sql, page, privateOwnerId == null ? new Object[0] : new Object[]{privateOwnerId}, CommentDTO.class);
        commentDTOPageData.getRows().forEach(e -> {
            e.setCommTime(DateValueFormatUtils.format(e.getCommTime(), "yyyy-MM-dd HH:mm:ss"));
        });
        return commentDTOPageData;
    }

    public Long count() throws SQLException {
        String sql = "select count(1) from " + tableName;
        return ((Number) queryFirstObj(sql)).longValue();
    }

    public Long countToDayComment() throws SQLException {
        String sql = "select count(1) from " + tableName + " where commTime>?";
        return ((Number) queryFirstObj(sql, new SimpleDateFormat("yyyy-MM-dd").format(new Date()))).longValue();
    }

    public List<Comment> findHaveReadIsFalse() throws SQLException {
        return select("WHERE have_read=? ORDER BY commTime DESC", false);
    }

    public void doRead(long id) {
        try {
            execute("UPDATE comment SET have_read=?,updatedAt=?,version=version+1 WHERE commentId=? AND have_read=?",
                    true, new java.sql.Timestamp(System.currentTimeMillis()).toString(), id, false);
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }
}
