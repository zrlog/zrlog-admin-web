package com.zrlog.admin.web.config;

import java.util.Map;
import java.util.Set;

/** Account page URLs are independent of the existing admin API endpoints. */
public final class AdminAccountPages {
    private AdminAccountPages() { }

    public static final String AUTHORIZE = "/admin/user/applications/authorize";
    public static final Map<String, String> PAGE_APIS = Map.of(
            "/admin/user", "/api/admin/user",
            "/admin/user/preferences", "/api/admin/user",
            "/admin/user/security", "/api/admin/account-security",
            "/admin/user/applications", "/api/admin/user",
            AUTHORIZE, "/api/admin/oauth/authorize",
            "/admin/website/members", "/api/admin/members",
            "/admin/user/permissions", "/api/admin/access");
    private static final Set<String> SENSITIVE_PAGES = Set.of(
            "/admin/user/applications", AUTHORIZE, "/admin/website/members", "/admin/user/permissions");

    public static boolean isSensitive(String uri) { return SENSITIVE_PAGES.contains(uri); }
    public static String apiUri(String uri) { return PAGE_APIS.getOrDefault(uri, "/api" + uri); }

    public static String titleUri(String uri) {
        switch (uri) {
            case "/api/admin/account-security": return "/admin/user/security";
            case "/api/admin/oauth": return "/admin/user/applications";
            case "/api/admin/oauth/authorize": return AUTHORIZE;
            case "/api/admin/members": return "/admin/website/members";
            case "/api/admin/access": return "/admin/user/permissions";
            default: return uri.replaceFirst("^/api", "");
        }
    }
}
