package com.zrlog.admin.business.rest.request;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PasskeyCredential {

    private String id;
    private String rawId;
    private String type;
    private AuthenticatorResponse response;
    private Map<String, Object> clientExtensionResults = Collections.emptyMap();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRawId() {
        return rawId;
    }

    public void setRawId(String rawId) {
        this.rawId = rawId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public AuthenticatorResponse getResponse() {
        return response;
    }

    public void setResponse(AuthenticatorResponse response) {
        this.response = response;
    }

    public Map<String, Object> getClientExtensionResults() {
        return clientExtensionResults;
    }

    public void setClientExtensionResults(Map<String, Object> clientExtensionResults) {
        this.clientExtensionResults = clientExtensionResults;
    }

    public static class AuthenticatorResponse {

        private String clientDataJSON;
        private String attestationObject;
        private String authenticatorData;
        private String signature;
        private String userHandle;
        private List<String> transports = Collections.emptyList();

        public String getClientDataJSON() {
            return clientDataJSON;
        }

        public void setClientDataJSON(String clientDataJSON) {
            this.clientDataJSON = clientDataJSON;
        }

        public String getAttestationObject() {
            return attestationObject;
        }

        public void setAttestationObject(String attestationObject) {
            this.attestationObject = attestationObject;
        }

        public String getAuthenticatorData() {
            return authenticatorData;
        }

        public void setAuthenticatorData(String authenticatorData) {
            this.authenticatorData = authenticatorData;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public String getUserHandle() {
            return userHandle;
        }

        public void setUserHandle(String userHandle) {
            this.userHandle = userHandle;
        }

        public List<String> getTransports() {
            return transports;
        }

        public void setTransports(List<String> transports) {
            this.transports = transports;
        }
    }
}
