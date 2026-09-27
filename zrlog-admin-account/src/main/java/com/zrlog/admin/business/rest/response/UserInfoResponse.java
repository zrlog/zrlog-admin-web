package com.zrlog.admin.business.rest.response;

import com.zrlog.business.rest.response.CheckVersionResponse;

public class UserInfoResponse {

    private Integer userId;
    private String role;
    private java.util.List<String> actions;
    public java.util.List<String> getActions() { return actions; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) {
        this.role = role;
        this.actions = java.util.Arrays.stream(com.zrlog.data.security.AccountAction.values()).filter(a -> a.getRoles().contains(role)).map(com.zrlog.data.security.AccountAction::getId).collect(java.util.stream.Collectors.toList());
    }
    private String userName;
    private String header;
    private String key;
    private CheckVersionResponse lastVersion;
    private boolean mfaEnabled;

    public UserInfoResponse() {
    }

    public UserInfoResponse(String userName, String header, String key, CheckVersionResponse lastVersion) {
        this(userName, header, key, lastVersion, false);
    }

    public UserInfoResponse(String userName, String header, String key, CheckVersionResponse lastVersion, boolean mfaEnabled) {
        this.userName = userName;
        this.header = header;
        this.key = key;
        this.lastVersion = lastVersion;
        this.mfaEnabled = mfaEnabled;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public CheckVersionResponse getLastVersion() {
        return lastVersion;
    }

    public void setLastVersion(CheckVersionResponse lastVersion) {
        this.lastVersion = lastVersion;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }
}
