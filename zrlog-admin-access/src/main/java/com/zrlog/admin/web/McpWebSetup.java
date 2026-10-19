package com.zrlog.admin.web;

import com.zrlog.admin.web.interceptor.McpInterceptor;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;

/** MCP is a separately selectable transport inside external access, independent of AI. */
public class McpWebSetup implements WebSetup {
    private final WebSetupContext context;
    public McpWebSetup(WebSetupContext context) { this.context = context; }
    @Override public void setup() {
        context.getZrLogConfig().getServerConfig().getBindings().interceptor(McpInterceptor.class,
                r -> new McpInterceptor(() -> r.get(com.zrlog.admin.business.knowledge.ContentToolFactory.class), r.get(com.zrlog.admin.business.service.OAuthService.class), () -> {
                    var provider = context.getZrLogConfig().getWebSetup(com.zrlog.web.WebSiteInfoProvider.class);
                    var site = provider == null ? null : provider.getPublicWebSiteInfo();
                    return site == null ? "" : site.getTitle();
                }));
        var interceptors = context.getZrLogConfig().getServerConfig().getInterceptors();
        interceptors.add(interceptors.indexOf(OAuthInterceptor.class) + 1, McpInterceptor.class);
    }
}
