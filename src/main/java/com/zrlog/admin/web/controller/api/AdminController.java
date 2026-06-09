package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.dto.UserLoginDTO;
import com.zrlog.admin.business.rest.request.LoginRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.ManifestUtils;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.exception.MissingInstallException;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ThreadUtils;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class AdminController extends BaseController {

    private final UserService userService = new UserService();

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public AdminPageDataResponse<UserBasicInfoResponse> login() throws Exception {
        if (!Constants.zrLogConfig.isInstalled()) {
            throw new MissingInstallException();
        }
        LoginRequest loginRequest = getRequestBodyWithNullCheck(LoginRequest.class);
        UserLoginDTO dto = userService.login(loginRequest);
        Constants.zrLogConfig.getTokenService().setAdminToken(dto.getId(), dto.getSecretKey(), dto.getUserBasicInfoResponse().getKey(),
                Objects.equals(loginRequest.getHttps(), true) ? "https" : "http", getRequest(), getResponse());
        new AdminAuditService().record(request, AdminAuditAction.LOGIN_SUCCESS, dto.getUserBasicInfoResponse().getUserName());
        return new AdminPageDataResponse<>(dto.getUserBasicInfoResponse());
    }


    @ResponseBody
    public AdminManifestResponse manifest() throws IOException {
        return ManifestUtils.manifest(request);
    }

    /**
     * 触发更新缓存
     */
    @ResponseBody
    @RefreshCache(updateStaticSites = {StaticSiteType.ADMIN, StaticSiteType.BLOG})
    public UpdateRecordResponse refreshCache() {
        new AdminAuditService().record(request, AdminAuditAction.REFRESH_CACHE);
        return new UpdateRecordResponse();
    }


    @ResponseBody
    public AdminPageDataResponse<ErrorPageResponse> error() {
        return new AdminPageDataResponse<>(new ErrorPageResponse(request.getParaToStr("message", "")), "", request.getUri());
    }

    @ResponseBody
    public AdminPageDataResponse<PluginInfoResponse> plugin() {
        String page = getRequest().getParaToStr("page", "");
        return new AdminPageDataResponse<>(new PluginInfoResponse("admin/plugins/" + page), "", request.getUri());
    }

    @ResponseBody
    public AdminPageDataResponse<IndexResponse> index() throws SQLException {
        List<String> tips = loadWelcomeTips();
        Collections.shuffle(tips);
        AdminDashboardService dashboardService = new AdminDashboardService();
        AdminDashboardConfigResponse dashboardConfig = dashboardService.getConfig(request, AdminTokenThreadLocal.getUser(), true);
        boolean auditTrailEnabled = isCardEnabled(dashboardConfig, "auditTrail");
        boolean activityEnabled = isCardEnabled(dashboardConfig, "activity");
        ExecutorService executor = ThreadUtils.newFixedThreadPool(20);
        try {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            CompletableFuture<StatisticsInfoResponse> statisticsInfo = new AdminStatisticsService().statisticsInfo(executor, auditTrailEnabled);
            futures.add(statisticsInfo);
            CompletableFuture<List<ArticleActivityData>> dataList = activityEnabled
                    ? new AdminArticleService().activityDataList(executor)
                    : CompletableFuture.completedFuture(Collections.emptyList());
            futures.add(dataList);
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return new AdminPageDataResponse<>(new IndexResponse(statisticsInfo.join(),
                    I18nUtil.getAdminBackendStringFromRes("admin.index.welcomeTip"),
                    new ArrayList<>(Collections.singletonList(tips.get(0))),
                    dataList.join(), BlogBuildInfoUtil.getVersionInfo(),
                    dashboardConfig), "", request.getUri());
        } finally {
            executor.shutdown();
        }
    }

    private boolean isCardEnabled(AdminDashboardConfigResponse config, String cardId) {
        if (config == null || config.getCards() == null) {
            return true;
        }
        for (AdminDashboardCardResponse item : config.getCards()) {
            if (Objects.equals("card", item.getKind()) && Objects.equals(cardId, item.getId())) {
                return !Objects.equals(item.getEnabled(), false);
            }
        }
        return true;
    }

    @ResponseBody
    public ApiStandardResponse<AdminDashboardConfigResponse> indexConfig() {
        AdminDashboardConfigResponse config;
        String message = "";
        if (request.getMethod() == HttpMethod.POST) {
            config = new AdminDashboardService().saveConfig(
                    getRequestBodyWithNullCheck(com.zrlog.admin.business.rest.request.AdminDashboardConfigRequest.class),
                    request, AdminTokenThreadLocal.getUser());
            new AdminAuditService().record(request, AdminAuditAction.UPDATE_DASHBOARD_CONFIG);
            message = I18nUtil.getAdminBackendStringFromRes("admin.common.update.success");
        } else {
            config = new AdminDashboardService().getConfig(request, AdminTokenThreadLocal.getUser());
        }
        return new ApiStandardResponse<>(config, message);
    }

    private List<String> loadWelcomeTips() {
        List<String> tips = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String tip = I18nUtil.getAdminBackendStringFromRes("admin.index.welcomeTips." + i);
            if (tip == null || tip.trim().isEmpty()) {
                break;
            }
            tips.add(tip);
        }
        return tips;
    }
}
