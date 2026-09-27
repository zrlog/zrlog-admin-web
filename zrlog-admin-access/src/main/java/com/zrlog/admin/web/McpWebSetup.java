package com.zrlog.admin.web;

import com.zrlog.admin.web.interceptor.McpInterceptor;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import com.zrlog.common.Constants;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;

/** MCP is a separately selectable transport inside external access, independent of AI. */
public class McpWebSetup implements WebSetup {
    private final WebSetupContext context;
    public McpWebSetup(WebSetupContext context) { this.context = context; }
    @Override public void setup() {
        var interceptors = context.getZrLogConfig().getServerConfig().getInterceptors();
        interceptors.add(interceptors.indexOf(OAuthInterceptor.class) + 1, McpInterceptor.class);
    }
    public static boolean enabled() {
        return Constants.zrLogConfig != null && Constants.zrLogConfig.getWebSetup(McpWebSetup.class) != null;
    }
}
