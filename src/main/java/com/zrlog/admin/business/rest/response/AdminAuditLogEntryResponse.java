package com.zrlog.admin.business.rest.response;

public class AdminAuditLogEntryResponse {

    private Long timestamp;
    private String ip;
    private String action;
    private String type;
    private String content;
    private String os;
    private String browser;
    private Boolean crawler;

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public Boolean getCrawler() {
        return crawler;
    }

    public void setCrawler(Boolean crawler) {
        this.crawler = crawler;
    }
}
