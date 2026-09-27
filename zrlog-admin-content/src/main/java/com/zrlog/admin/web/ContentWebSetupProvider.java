package com.zrlog.admin.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;
import java.util.Set;

public class ContentWebSetupProvider implements WebSetupProvider {
    @Override public String name() { return "admin-content"; }
    @Override public int order() { return 120; }
    @Override public Set<String> requiredModules() { return Set.of("admin", "admin-account"); }
    @Override public WebSetup create(WebSetupContext context) {
        return new ContentWebSetup(context.getZrLogConfig().getServerConfig().getRouter());
    }
}
