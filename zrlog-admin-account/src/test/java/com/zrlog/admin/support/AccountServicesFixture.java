package com.zrlog.admin.support;

import com.hibegin.common.dao.DAO;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.account.DaggerAccountComponent;
import com.zrlog.admin.web.token.AdminTokenService;
import com.zrlog.business.di.DaggerServiceComponent;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.util.WebSiteUtils;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

/** Adapts existing database fixtures to the same generated graph used by the application. */
public final class AccountServicesFixture {
    private AccountServicesFixture() {}

    public static AccountComponent component() {
        return component(Collections::emptySet);
    }

    public static AccountComponent component(Supplier<Set<String>> cacheableUris) {
        var data = DaggerDataComponent.factory().create(DAO::getDefaultDataSource, () -> true);
        var services =
                DaggerServiceComponent.factory()
                        .create(data, () -> true, () -> null, () -> null, Collections::emptyList);
        var tokens =
                new AdminTokenService(60, () -> true, () -> false, data::user, data::preferences);
        return DaggerAccountComponent.factory()
                .create(
                        data,
                        services,
                        tokens,
                        () -> true,
                        () -> null,
                        cacheableUris,
                        () -> WebSiteUtils.fillDefaultInfo(new PublicWebSiteInfo()),
                        () -> "test",
                        () -> 18080,
                        () -> "");
    }
}
