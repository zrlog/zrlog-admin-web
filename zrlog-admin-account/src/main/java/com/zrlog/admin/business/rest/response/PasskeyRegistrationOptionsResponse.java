package com.zrlog.admin.business.rest.response;

import java.util.Collections;
import java.util.List;

public class PasskeyRegistrationOptionsResponse {

    private String challenge;
    private RelyingParty rp;
    private User user;
    private List<CredentialParameter> pubKeyCredParams = Collections.singletonList(new CredentialParameter());
    private long timeout = 60000;
    private List<PasskeyCredentialDescriptor> excludeCredentials = Collections.emptyList();
    private AuthenticatorSelection authenticatorSelection = new AuthenticatorSelection();
    private String attestation = "none";

    public PasskeyRegistrationOptionsResponse(String challenge, RelyingParty rp, User user,
                                              List<PasskeyCredentialDescriptor> excludeCredentials) {
        this.challenge = challenge;
        this.rp = rp;
        this.user = user;
        this.excludeCredentials = excludeCredentials;
    }

    public String getChallenge() {
        return challenge;
    }

    public RelyingParty getRp() {
        return rp;
    }

    public User getUser() {
        return user;
    }

    public List<CredentialParameter> getPubKeyCredParams() {
        return pubKeyCredParams;
    }

    public long getTimeout() {
        return timeout;
    }

    public List<PasskeyCredentialDescriptor> getExcludeCredentials() {
        return excludeCredentials;
    }

    public AuthenticatorSelection getAuthenticatorSelection() {
        return authenticatorSelection;
    }

    public String getAttestation() {
        return attestation;
    }

    public static class RelyingParty {
        private String id;
        private String name;

        public RelyingParty(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static class User {
        private String id;
        private String name;
        private String displayName;

        public User(String id, String name, String displayName) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class CredentialParameter {
        private String type = "public-key";
        private int alg = -7;

        public String getType() {
            return type;
        }

        public int getAlg() {
            return alg;
        }
    }

    public static class AuthenticatorSelection {
        private String residentKey = "required";
        private boolean requireResidentKey = true;
        private String userVerification = "required";

        public String getResidentKey() {
            return residentKey;
        }

        public boolean isRequireResidentKey() {
            return requireResidentKey;
        }

        public String getUserVerification() {
            return userVerification;
        }
    }
}
