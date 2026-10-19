package com.zrlog.admin.support;

import com.zrlog.admin.util.SystemInfoUtils;
import com.zrlog.common.ZrLogConfig;

public final class RuntimeInfoFixture {
    private RuntimeInfoFixture() { }

    public static SystemInfoUtils from(ZrLogConfig config) {
        return new SystemInfoUtils(() -> config != null && config.isInstalled(),
                () -> config == null ? null : config.getDataSource(),
                () -> config == null || config.getUpdater() == null ? null : config.getUpdater().execFile(),
                () -> config == null ? "" : config.getProgramUptime());
    }
}
