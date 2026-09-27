package com.zrlog.admin.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;
import java.util.Set;

public class McpWebSetupProvider implements WebSetupProvider {
    @Override public String name() { return "admin-mcp"; }
    @Override public int order() { return 160; }
    @Override public Set<String> requiredModules() { return Set.of("admin-access", "admin-content"); }
    @Override public WebSetup create(WebSetupContext context) { return new McpWebSetup(context); }
}
