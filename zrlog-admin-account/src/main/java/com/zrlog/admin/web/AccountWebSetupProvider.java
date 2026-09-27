package com.zrlog.admin.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;
import java.util.Set;

public class AccountWebSetupProvider implements WebSetupProvider {
    @Override public String name() { return "admin-account"; }
    @Override public int order() { return 110; }
    @Override public Set<String> requiredModules() { return Set.of("admin"); }
    @Override public WebSetup create(WebSetupContext context) {
        return new AccountWebSetup(context.getZrLogConfig().getServerConfig().getRouter());
    }
}
