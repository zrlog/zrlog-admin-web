package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.request.OptimizeAiPromptRequest;
import com.zrlog.admin.business.rest.request.OptimizeWebsiteDescriptionRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.ai.service.AIToolService;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.updater.AutoUpgradeVersionType;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.model.WebSite;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

public class WebSiteController extends BaseController {

    private final WebSiteService webSiteService = new WebSiteService();

    @ResponseBody
    public AdminPageDataResponse<VersionResponse> version() {
        VersionResponse versionResponse = new VersionResponse();
        versionResponse.setBuildId(BlogBuildInfoUtil.getBuildId());
        versionResponse.setVersion(BlogBuildInfoUtil.getVersion());
        versionResponse.setChangelog(getCurrentChangeLog(I18nUtil.getBackend()));
        InputStream resourceAsStream = WebSiteController.class.getResourceAsStream(AdminConstants.BUILD_SYSTEM_INFO_MD);
        if (Objects.nonNull(resourceAsStream)) {
            versionResponse.setBuildSystemInfo(IOUtil.getStringInputStream(resourceAsStream));
        } else {
            versionResponse.setBuildSystemInfo("#### Not find build system info file");
        }
        return new AdminPageDataResponse<>(versionResponse, "", request.getUri());
    }

    protected String getCurrentChangeLog(Map<String, Object> backendMessages) {
        return UpdateVersionInfoPlugin.getCurrentChangeLog(backendMessages);
    }

