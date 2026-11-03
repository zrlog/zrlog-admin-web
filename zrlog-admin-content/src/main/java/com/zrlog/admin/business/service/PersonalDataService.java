package com.zrlog.admin.business.service;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.request.PersonalDataPreviewRequest;
import com.zrlog.admin.business.rest.response.PersonalDataCommentExportResponse;
import com.zrlog.admin.business.rest.response.PersonalDataPreviewResponse;
import com.zrlog.data.repository.CommentRepository;
import com.zrlog.data.repository.UserRepository;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.*;

public class PersonalDataService {
    private static final String COMMENT_MATCH_CONDITION =
            "(lower(coalesce(userMail, '')) = ?"
                    + " or lower(coalesce(userName, '')) = ?"
                    + " or lower(coalesce(userHome, '')) = ?"
                    + " or lower(coalesce(userIp, '')) = ?)";
    private final jakarta.inject.Provider<UserRepository> userProvider;
    private final jakarta.inject.Provider<CommentRepository> commentProvider;

    @Inject
    public PersonalDataService(
            jakarta.inject.Provider<UserRepository> userProvider,
            jakarta.inject.Provider<CommentRepository> commentProvider) {
        this.userProvider = userProvider;
        this.commentProvider = commentProvider;
    }

    public PersonalDataPreviewResponse preview(
            PersonalDataPreviewRequest request, int currentUserId) throws SQLException {
        String query = request.getQuery();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        CommentRepository comment = this.commentProvider.get();
        PersonalDataPreviewResponse response =
                comment.queryOne(
                        "select count(1) as commentCount,"
                                + " count(distinct logId) as commentArticleCount,"
                                + " max(commTime) as latestCommentTime"
                                + " from "
                                + CommentRepository.TABLE_NAME
                                + " where "
                                + COMMENT_MATCH_CONDITION,
                        PersonalDataPreviewResponse.class,
                        normalizedQuery,
                        normalizedQuery,
                        normalizedQuery,
                        normalizedQuery);

        response.setQuery(query);
        response.setLatestCommentTime(formatDate(response.getLatestCommentTime()));
        fillAdminMatch(response, query, currentUserId);
        return response;
    }

    public PersonalDataCommentExportResponse exportComments(PersonalDataPreviewRequest request)
            throws SQLException {
        String query = request.getQuery();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<PersonalDataCommentExportResponse.CommentEntry> rows =
                this.commentProvider
                        .get()
                        .queryList(
                                "select commentId as id,userComment,userMail,userHome,userIp,userName,commTime,logId"
                                        + " from "
                                        + CommentRepository.TABLE_NAME
                                        + " where "
                                        + COMMENT_MATCH_CONDITION
                                        + " order by commTime desc",
                                PersonalDataCommentExportResponse.CommentEntry.class,
                                normalizedQuery,
                                normalizedQuery,
                                normalizedQuery,
                                normalizedQuery);

        PersonalDataCommentExportResponse response = new PersonalDataCommentExportResponse();
        response.setQuery(query);
        response.setExportedAt(System.currentTimeMillis());
        response.setComments(toCommentEntries(rows));
        response.setCommentCount(response.getComments().size());
        return response;
    }

    List<PersonalDataCommentExportResponse.CommentEntry> toCommentEntries(
            List<PersonalDataCommentExportResponse.CommentEntry> rows) {
        for (var row : rows) {
            row.setCommTime(formatDate(row.getCommTime()));
            row.setUserComment(Objects.toString(row.getUserComment(), ""));
            row.setUserMail(Objects.toString(row.getUserMail(), ""));
            row.setUserHome(Objects.toString(row.getUserHome(), ""));
            row.setUserIp(Objects.toString(row.getUserIp(), ""));
            row.setUserName(Objects.toString(row.getUserName(), ""));
        }
        return rows;
    }

    private void fillAdminMatch(
            PersonalDataPreviewResponse response, String query, int currentUserId)
            throws SQLException {
        if (currentUserId <= 0) {
            return;
        }
        com.zrlog.model.User user = this.userProvider.get().loadById((long) currentUserId);
        if (Objects.isNull(user)) {
            return;
        }
        response.setAdminUserMatched(equalsIgnoreCase(query, user.getUserName()));
        response.setAdminEmailMatched(equalsIgnoreCase(query, user.getEmail()));
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
