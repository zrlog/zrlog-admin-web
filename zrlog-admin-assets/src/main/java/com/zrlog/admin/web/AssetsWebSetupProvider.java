package com.zrlog.admin.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;
import java.util.Set;

public class AssetsWebSetupProvider implements WebSetupProvider {
    @Override public String name() { return "admin-assets"; }
    @Override public int order() { return 130; }
    @Override public Set<String> requiredModules() { return Set.of("admin", "admin-account", "admin-content"); }
    @Override public WebSetup create(WebSetupContext context) {
        return new AssetsWebSetup(context.getZrLogConfig().getServerConfig());
    }
}
