package com.zrlog.admin.business.audit;

/** Immutable attribution and source metadata. Carries no request, credential or service. */
public final class AdminAuditContext {
    private final int actorUserId;
    private final String ip;
    private final String uri;
    private final String userAgent;

    public AdminAuditContext(int actorUserId, String ip, String uri, String userAgent) {
        this.actorUserId = actorUserId;
        this.ip = ip;
        this.uri = uri;
        this.userAgent = userAgent;
    }

    public int getActorUserId() { return actorUserId; }
    public String getIp() { return ip; }
    public String getUri() { return uri; }
    public String getUserAgent() { return userAgent; }
}
