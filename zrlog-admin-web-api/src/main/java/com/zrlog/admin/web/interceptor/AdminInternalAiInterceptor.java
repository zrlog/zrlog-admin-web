package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;

/** Only the explicitly registered plugin-internal AI route accepts plugin-core authentication. */
public class AdminInternalAiInterceptor extends AdminInterceptor {
    public static final String COMMENT_ANALYZE_PATH = "/api/admin/internal/ai/comment/analyze";

    @Override public boolean isHandleAble(HttpRequest request) {
        return COMMENT_ANALYZE_PATH.equals(request.getUri());
    }

    @Override public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        PluginTokenValidator.validate(request);
        doMethodInterceptor(request, response, getMethod(request));
        return false;
    }
}
