package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;

import java.util.function.Supplier;

/**
 * Only the explicitly registered plugin-internal AI route accepts plugin-core authentication.
 */
public class AdminInternalAiInterceptor extends AdminMethodInterceptor {
    public static final String COMMENT_ANALYZE_PATH = "/api/admin/internal/ai/comment/analyze";
    private final PluginTokenValidator pluginTokens;

    public AdminInternalAiInterceptor(PluginTokenValidator pluginTokens, Supplier<AdminActionDispatcher> dispatcher) {
        super(dispatcher);
        this.pluginTokens = pluginTokens;
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        return COMMENT_ANALYZE_PATH.equals(request.getUri());
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        pluginTokens.validate(request);
        doMethodInterceptor(request, response, getMethod(request));
        return false;
    }
}
