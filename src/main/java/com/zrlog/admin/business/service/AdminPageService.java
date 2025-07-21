package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminApiPageDataStandardResponse;
import com.zrlog.admin.business.rest.response.ServerSideDataResponse;
import com.zrlog.admin.business.rest.response.UserInfoResponse;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.common.TokenService;
import com.zrlog.common.rest.response.StandardResponse;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.I18nUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

public class AdminPageService {

    public String buildHtml(HttpRequest request, HttpResponse httpResponse, InputStream htmlInputStream) throws Throwable {
        Document document = Jsoup.parse(IOUtil.getStringInputStream(htmlInputStream));
        //clean history
        document.body().removeClass("dark");
        document.body().removeClass("light");
        Objects.requireNonNull(document.selectFirst("base")).attr("href", "/");
        PublicWebSiteInfo publicWebSiteInfo = AdminConstants.getPublicWebSiteInfo();
        document.body().addClass(Objects.equals(publicWebSiteInfo.getAdmin_darkMode(), true) ? "dark" : "light");
        Element htmlElement = document.selectFirst("html");
        if (Objects.nonNull(htmlElement)) {
            htmlElement.attr("lang", I18nUtil.getCurrentLocale().split("_")[0]);
        }
        Elements select = document.head().select("meta[name=theme-color]");
        if (!select.isEmpty()) {
            Element first = select.first();
            if (Objects.nonNull(first)) {
                first.attr("content", publicWebSiteInfo.getAdmin_color_primary());
            }
        }
        String adminStaticResourceHostByWebSite = AdminWebTools.getAdminStaticResourceBaseUrlByWebSite(request);

        Elements manifest = document.head().select("link[rel=manifest]");
        if (!manifest.isEmpty()) {
            Element manifestElement = manifest.first();
            if (Objects.nonNull(manifestElement)) {
                //login page, disable pwa
                if (request.getUri().startsWith(TokenService.ADMIN_LOGIN_URI_PATH)) {
                    manifest.remove();
                } else {
                    fillToRealLink(request, adminStaticResourceHostByWebSite, manifestElement);
                }
            }
        }

        Elements linkManifest = document.head().select("link[rel=stylesheet]");
        if (!manifest.isEmpty()) {
            for (Element link : linkManifest) {
                if (StringUtils.isNotEmpty(adminStaticResourceHostByWebSite) && !BaseStaticSitePlugin.isStaticPluginRequest(request)) {
                    link.attr("href", link.attr("href").replaceFirst("/", adminStaticResourceHostByWebSite + "/"));
                } else {
                    link.attr("href", link.attr("href").replaceFirst("/", WebTools.getHomeUrl(request)));
                }
            }
        }
        Elements scripts = document.head().select("script");
        if (!scripts.isEmpty()) {
            scripts.forEach(script -> {
                if (StringUtils.isNotEmpty(adminStaticResourceHostByWebSite) && !BaseStaticSitePlugin.isStaticPluginRequest(request)) {
                    script.attr("src", script.attr("src").replaceFirst("/", adminStaticResourceHostByWebSite + "/"));
                } else {
                    script.attr("src", script.attr("src").replaceFirst("/", WebTools.getHomeUrl(request)));
                }
            });
        }

        Elements favicon = document.head().select("link[rel=shortcut icon]");
        if (!favicon.isEmpty()) {
            favicon.forEach(link -> fillToRealLink(request, adminStaticResourceHostByWebSite, link));
        }

        Elements base = document.head().select("base");
        base.attr("href", WebTools.getHomeUrl(request));
        ServerSideDataResponse<Object> serverSideDataResponse = serverSide(request.getUri(), request, httpResponse);
        Objects.requireNonNull(document.getElementById("__SS_DATA__")).text(new Gson().toJson(serverSideDataResponse));
        document.title(serverSideDataResponse.getDocumentTitle());
        return document.html();
    }

    private void fillToRealLink(HttpRequest request, String adminStaticResourceHostByWebSite, Element link) {
        String newUrl;
        if (StringUtils.isNotEmpty(adminStaticResourceHostByWebSite) && !BaseStaticSitePlugin.isStaticPluginRequest(request)) {
            newUrl = link.attr("href").replaceFirst("/", adminStaticResourceHostByWebSite + "/");
        } else {
            newUrl = link.attr("href").replaceFirst("/", WebTools.getHomeUrl(request));
        }
        link.attr("href", newUrl + "?v=" + AdminConstants.adminResource.getStaticResourceBuildId());
    }

    public ServerSideDataResponse<Object> serverSide(String uri, HttpRequest request, HttpResponse response) throws Throwable {
        Map<String, Object> resourceInfo = AdminConstants.adminResource.adminResourceInfo(request);
        if (Objects.isNull(AdminTokenThreadLocal.getUser())) {
            return new ServerSideDataResponse<>(null, resourceInfo, null, null, AdminConstants.getAdminDocumentTitleByUri(request.getUri()));
        }
        UserInfoResponse userInfo = new UserService().getUserInfoWithCache(AdminTokenThreadLocal.getUserId(), AdminTokenThreadLocal.getUser().getSessionId());
        Method method = request.getRequestConfig().getRouter().getMethod("/api" + uri, request.getMethod());
        try {
            Controller controller = Controller.buildController(method, request, response);
            StandardResponse result = (StandardResponse) method.invoke(controller);
            if (Objects.isNull(result)) {
                return new ServerSideDataResponse<>(userInfo, resourceInfo, new Object(), AdminTokenThreadLocal.getUser().getSessionId(), AdminConstants.getAdminDocumentTitleByUri(request.getUri()));
            }
            if (result instanceof AdminApiPageDataStandardResponse<?>) {
                AdminApiPageDataStandardResponse<?> data = (AdminApiPageDataStandardResponse<?>) result;
                return new ServerSideDataResponse<>(userInfo, resourceInfo, data.getData(), AdminTokenThreadLocal.getUser().getSessionId(), data.getDocumentTitle());
            }
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
        return new ServerSideDataResponse<>(userInfo, resourceInfo, null, AdminTokenThreadLocal.getUser().getSessionId(), AdminConstants.getAdminDocumentTitleByUri(request.getUri()));
    }

}
