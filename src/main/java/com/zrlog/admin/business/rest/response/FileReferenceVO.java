package com.zrlog.admin.business.rest.response;

public class FileReferenceVO {

    private int logId;
    private String title;
    private String alias;
    private boolean thumbnail;
    private boolean content;

    public FileReferenceVO() {
    }

    public FileReferenceVO(int logId, String title, String alias) {
        this.logId = logId;
        this.title = title;
        this.alias = alias;
    }

    public int getLogId() {
        return logId;
    }

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public boolean isThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(boolean thumbnail) {
        this.thumbnail = thumbnail;
    }

    public boolean isContent() {
        return content;
    }

    public void setContent(boolean content) {
        this.content = content;
    }
}
