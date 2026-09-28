package com.zrlog.admin.web.interceptor;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.exception.ArgsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Shared authentication for requests from the running plugin-core process. */
public final class PluginTokenValidator {
    private PluginTokenValidator() { }

    public static void validate(HttpRequest request) {
        String requestToken = request.getHeader("X-Plugin-Token");
        if (StringUtils.isEmpty(requestToken)) {
            throw new ArgsException("missing_token");
        }
        PluginCorePlugin plugin = Constants.zrLogConfig.getPlugin(PluginCorePlugin.class);
        String expected = plugin == null ? null : plugin.getToken();
        if (StringUtils.isEmpty(expected) || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), requestToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ArgsException("token");
        }
    }
}
