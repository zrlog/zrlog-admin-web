package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class PersonalDataCommentExportResponse {

    private String query;
    private long exportedAt;
    private long commentCount;
    private List<CommentEntry> comments = new ArrayList<>();

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public long getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(long exportedAt) {
        this.exportedAt = exportedAt;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public List<CommentEntry> getComments() {
        return comments;
    }

    public void setComments(List<CommentEntry> comments) {
        this.comments = comments;
    }

    public static class CommentEntry {

        private long id;
        private String userComment;
        private String userMail;
        private String userHome;
        private String userIp;
        private String userName;
        private String commTime;
        private long logId;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getUserComment() {
            return userComment;
        }

        public void setUserComment(String userComment) {
            this.userComment = userComment;
        }

        public String getUserMail() {
            return userMail;
        }

        public void setUserMail(String userMail) {
            this.userMail = userMail;
        }

        public String getUserHome() {
            return userHome;
        }

        public void setUserHome(String userHome) {
            this.userHome = userHome;
        }

        public String getUserIp() {
            return userIp;
        }

        public void setUserIp(String userIp) {
            this.userIp = userIp;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getCommTime() {
            return commTime;
        }

        public void setCommTime(String commTime) {
            this.commTime = commTime;
        }

        public long getLogId() {
            return logId;
        }

        public void setLogId(long logId) {
            this.logId = logId;
        }
    }
}
