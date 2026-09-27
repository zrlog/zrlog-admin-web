package com.zrlog.admin.business.service;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.request.PersonalDataPreviewRequest;
import com.zrlog.admin.business.rest.response.PersonalDataCommentExportResponse;
import com.zrlog.admin.business.rest.response.PersonalDataPreviewResponse;
import com.zrlog.model.Comment;
import com.zrlog.model.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class PersonalDataService {

    private static final String COMMENT_MATCH_CONDITION =
            "(lower(coalesce(userMail, '')) = ?"
                    + " or lower(coalesce(userName, '')) = ?"
                    + " or lower(coalesce(userHome, '')) = ?"
                    + " or lower(coalesce(userIp, '')) = ?)";

    public PersonalDataPreviewResponse preview(PersonalDataPreviewRequest request, int currentUserId)
            throws SQLException {
        String query = request.getQuery();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        Comment comment = new Comment();
        Map<String, Object> commentStats = comment.queryFirstWithParams(
                "select count(1) as commentCount,"
                        + " count(distinct logId) as commentArticleCount,"
                        + " max(commTime) as latestCommentTime"
                        + " from " + Comment.TABLE_NAME
                        + " where " + COMMENT_MATCH_CONDITION,
                normalizedQuery, normalizedQuery, normalizedQuery, normalizedQuery);

        PersonalDataPreviewResponse response = new PersonalDataPreviewResponse();
        response.setQuery(query);
        response.setCommentCount(toLong(commentStats.get("commentCount")));
        response.setCommentArticleCount(toLong(commentStats.get("commentArticleCount")));
        response.setLatestCommentTime(formatDate(commentStats.get("latestCommentTime")));
        fillAdminMatch(response, query, currentUserId);
        return response;
    }

    public PersonalDataCommentExportResponse exportComments(PersonalDataPreviewRequest request) throws SQLException {
        String query = request.getQuery();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = new Comment().queryListWithParams(
                "select commentId as id,userComment,userMail,userHome,userIp,userName,commTime,logId"
                        + " from " + Comment.TABLE_NAME
                        + " where " + COMMENT_MATCH_CONDITION
                        + " order by commTime desc",
                normalizedQuery, normalizedQuery, normalizedQuery, normalizedQuery);

        PersonalDataCommentExportResponse response = new PersonalDataCommentExportResponse();
        response.setQuery(query);
        response.setExportedAt(System.currentTimeMillis());
        response.setComments(toCommentEntries(rows));
        response.setCommentCount(response.getComments().size());
        return response;
    }

    List<PersonalDataCommentExportResponse.CommentEntry> toCommentEntries(List<Map<String, Object>> rows) {
        List<PersonalDataCommentExportResponse.CommentEntry> entries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            PersonalDataCommentExportResponse.CommentEntry entry =
                    new PersonalDataCommentExportResponse.CommentEntry();
            entry.setId(toLong(row.get("id")));
            entry.setUserComment(toString(row.get("userComment")));
            entry.setUserMail(toString(row.get("userMail")));
            entry.setUserHome(toString(row.get("userHome")));
            entry.setUserIp(toString(row.get("userIp")));
            entry.setUserName(toString(row.get("userName")));
            entry.setCommTime(formatDate(row.get("commTime")));
            entry.setLogId(toLong(row.get("logId")));
            entries.add(entry);
        }
        return entries;
    }

    private void fillAdminMatch(PersonalDataPreviewResponse response, String query, int currentUserId)
            throws SQLException {
        if (currentUserId <= 0) {
            return;
        }
        Map<String, Object> user = new User().loadById(currentUserId);
        if (Objects.isNull(user)) {
            return;
        }
        response.setAdminUserMatched(equalsIgnoreCase(query, user.get("userName")));
        response.setAdminEmailMatched(equalsIgnoreCase(query, user.get("email")));
    }

    boolean equalsIgnoreCase(String query, Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        String text = (String) value;
        if (StringUtils.isEmpty(text)) {
            return false;
        }
        return query.equalsIgnoreCase(text.trim());
    }

    long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0;
    }

    String toString(Object value) {
        if (Objects.isNull(value)) {
            return "";
        }
        return String.valueOf(value);
    }

    String formatDate(Object value) {
        if (Objects.isNull(value)) {
            return "";
        }
        return ResultValueConvertUtils.formatDate(value, "yyyy-MM-dd HH:mm:ss");
    }
}
