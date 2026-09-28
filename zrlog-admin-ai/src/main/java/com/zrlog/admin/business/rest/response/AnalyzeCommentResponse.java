package com.zrlog.admin.business.rest.response;

public class AnalyzeCommentResponse {
    private String verdict;
    private String reason;
    private String reply;
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}
