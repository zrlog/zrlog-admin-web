package com.zrlog.admin.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;
import java.util.Set;

public class AdminUiWebSetupProvider implements WebSetupProvider {
    @Override public String name() { return "admin-ui"; }
    @Override public int order() { return 170; }
    @Override public Set<String> requiredModules() { return Set.of("admin"); }
    @Override public WebSetup create(WebSetupContext context) {
        return new AdminUiWebSetup(context.getZrLogConfig(), context.getContextPath());
    }
}
