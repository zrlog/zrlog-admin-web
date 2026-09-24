package com.zrlog.admin.business.security;

import java.util.List;

/** OAuth wire names deliberately follow the OAuth specifications. */
public final class OAuthModels {
    private OAuthModels() { }
    public static class Client implements com.zrlog.common.Validator {
        public void doValid() { if (name == null || redirectUris == null) throw new com.zrlog.common.exception.ArgsException(); }
        public String clientId;
        public String name;
        public List<String> redirectUris;
        public boolean enabled = true;
    }
    public static class AuthorizationRequest {
        public String client_id;
        public String redirect_uri;
        public String response_type;
        public String scope;
        public String state;
        public String code_challenge;
        public String code_challenge_method;
        public String resource;
    }
    public static class Pending {
        public AuthorizationRequest request;
        public int userId;
        public String sessionHash;
        public String csrfHash;
    }
    public static class Consent {
        public String requestId;
        public String csrf;
        public String clientName;
        public String redirectUri;
        public String resource;
        public List<String> scopes;
        public List<String> availableScopes;
    }
    public static class Decision implements com.zrlog.common.Validator {
        public void doValid() { if (requestId == null || csrf == null) throw new com.zrlog.common.exception.ArgsException(); }
        public String requestId;
        public String csrf;
        public boolean approve;
        public List<String> scopes;
    }
    public static class Redirect { public String redirectUri; public Redirect(String uri) { redirectUri = uri; } }
    public static class TokenRequest {
        public String grant_type;
        public String client_id;
        public String code;
        public String redirect_uri;
        public String code_verifier;
        public String refresh_token;
        public String scope;
        public String resource;
    }
    public static class TokenResponse {
        public String access_token;
        public String token_type = "Bearer";
        public long expires_in;
        public String refresh_token;
        public String scope;
    }
    public static class Error {
        public String error;
        public Error(String error) { this.error = error; }
    }
    public static class Grant {
        public String id;
        public int userId;
        public String clientId;
        public String clientName;
        public String scope;
        public String resource;
        public long createdAt;
        public boolean revoked;
    }
    public static class Page {
        public List<Client> clients;
        public List<Grant> grants;
        public boolean administrator;
        public String issuer;
        public String resource;
    }
    public static class Revoke implements com.zrlog.common.Validator { public String id; public void doValid() { if (id == null || id.isEmpty()) throw new com.zrlog.common.exception.ArgsException(); } }
    public static class Identity {
        public int userId;
        public String role;
        public String clientId;
        public List<String> scopes;
    }
    public static class Metadata {
        public String issuer;
        public String authorization_endpoint;
        public String token_endpoint;
        public String revocation_endpoint;
        public List<String> response_types_supported = List.of("code");
        public List<String> grant_types_supported = List.of("authorization_code", "refresh_token");
        public List<String> token_endpoint_auth_methods_supported = List.of("none");
        public List<String> revocation_endpoint_auth_methods_supported = List.of("none");
        public List<String> code_challenge_methods_supported = List.of("S256");
        public List<String> scopes_supported;
        public boolean authorization_response_iss_parameter_supported = true;
    }
    public static class ResourceMetadata {
        public String resource;
        public List<String> authorization_servers;
        public List<String> scopes_supported = List.of("articles:read");
        public List<String> bearer_methods_supported = List.of("header");
    }
}