    @ResponseBody
    public AdminPageDataResponse<BasicWebSiteInfo> index() throws SQLException {
        return basic();
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = {StaticSiteType.BLOG, StaticSiteType.ADMIN})
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public AdminPageDataResponse<BasicWebSiteInfo> basic() throws SQLException {
        if (request.getMethod() == HttpMethod.POST) {
            update(getRequestBodyWithNullCheck(BasicWebSiteInfo.class));
        }
        return new AdminPageDataResponse<>(webSiteService.basicWebSiteInfo(), getRespMessage(), request.getUri());
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public ApiStandardResponse<OptimizeWebsiteDescriptionResponse> optimizeDescription()
            throws IOException, InterruptedException {
        OptimizeWebsiteDescriptionRequest optimizeRequest = getRequestBodyWithNullCheck(OptimizeWebsiteDescriptionRequest.class);
        OptimizeWebsiteDescriptionResponse optimizeResponse = new AIToolService().optimizeWebsiteDescription(optimizeRequest);
        return new ApiStandardResponse<>(optimizeResponse);
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public ApiStandardResponse<OptimizeAiPromptResponse> optimizeAiPrompt()
            throws IOException, InterruptedException {
        OptimizeAiPromptRequest optimizeRequest = getRequestBodyWithNullCheck(OptimizeAiPromptRequest.class);
        OptimizeAiPromptResponse optimizeResponse = new AIToolService().optimizeAiPrompt(optimizeRequest);
        return new ApiStandardResponse<>(optimizeResponse);
    }

    private String getRespMessage() {
        return request.getMethod() == HttpMethod.POST ? I18nUtil.getAdminBackendStringFromRes("admin.common.update.success") : "";
    }

    private void update(Object t) throws SQLException {
        new AdminAuditService().record(request, AdminAuditAction.UPDATE_SETTING, getSettingAuditContent(t));
        Map<String, Object> requestMap = BeanUtil.convert(t, Map.class);
        if (Objects.nonNull(requestMap)) {
            for (Entry<String, Object> param : requestMap.entrySet()) {
                new WebSite().updateByKV(param.getKey(), toWebsiteValue(param.getValue()));
            }
        }
        BaseDataInitVO dataInitVO = Constants.zrLogConfig.getCacheService().getInitData();
        if (Objects.nonNull(dataInitVO)) {
            dataInitVO.setWebSite(new WebSite().getPublicWebSite());
        }
    }

    private Object toWebsiteValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Double.isFinite(number) && Math.rint(number) == number) {
                return Long.toString((long) number);
            }
        }
        return value.toString();
    }

    private String getSettingAuditContent(Object t) {
        String key = AdminConstants.TITLE_MAP.get(getAdminPageUri(request.getUri()));
        String content = I18nUtil.getAdminBackendStringFromRes(key);
        return StringUtils.isEmpty(content) ? t.getClass().getSimpleName() : content;
    }

    private String getAdminPageUri(String uri) {
        String apiPrefix = "/api" + AdminConstants.ADMIN_URI_BASE_PATH;
        if (uri.startsWith(apiPrefix)) {
            return AdminConstants.ADMIN_URI_BASE_PATH + uri.substring(apiPrefix.length());
        }
        return uri;
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = StaticSiteType.BLOG)
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public AdminPageDataResponse<BlogWebSiteInfo> blog() throws SQLException {
        if (request.getMethod() == HttpMethod.POST) {
            update(getRequestBodyWithNullCheck(BlogWebSiteInfo.class));
        }
        return new AdminPageDataResponse<>(webSiteService.blogWebSiteInfo(), getRespMessage(), request.getUri());
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = StaticSiteType.BLOG)
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public AdminPageDataResponse<OtherWebSiteInfo> other() throws SQLException {
        if (request.getMethod() == HttpMethod.POST) {
            update(getRequestBodyWithNullCheck(OtherWebSiteInfo.class));
        }
        return new AdminPageDataResponse<>(webSiteService.other(), getRespMessage(), request.getUri());
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = StaticSiteType.ADMIN)
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public AdminPageDataResponse<AdminWebSiteInfo> admin() throws SQLException {
        if (request.getMethod() == HttpMethod.POST) {
            AdminWebSiteInfo adminWebSiteInfo = getRequestBodyWithNullCheck(AdminWebSiteInfo.class);
            update(adminWebSiteInfo);
            Constants.zrLogConfig.getTokenService().updateSessionTimeout(adminWebSiteInfo.getSession_timeout());
        }
        return new AdminPageDataResponse<>(webSiteService.adminWebSiteInfo(), getRespMessage(), request.getUri());
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public AdminPageDataResponse<ArticleEditWebSiteInfo> articleEdit() throws SQLException {
        if (request.getMethod() == HttpMethod.POST) {
            update(getRequestBodyWithNullCheck(ArticleEditWebSiteInfo.class));
        }
        return new AdminPageDataResponse<>(webSiteService.articleEditWebSiteInfo(), getRespMessage(), request.getUri());
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = StaticSiteType.BLOG)
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public AdminPageDataResponse<ContentProtectorWebSiteInfo> contentProtector() throws SQLException {
        if (request.getMethod() == HttpMethod.POST) {
            update(getRequestBodyWithNullCheck(ContentProtectorWebSiteInfo.class));
        }
        return new AdminPageDataResponse<>(webSiteService.contentProtector(), getRespMessage(), request.getUri());
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public AdminPageDataResponse<FeatureLabWebSiteInfo> lab() throws SQLException {
        if (request.getMethod() == HttpMethod.POST) {
            update(getRequestBodyWithNullCheck(FeatureLabWebSiteInfo.class));
        }
        return new AdminPageDataResponse<>(webSiteService.featureLab(), getRespMessage(), request.getUri());
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    public AdminPageDataResponse<UpgradeWebSiteInfo> upgrade() throws SQLException {
        if (request.getMethod() == HttpMethod.POST) {
            UpgradeWebSiteInfo request = getRequestBodyWithNullCheck(UpgradeWebSiteInfo.class);
            UpdateVersionInfoPlugin updateVersionInfoPlugin = Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class);
            if (Objects.isNull(updateVersionInfoPlugin)) {
                throw new RuntimeException(I18nUtil.getAdminBackendStringFromRes("admin.website.upgrade.error.missingUpdatePlugin"));
            }
            update(request);
            updateVersionInfoPlugin.stop();
            if (AutoUpgradeVersionType.cycle(request.getAutoUpgradeVersion()) != AutoUpgradeVersionType.NEVER) {
                updateVersionInfoPlugin.start();
            }
        }
        return new AdminPageDataResponse<>(webSiteService.upgradeWebSiteInfo(), getRespMessage(), request.getUri());
    }

    @ResponseBody
    public AdminPageDataResponse<AIWebSiteInfoResponse> ai() throws SQLException {
        if (request.getMethod() == HttpMethod.POST) {
            AIWebSiteInfo request = getRequestBodyWithNullCheck(AIWebSiteInfo.class);
            AIWebSiteInfo current = webSiteService.ai();
            if (StringUtils.isEmpty(request.getAi_api_key())) {
                if (StringUtils.isEmpty(current.getAi_api_key())) {
                    throw new ArgsException("ai_api_key");
                }
                request.setAi_api_key(current.getAi_api_key());
            }
            if (Objects.isNull(request.getAi_image_provider())) {
                request.setAi_image_api_key(current.getAi_image_api_key());
            } else if (StringUtils.isEmpty(request.getAi_image_api_key())) {
                if (StringUtils.isNotEmpty(current.getAi_image_api_key())) {
                    request.setAi_image_api_key(current.getAi_image_api_key());
                } else {
                    request.setAi_image_api_key(request.getAi_api_key());
                }
            }
            update(request);
        }
        AIWebSiteInfo ai = webSiteService.ai();
        AIWebSiteInfoResponse infoResponse = BeanUtil.convert(ai, AIWebSiteInfoResponse.class);
        infoResponse.setHasAiApiKey(StringUtils.isNotEmpty(ai.getAi_api_key()));
        infoResponse.setHasAiImageApiKey(StringUtils.isNotEmpty(ai.getAi_image_api_key()));
        infoResponse.setAi_api_key("");
        infoResponse.setAi_image_api_key("");
        infoResponse.setAllProviders(Arrays.stream(AIProviderType.values()).map(e -> {
            AIWebSiteInfoResponse.AIProvider aiProvider = new AIWebSiteInfoResponse.AIProvider();
            aiProvider.setName(e);
            aiProvider.setModels(e.getModels());
            aiProvider.setModelEntries(e.getModelEntries());
            return aiProvider;
        }).collect(Collectors.toList()));
        infoResponse.setAllImageProviders(Arrays.stream(AIProviderType.values()).filter(e -> !e.getImageModels().isEmpty()).map(e -> {
            AIWebSiteInfoResponse.AIProvider aiProvider = new AIWebSiteInfoResponse.AIProvider();
            aiProvider.setName(e);
            aiProvider.setModels(e.getImageModels());
            aiProvider.setModelEntries(e.getModelEntries());
            return aiProvider;
        }).collect(Collectors.toList()));
        return new AdminPageDataResponse<>(infoResponse, getRespMessage(), request.getUri());
    }
}
