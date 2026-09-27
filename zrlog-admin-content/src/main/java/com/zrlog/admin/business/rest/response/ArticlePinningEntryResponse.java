package com.zrlog.admin.business.rest.response;

public class ArticlePinningEntryResponse {

    private Long logId;
    private String title;
    private Long sticky;

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getSticky() {
        return sticky;
    }

    public void setSticky(Long sticky) {
        this.sticky = sticky;
    }
}
