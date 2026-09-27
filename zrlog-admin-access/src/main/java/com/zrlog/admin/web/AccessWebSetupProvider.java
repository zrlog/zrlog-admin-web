package com.zrlog.admin.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;
import java.util.Set;

public class AccessWebSetupProvider implements WebSetupProvider {
    @Override public String name() { return "admin-access"; }
    @Override public int order() { return 150; }
    @Override public Set<String> requiredModules() { return Set.of("admin", "admin-account"); }
    @Override public WebSetup create(WebSetupContext context) { return new AccessWebSetup(context); }
}
