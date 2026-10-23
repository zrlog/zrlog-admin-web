package com.zrlog.admin.business.service;

import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.common.CacheService;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.model.WebSite;
import com.zrlog.theme.spi.BundledThemes;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

/** Owns theme configuration values, sanitization and localized form metadata. */
public final class TemplateSettingsService {
    private static final Logger LOGGER = Logger.getLogger(TemplateSettingsService.class.getName());
    private final Provider<WebSite> webSiteProvider;
    private final CacheService cacheService;

    @Inject
    public TemplateSettingsService(Provider<WebSite> webSiteProvider, CacheService cacheService) {
        this.webSiteProvider = webSiteProvider;
        this.cacheService = cacheService;
    }

    private boolean isNeedClean(String contentType) {
        return !Objects.equals(contentType, "html") && !Objects.equals(contentType, "yml");
    }

    public UpdateRecordResponse save(String template, Map<String, Object> settingMap) throws SQLException, IOException {
        TemplateVO.TemplateConfigMap configMap = TemplateInfoHelper.loadTemplateVO(template).getConfig();
        for (Map.Entry<String, Object> entry : settingMap.entrySet()) {
            // Boolean switches and numeric settings keep their JSON types; only text needs HTML cleaning.
            if (Objects.isNull(entry.getValue()) || entry.getValue() instanceof Boolean
                    || entry.getValue() instanceof Number) {
                continue;
            }
            String key = entry.getKey();
            TemplateVO.TemplateConfigVO templateConfigVO = configMap.get(key);
            if (Objects.isNull(templateConfigVO)) {
                continue;
            }
            if (Objects.equals("css", templateConfigVO.getContentType())) {
                settingMap.put(key, Jsoup.clean((String) entry.getValue(), Safelist.none().addTags("style")));
                continue;
            }
            //校验输入内容
            if (isNeedClean(templateConfigVO.getContentType())) {
                settingMap.put(key, Jsoup.clean((String) entry.getValue(), Safelist.none()));
            }
        }
        this.webSiteProvider.get().updateTemplateConfigMap(template, settingMap);
        UpdateRecordResponse updateRecordResponse = new UpdateRecordResponse();
        updateRecordResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.template.update.success"));
        return updateRecordResponse;
    }

    public TemplateVO loadTemplateConfig(String templateName) {
        TemplateVO templateVO = TemplateInfoHelper.loadTemplateVO(templateName);
        TemplateVO.TemplateConfigMap config = templateVO.getConfig();
        Map<String, Object> dbConfig =
                this.webSiteProvider.get().getTemplateConfigMap(templateName);
        config.forEach((key, value) -> {
            if (dbConfig.containsKey(key)) {
                value.setValue(dbConfig.get(key));
            }
        });
        templateVO.setConfig(config);
        localizeConfig(templateVO);
        //添加一个隐藏的表单域
        TemplateVO.TemplateConfigVO templateConfigVO = new TemplateVO.TemplateConfigVO();
        templateConfigVO.setHtmlElementType("input");
        templateConfigVO.setType("hidden");
        templateConfigVO.setValue(templateName);
        config.put("template", templateConfigVO);
        return templateVO;
    }

    private void localizeConfig(TemplateVO template) {
        String locale = I18nUtil.getCurrentLocale(cacheService.getPublicWebSiteInfo().getLanguage()).startsWith("zh") ? "zh_CN" : "en_US";
        String resource = template.getTemplate() + "/language/i18n_" + locale + ".properties";
        boolean bundled = BundledThemes.getInstance().contains(template.getTemplate());
        Path file = bundled ? null : PathUtil.getStaticFile(resource).toPath();
        if (!bundled && !Files.isRegularFile(file)) {
            return;
        }
        Properties messages = new Properties();
        try (InputStream input = bundled ? TemplateSettingsService.class.getResourceAsStream(resource)
                : Files.newInputStream(file)) {
            if (input == null) {
                return;
            }
            messages.load(input);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warning("Could not load theme configuration translations: " + resource);
            return;
        }
        template.getConfig().forEach((key, value) -> {
            String prefix = "configForm." + key;
            value.setLabel(messages.getProperty(prefix + ".label", value.getLabel()));
            value.setPlaceholder(messages.getProperty(prefix + ".placeholder", value.getPlaceholder()));
        });
    }

}
