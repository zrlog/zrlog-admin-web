package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.dto.UserLoginDTO;
import com.zrlog.admin.business.rest.request.LoginRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.AdminStatisticsService;
import com.zrlog.admin.business.service.UserService;
import com.zrlog.admin.util.ManifestUtils;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.business.exception.MissingInstallException;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
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
    public AdminApiPageDataStandardResponse<UserBasicInfoResponse> login() throws Exception {
        if (!Constants.zrLogConfig.isInstalled()) {
            throw new MissingInstallException();
        }
        LoginRequest loginRequest = getRequestBodyWithNullCheck(LoginRequest.class);
        UserLoginDTO dto = userService.login(loginRequest);
        Constants.zrLogConfig.getTokenService().setAdminToken(dto.getId(), dto.getSecretKey(), dto.getUserBasicInfoResponse().getKey(),
                Objects.equals(loginRequest.getHttps(), true) ? "https" : "http", getRequest(), getResponse());
        new com.zrlog.admin.business.service.AdminAuditService().record(request, "登录成功", "login");
        return new AdminApiPageDataStandardResponse<>(dto.getUserBasicInfoResponse());
    }


    @ResponseBody
    public Map<String, Object> manifest() throws IOException {
        return ManifestUtils.manifest(request);
    }

    /**
     * 触发更新缓存
     */
    @ResponseBody
    @RefreshCache(updateStaticSites = {StaticSiteType.ADMIN, StaticSiteType.BLOG})
    public UpdateRecordResponse refreshCache() {
        return new UpdateRecordResponse();
    }


    @ResponseBody
    public AdminApiPageDataStandardResponse<ErrorPageResponse> error() {
        return new AdminApiPageDataStandardResponse<>(new ErrorPageResponse(request.getParaToStr("message", "")), "", request.getUri());
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<PluginInfoResponse> plugin() {
        String page = getRequest().getParaToStr("page", "");
        return new AdminApiPageDataStandardResponse<>(new PluginInfoResponse("admin/plugins/" + page), "", request.getUri());
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<IndexResponse> index() throws SQLException {
        List<String> tips = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            tips.add(I18nUtil.getAdminBackendStringFromRes("admin.index.welcomeTips_" + i));
        }
        Collections.shuffle(tips);
        ExecutorService executor = ThreadUtils.newFixedThreadPool(20);
        try {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            CompletableFuture<StatisticsInfoResponse> statisticsInfo = new AdminStatisticsService().statisticsInfo(executor);
            futures.add(statisticsInfo);
            CompletableFuture<List<ArticleActivityData>> dataList = new AdminArticleService().activityDataList(executor);
            futures.add(dataList);
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return new AdminApiPageDataStandardResponse<>(new IndexResponse(statisticsInfo.join(),
                    I18nUtil.getAdminBackendStringFromRes("admin.index.welcomeTip"),
                    new ArrayList<>(Collections.singletonList(tips.get(0))),
                    dataList.join(), BlogBuildInfoUtil.getVersionInfo()), "", request.getUri());
        } finally {
            executor.shutdown();
        }
    }
}
