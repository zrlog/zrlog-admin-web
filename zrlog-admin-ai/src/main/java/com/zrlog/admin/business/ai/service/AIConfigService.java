package com.zrlog.admin.business.ai.service;

import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.response.AIWebSiteInfoResponse;
import com.zrlog.admin.business.service.WebSiteSettingsService;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.model.WebSite;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class AIConfigService {

    public static final String AI_REASONING_ENABLED_KEY = "ai_reasoning_enabled";
    static final List<String> AI_WEBSITE_INFO_KEYS = Arrays.asList("ai_provider", "ai_model", "ai_base_url", "ai_api_key", "ai_prompt",
            "ai_max_completion_tokens", AI_REASONING_ENABLED_KEY, "ai_image_provider", "ai_image_model",
            "ai_image_base_url", "ai_image_api_key");
    public AIWebSiteInfo ai() {
        return normalizeAIWebSiteInfo(queryToMap(AI_WEBSITE_INFO_KEYS, AIWebSiteInfo.class));
    }

    static <T extends AIWebSiteInfo> T normalizeAIWebSiteInfo(T info) {
        if (info.getAi_reasoning_enabled() == null) {
            info.setAi_reasoning_enabled(Boolean.TRUE);
        }
        return info;
    }

    static <T> T queryToMap(List<String> names, Class<T> clazz) {
        Map<String, Object> webSiteByNameIn = new WebSite().getWebSiteByNameIn(names);
        return ResultBeanUtils.convert(webSiteByNameIn, clazz);
    }

    public void updateAi(AIWebSiteInfo settings, HttpRequest request) throws SQLException {
        mergeRetainedApiKeys(settings, ai());
        new WebSiteSettingsService().update(settings, request);
    }

    public AIWebSiteInfoResponse aiResponse() {
        AIWebSiteInfo ai = ai();
        AIWebSiteInfoResponse response = BeanUtil.convert(ai, AIWebSiteInfoResponse.class);
        response.setHasAiApiKey(StringUtils.isNotEmpty(ai.getAi_api_key()));
        response.setHasAiImageApiKey(StringUtils.isNotEmpty(ai.getAi_image_api_key()));
        response.setAi_api_key("");
        response.setAi_image_api_key("");
        response.setAllProviders(Arrays.stream(AIProviderType.values())
                .map(provider -> toProvider(provider, false)).collect(Collectors.toList()));
        response.setAllImageProviders(Arrays.stream(AIProviderType.values())
                .filter(provider -> !provider.getImageModels().isEmpty())
                .map(provider -> toProvider(provider, true)).collect(Collectors.toList()));
        return response;
    }

    void mergeRetainedApiKeys(AIWebSiteInfo settings, AIWebSiteInfo current) {
        if (StringUtils.isEmpty(settings.getAi_api_key())) {
            boolean sameEndpoint = Objects.equals(settings.getAi_provider(), current.getAi_provider())
                    && Objects.equals(AIWebSiteInfo.normalizeBaseUrl(settings.getAi_base_url()),
                    AIWebSiteInfo.normalizeBaseUrl(current.getAi_base_url()));
            if (sameEndpoint && StringUtils.isNotEmpty(current.getAi_api_key())) {
                settings.setAi_api_key(current.getAi_api_key());
            } else if (StringUtils.isEmpty(settings.getAi_base_url())) {
                throw new ArgsException("ai_api_key");
            }
        }
        if (settings.getAi_image_provider() == null) {
            settings.setAi_image_api_key(current.getAi_image_api_key());
            return;
        }
        if (StringUtils.isNotEmpty(settings.getAi_image_api_key())) {
            return;
        }
        boolean sameImageEndpoint = Objects.equals(settings.getAi_image_provider(), current.getAi_image_provider())
                && Objects.equals(AIWebSiteInfo.normalizeImageBaseUrl(settings.getAi_image_base_url()),
                AIWebSiteInfo.normalizeImageBaseUrl(current.getAi_image_base_url()));
        boolean sameAsTextEndpoint = Objects.equals(settings.getAi_image_provider(), settings.getAi_provider())
                && Objects.equals(AIWebSiteInfo.normalizeImageBaseUrl(settings.getAi_image_base_url()),
                AIWebSiteInfo.normalizeBaseUrl(settings.getAi_base_url()));
        if (sameImageEndpoint && StringUtils.isNotEmpty(current.getAi_image_api_key())) {
            settings.setAi_image_api_key(current.getAi_image_api_key());
        } else if (sameAsTextEndpoint && StringUtils.isNotEmpty(settings.getAi_api_key())) {
            settings.setAi_image_api_key(settings.getAi_api_key());
        } else if (StringUtils.isEmpty(settings.getAi_image_base_url())) {
            throw new ArgsException("ai_image_api_key");
        }
    }

    private AIWebSiteInfoResponse.AIProvider toProvider(AIProviderType provider, boolean image) {
        AIWebSiteInfoResponse.AIProvider result = new AIWebSiteInfoResponse.AIProvider();
        result.setName(provider);
        result.setBaseUrl(provider.getBaseUrl());
        result.setModels(image ? provider.getImageModels() : provider.getModels());
        result.setModelEntries(provider.getModelEntries());
        return result;
    }

}
