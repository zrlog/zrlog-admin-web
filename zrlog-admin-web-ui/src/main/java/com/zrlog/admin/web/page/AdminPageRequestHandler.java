package com.zrlog.admin.web.page;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.ServerSideDataResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.AdminPageService;
import com.zrlog.admin.web.config.AdminAccountPages;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.security.AdminRouteAuthorizer;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.rest.response.StandardResponse;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.plugin.BaseStaticSitePlugin;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

/** Resolves incoming identity and dispatches HTTP page APIs before rendering their explicit result. */
@Singleton
public final class AdminPageRequestHandler {
    private final AdminRequestIdentity identities;
    private final AdminRouteAuthorizer routes;
    private final AdminPageService renderer;
    private final AdminPageDataFactory pages;

    @Inject
    public AdminPageRequestHandler(AdminRequestIdentity identities, AdminRouteAuthorizer routes,
                                   AdminPageService renderer, AdminPageDataFactory pages) {
        this.identities = identities;
        this.routes = routes;
        this.renderer = renderer;
        this.pages = pages;
    }

    public String buildHtml(HttpRequest request, HttpResponse response, InputStream html) throws Throwable {
        return renderer.buildHtml(request, html, serverSide(request.getUri(), request, response));
    }

    public ServerSideDataResponse<Object> serverSide(String uri, HttpRequest request, HttpResponse response) throws Throwable {
        var credential = BaseStaticSitePlugin.isStaticPluginRequest(request) ? null : AdminTokenThreadLocal.getUser();
        var actor = credential == null ? null : identities.account(credential);
        return load(actor, credential == null ? null : credential.getSessionId(), uri, request, response);
    }

    public AdminPageDataResponse<ServerSideDataResponse<Object>> response(
            String uri, HttpRequest request, HttpResponse response) throws Throwable {
        var credential = BaseStaticSitePlugin.isStaticPluginRequest(request) ? null : AdminTokenThreadLocal.getUser();
        var actor = credential == null ? null : identities.account(credential);
        var data = load(actor, credential == null ? null : credential.getSessionId(), uri, request, response);
        return pages.response(actor, data);
    }

    private ServerSideDataResponse<Object> load(AccountAccess actor, String sessionId, String uri,
                                                 HttpRequest request, HttpResponse response) throws Throwable {
        if (actor == null) return renderer.serverSide(null, null, uri, request, null, pages.title(request.getUri()));
        var method = request.getRequestConfig().getRouter().getMethod(AdminAccountPages.apiUri(uri), request.getMethod());
        // A disabled feature keeps its authenticated shell so the frontend can display its 404.
        if (method == null) return renderer.serverSide(actor, sessionId, uri, request, null, pages.title(uri));
        routes.checkRoute(actor, method, request);
        StandardResponse result;
        try {
            result = (StandardResponse) method.invoke(Controller.buildController(method, request, response));
        } catch (InvocationTargetException error) {
            throw error.getTargetException();
        }
        Object data = null;
        String title = pages.title(request.getUri());
        if (result == null) data = new Object();
        else if (result instanceof AdminPageDataResponse<?>) {
            var page = (AdminPageDataResponse<?>) result;
            data = page.getData();
            title = AdminAccountPages.PAGE_APIS.containsKey(uri) ? pages.title(uri) : page.getDocumentTitle();
        }
        return renderer.serverSide(actor, sessionId, uri, request, data, title);
    }
}
