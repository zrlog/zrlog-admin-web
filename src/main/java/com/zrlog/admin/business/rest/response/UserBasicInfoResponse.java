package com.zrlog.admin.business.rest.response;

import java.util.Set;

public class UserBasicInfoResponse extends UserInfoResponse {

    private String email;
    private Set<String> cacheableApiUris;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getCacheableApiUris() {
        return cacheableApiUris;
    }

    public void setCacheableApiUris(Set<String> cacheableApiUris) {
        this.cacheableApiUris = cacheableApiUris;
    }
}
