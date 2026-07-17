package com.zrlog.admin.business.rest.base;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * 与 AI 的 api 服务交互的配置信息
 */
public class AIWebSiteInfo implements Validator {

    private AIProviderType ai_provider;
    private String ai_model;
    private String ai_base_url;
    private String ai_api_key;
    private String ai_prompt;
    private Integer ai_max_completion_tokens;
    private Boolean ai_reasoning_enabled = Boolean.TRUE;
    private AIProviderType ai_image_provider;
    private String ai_image_model;
    private String ai_image_base_url;
    private String ai_image_api_key;

    public AIProviderType getAi_provider() {
        return ai_provider;
    }

    public void setAi_provider(AIProviderType ai_provider) {
        this.ai_provider = ai_provider;
    }

    public String getAi_model() {
        return ai_model;
    }

    public void setAi_model(String ai_model) {
        this.ai_model = ai_model;
    }

    public String getAi_base_url() {
        return ai_base_url;
    }

    public void setAi_base_url(String ai_base_url) {
        this.ai_base_url = ai_base_url;
    }

    public String getAi_api_key() {
        return ai_api_key;
    }

    public void setAi_api_key(String ai_api_key) {
        this.ai_api_key = ai_api_key;
    }

    public String getAi_prompt() {
        return ai_prompt;
    }

    public void setAi_prompt(String ai_prompt) {
        this.ai_prompt = ai_prompt;
    }

    public Integer getAi_max_completion_tokens() {
        return ai_max_completion_tokens;
    }

    public void setAi_max_completion_tokens(Integer ai_max_completion_tokens) {
        this.ai_max_completion_tokens = ai_max_completion_tokens;
    }

    public Boolean getAi_reasoning_enabled() {
        return ai_reasoning_enabled;
    }

    public void setAi_reasoning_enabled(Boolean ai_reasoning_enabled) {
        this.ai_reasoning_enabled = ai_reasoning_enabled;
    }

    public boolean isReasoningEnabled() {
        return !Objects.equals(ai_reasoning_enabled, false);
    }

    public AIProviderType getAi_image_provider() {
        return ai_image_provider;
    }

    public void setAi_image_provider(AIProviderType ai_image_provider) {
        this.ai_image_provider = ai_image_provider;
    }

    public String getAi_image_model() {
        return ai_image_model;
    }

    public void setAi_image_model(String ai_image_model) {
        this.ai_image_model = ai_image_model;
    }

    public String getAi_image_base_url() {
        return ai_image_base_url;
    }

    public void setAi_image_base_url(String ai_image_base_url) {
        this.ai_image_base_url = ai_image_base_url;
    }

    public String getAi_image_api_key() {
        return ai_image_api_key;
    }

    public void setAi_image_api_key(String ai_image_api_key) {
        this.ai_image_api_key = ai_image_api_key;
    }

    @Override
    public void doValid() {
        if (Objects.isNull(ai_provider)) {
            throw new ArgsException("ai_provider");
        }
        if (StringUtils.isEmpty(ai_model) || StringUtils.isEmpty(ai_model.trim())) {
            throw new ArgsException("ai_model");
        }
        ai_model = ai_model.trim();
        ai_base_url = normalizeBaseUrl(ai_base_url);
        if (Objects.nonNull(ai_max_completion_tokens) && ai_max_completion_tokens < 1) {
            throw new ArgsException("ai_max_completion_tokens");
        }
        ai_image_base_url = normalizeImageBaseUrl(ai_image_base_url);
        if (Objects.nonNull(ai_image_provider)) {
            if (StringUtils.isEmpty(ai_image_model) || StringUtils.isEmpty(ai_image_model.trim())) {
                throw new ArgsException("ai_image_model");
            }
            ai_image_model = ai_image_model.trim();
            if (StringUtils.isEmpty(ai_image_base_url)
                    && !ai_image_provider.getImageModels().contains(ai_image_model)) {
                throw new ArgsException("ai_image_model");
            }
        }
    }

    public static String normalizeBaseUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl, "ai_base_url");
    }

    public static String normalizeImageBaseUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl, "ai_image_base_url");
    }

    private static String normalizeBaseUrl(String baseUrl, String fieldName) {
        if (StringUtils.isEmpty(baseUrl)) {
            return "";
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String scheme = uri.getScheme();
            if (StringUtils.isEmpty(scheme)
                    || !(Objects.equals("http", scheme.toLowerCase(Locale.ROOT))
                        || Objects.equals("https", scheme.toLowerCase(Locale.ROOT)))
                    || StringUtils.isEmpty(uri.getHost())
                    || StringUtils.isNotEmpty(uri.getUserInfo())
                    || StringUtils.isNotEmpty(uri.getFragment())) {
                throw new ArgsException(fieldName);
            }
            return trimTrailingSlashes(uri.toString());
        } catch (IllegalArgumentException e) {
            throw new ArgsException(fieldName);
        }
    }

    private static String trimTrailingSlashes(String baseUrl) {
        int queryIndex = baseUrl.indexOf('?');
        String query = queryIndex >= 0 ? baseUrl.substring(queryIndex) : "";
        String path = queryIndex >= 0 ? baseUrl.substring(0, queryIndex) : baseUrl;
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path + query;
    }
}
