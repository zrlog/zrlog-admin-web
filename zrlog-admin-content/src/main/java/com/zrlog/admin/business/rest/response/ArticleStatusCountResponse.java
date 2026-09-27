package com.zrlog.admin.business.rest.response;

import java.io.Serializable;

public class ArticleStatusCountResponse implements Serializable {

    private long total;
    private long draft;
    private long privateCount;
    private long published;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getDraft() {
        return draft;
    }

    public void setDraft(long draft) {
        this.draft = draft;
    }

    public long getPrivateCount() {
        return privateCount;
    }

    public void setPrivateCount(long privateCount) {
        this.privateCount = privateCount;
    }

    public long getPublished() {
        return published;
    }

    public void setPublished(long published) {
        this.published = published;
    }
}
