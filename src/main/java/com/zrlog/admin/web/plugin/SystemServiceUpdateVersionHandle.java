package com.zrlog.admin.web.plugin;

import com.zrlog.common.vo.Version;

import java.util.Map;
import java.util.Objects;

public class SystemServiceUpdateVersionHandle implements UpdateVersionHandler {

    private String message;
    private boolean finish;
    private final Map<String, Object> blogRes;
    private final Version version;

    public SystemServiceUpdateVersionHandle(Map<String, Object> blogRes, Version version) {
        this.blogRes = blogRes;
        this.version = version;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public boolean isFinish() {
        return finish;
    }

    @Override
    public void doHandle() {
        if (Objects.nonNull(version) && version.getVersion().contains("-SNAPSHOT")) {
            String key = "/etc/init.d/zrlog upgrade";
            message = ((String) blogRes.get("systemServiceUpgradeTips")).replace(key, key + "-preview");
        } else {
            message = (String) blogRes.get("systemServiceUpgradeTips");
        }
        finish = true;
    }
}
