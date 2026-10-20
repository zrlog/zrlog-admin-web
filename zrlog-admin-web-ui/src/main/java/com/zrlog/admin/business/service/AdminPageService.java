package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.rest.response.ServerSideDataResponse;
import com.zrlog.admin.business.rest.response.UserInfoResponse;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

@Singleton
public class AdminPageService {
    private final UserService userService;

    private final UserPreferenceService userPreferenceService;

    private final AccountIdentityService identities;

    private final CacheService cacheService;
    private final AdminPageDataFactory pages;
    private final AdminResourceService resources;
    private final Supplier<String> buildId;

    @Inject
    public AdminPageService(
            CacheService cacheService,
            AdminPageDataFactory pages,
            AdminResourceService resources,
            Supplier<String> buildId,
            AccountIdentityService identities,
            UserPreferenceService userPreferenceService,
            UserService userService) {
        this.userService = userService;

        this.userPreferenceService = userPreferenceService;

        this.identities = identities;

        this.buildId = Objects.requireNonNull(buildId);
        this.pages = pages;
        this.resources = Objects.requireNonNull(resources);
        this.cacheService = cacheService;
    }

    public String buildHtml(HttpRequest request, InputStream htmlInputStream,
                            ServerSideDataResponse<Object> serverSideDataResponse) throws IOException {
        Document document = Jsoup.parse(IOUtil.getStringInputStream(htmlInputStream));
        //clean history
        document.body().removeClass("dark");
        document.body().removeClass("light");
        Objects.requireNonNull(document.selectFirst("base")).attr("href", "/");
        PublicWebSiteInfo publicWebSiteInfo = cacheService.getPublicWebSiteInfo();
        document.body().addClass(Objects.equals(publicWebSiteInfo.getAdmin_darkMode(), true) ? "dark" : "light");
        Element htmlElement = document.selectFirst("html");
        if (Objects.nonNull(htmlElement)) {
            htmlElement.attr("lang", I18nUtil.getCurrentLocale(publicWebSiteInfo.getLanguage()).split("_")[0]);
        }
        Elements select = document.head().select("meta[name=theme-color]");
        if (!select.isEmpty()) {
            Element first = select.first();
            if (Objects.nonNull(first)) {
                first.attr("content", publicWebSiteInfo.getAdmin_color_primary());
            }
        }
        String adminStaticResourceHostByWebSite = resources.staticResourceBaseUrl(request);

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
        Objects.requireNonNull(document.getElementById("__SS_DATA__")).text(new Gson().toJson(serverSideDataResponse));
        document.body().removeClass("dark").removeClass("light")
                .addClass(Boolean.TRUE.equals(serverSideDataResponse.getResourceInfo().getAdmin_darkMode()) ? "dark" : "light");
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
        link.attr("href", newUrl + "?v=" + buildId.get());
    }

    public ServerSideDataResponse<Object> serverSide(AccountAccess actor, String sessionId, String uri,
                                                     HttpRequest request, Object data, String documentTitle) throws SQLException {
        AdminResourceInfoResponse resourceInfo = resources.adminResourceInfo(request);
        if (actor == null || BaseStaticSitePlugin.isStaticPluginRequest(request)) {
            return new ServerSideDataResponse<>(null, resourceInfo, null, null,
                    pages.title(request.getUri()), pages.metadata(null, uri));
        }
        identities.revalidate(actor);
        UserInfoResponse userInfo = userService.getUserInfoWithCache(actor.getUserId(), sessionId, cacheService);
        userPreferenceService.apply(resourceInfo, userInfo.getLanguage(), userInfo.getAppearance());
        return new ServerSideDataResponse<>(userInfo, resourceInfo, data, sessionId,
                documentTitle, pages.metadata(actor, uri));
    }

}
