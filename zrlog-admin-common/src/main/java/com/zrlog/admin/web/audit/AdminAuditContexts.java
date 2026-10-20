package com.zrlog.admin.web.audit;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.blog.web.util.WebTools;

/**
 * Extract HTTP metadata while the request is available; identity comes from the caller.
 */
public final class AdminAuditContexts {
    private AdminAuditContexts() {
    }

    public static AdminAuditContext from(HttpRequest request, int actorUserId) {
        return new AdminAuditContext(actorUserId, WebTools.getRealIp(request),
                request.getUri(), request.getHeader("User-Agent"));
    }
}
