package com.zrlog.admin.business.rest.response;

import java.util.List;

public class AdminDashboardWelcomeDataResponse {

    private String welcomeTip;
    private List<String> tips;
    private String versionInfo;

    public AdminDashboardWelcomeDataResponse() {
    }

    public AdminDashboardWelcomeDataResponse(String welcomeTip, List<String> tips, String versionInfo) {
        this.welcomeTip = welcomeTip;
        this.tips = tips;
        this.versionInfo = versionInfo;
    }

    public String getWelcomeTip() {
        return welcomeTip;
    }

    public void setWelcomeTip(String welcomeTip) {
        this.welcomeTip = welcomeTip;
    }

    public List<String> getTips() {
        return tips;
    }

    public void setTips(List<String> tips) {
        this.tips = tips;
    }

    public String getVersionInfo() {
        return versionInfo;
    }

    public void setVersionInfo(String versionInfo) {
        this.versionInfo = versionInfo;
    }
}
