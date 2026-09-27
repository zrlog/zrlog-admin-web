package com.zrlog.admin.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;
import java.util.Set;

public class AIWebSetupProvider implements WebSetupProvider {
    @Override public String name() { return "admin-ai"; }
    @Override public int order() { return 140; }
    @Override public Set<String> requiredModules() { return Set.of("admin", "admin-content", "admin-assets"); }
    @Override public WebSetup create(WebSetupContext context) {
        return new AIWebSetup(context.getZrLogConfig().getServerConfig().getRouter());
    }

}
