package com.zrlog.admin.web.config;

import java.util.Map;
import java.util.Set;

/** Account page URLs are independent of the existing admin API endpoints. */
public final class AdminAccountPages {
    private AdminAccountPages() { }

    public static final String AUTHORIZE = "/admin/user/applications/authorize";
    public static final Map<String, String> PAGE_APIS = Map.ofEntries(
            Map.entry("/admin/user", "/api/admin/user"),
            Map.entry("/admin/user/preferences/appearance", "/api/admin/user/preferences"),
            Map.entry("/admin/user/preferences/writing", "/api/admin/user/preferences"),
            Map.entry("/admin/user/preferences/assistant", "/api/admin/user/preferences"),
            Map.entry("/admin/user/preferences/session", "/api/admin/user/preferences"),
            Map.entry("/admin/user/security", "/api/admin/account-security"),
            Map.entry("/admin/user/applications/tokens", "/api/admin/oauth"),
            Map.entry("/admin/user/applications/grants", "/api/admin/oauth"),
            Map.entry("/admin/user/applications/clients", "/api/admin/oauth/clients"),
            Map.entry(AUTHORIZE, "/api/admin/oauth/authorize"),
            Map.entry("/admin/website/members", "/api/admin/members"));
    private static final Set<String> SENSITIVE_PAGES = Set.of(
            "/admin/user/applications/tokens", "/admin/user/applications/grants",
            "/admin/user/applications/clients", AUTHORIZE, "/admin/website/members");

    public static boolean isSensitive(String uri) { return SENSITIVE_PAGES.contains(uri); }
    public static String apiUri(String uri) { return PAGE_APIS.getOrDefault(uri, "/api" + uri); }

    public static String titleUri(String uri) {
        switch (uri) {
            case "/api/admin/account-security": return "/admin/user/security";
            case "/api/admin/user/preferences": return "/admin/user/preferences/appearance";
            case "/api/admin/oauth": return "/admin/user/applications/tokens";
            case "/api/admin/oauth/clients": return "/admin/user/applications/clients";
            case "/api/admin/oauth/authorize": return AUTHORIZE;
            case "/api/admin/members": return "/admin/website/members";
            default: return uri.replaceFirst("^/api", "");
        }
    }
}
