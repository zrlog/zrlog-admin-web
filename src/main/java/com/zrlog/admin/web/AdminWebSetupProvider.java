package com.zrlog.admin.web;

import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.business.service.AdminResourceImpl;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;

public class AdminWebSetupProvider implements WebSetupProvider {

    @Override
    public String name() {
        return "admin";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public WebSetup create(WebSetupContext context) {
        AdminResource adminResource = new AdminResourceImpl(context.getContextPath());
        return new AdminWebSetup(context.getZrLogConfig(), adminResource, context.getContextPath());
    }
}
