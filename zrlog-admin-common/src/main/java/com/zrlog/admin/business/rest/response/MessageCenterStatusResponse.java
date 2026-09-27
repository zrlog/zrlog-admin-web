package com.zrlog.admin.business.rest.response;

public class MessageCenterStatusResponse {

    private long revision;
    private boolean hasUnread;

    public MessageCenterStatusResponse() {
    }

    public MessageCenterStatusResponse(long revision, boolean hasUnread) {
        this.revision = revision;
        this.hasUnread = hasUnread;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public boolean isHasUnread() {
        return hasUnread;
    }

    public void setHasUnread(boolean hasUnread) {
        this.hasUnread = hasUnread;
    }
}
