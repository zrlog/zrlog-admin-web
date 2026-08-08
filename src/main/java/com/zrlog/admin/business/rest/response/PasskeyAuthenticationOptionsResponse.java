package com.zrlog.admin.business.rest.response;

import java.util.Collections;
import java.util.List;

public class PasskeyAuthenticationOptionsResponse {

    private String challenge;
    private long timeout = 60000;
    private String rpId;
    private List<PasskeyCredentialDescriptor> allowCredentials = Collections.emptyList();
    private String userVerification = "required";

    public PasskeyAuthenticationOptionsResponse(String challenge, String rpId) {
        this.challenge = challenge;
        this.rpId = rpId;
    }

    public String getChallenge() {
        return challenge;
    }

    public long getTimeout() {
        return timeout;
    }

    public String getRpId() {
        return rpId;
    }

    public List<PasskeyCredentialDescriptor> getAllowCredentials() {
        return allowCredentials;
    }

    public String getUserVerification() {
        return userVerification;
    }
}
