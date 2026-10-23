package com.zrlog.admin.business.service;

import com.hibegin.common.util.FileUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.blog.web.plugin.TemplateDownloadPlugin;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.template.HtmlTemplateProcessor;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.model.WebSite;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.TemplateHelper;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Coordinates theme management operations, previews and audit attribution. */
@Singleton
public class TemplateService {
    private final AdminAuditService adminAuditService;
    private final Provider<WebSite> webSiteProvider;
    private final CacheService cacheService;
    private final Supplier<PluginCorePlugin> pluginCore;
    private final Supplier<TemplateDownloadPlugin> templateDownload;
    private final TemplatePackageInstaller installer;
    private final TemplateSettingsService settingsService;
    private final TemplateCatalog catalog;

    @Inject
    public TemplateService(CacheService cacheService, Supplier<PluginCorePlugin> pluginCore,
                           Supplier<TemplateDownloadPlugin> templateDownload,
                           AdminAuditService adminAuditService, Provider<WebSite> webSiteProvider,
                           TemplatePackageInstaller installer, TemplateSettingsService settingsService,
                           TemplateCatalog catalog) {
        this.cacheService = cacheService;
        this.pluginCore = pluginCore;
        this.templateDownload = templateDownload;
        this.adminAuditService = adminAuditService;
        this.webSiteProvider = webSiteProvider;
        this.installer = installer;
        this.settingsService = settingsService;
        this.catalog = catalog;
    }

    public void prepareTemplates() throws IOException {
        installer.prepare(cacheService.getPublicWebSiteInfo().getTemplate());
    }

    public List<TemplateEntryResponse> getAllTemplates(String previewTemplate) throws IOException {
        return catalog.list(cacheService.getPublicWebSiteInfo().getTemplate(), previewTemplate);
    }

    public String getTemplatePath(HttpRequest request) {
        return TemplateHelper.getTemplatePath(request, cacheService.getPublicWebSiteInfo().getTemplate(), templateDownload.get());
    }

    public boolean apply(String template, AdminAuditContext audit) throws SQLException {
        boolean updated = this.webSiteProvider.get().updateByKV("template", template);
        this.adminAuditService.record(audit, AdminAuditAction.APPLY_TEMPLATE, template);
        return updated;
    }

    public DeleteResponse delete(String shortTemplate, AdminAuditContext audit) {
        File file = PathUtil.safeAppendFilePath(
                PathUtil.getStaticPath() + Constants.TEMPLATE_BASE_PATH, shortTemplate);
        boolean deleted = file.exists() && FileUtils.deleteFile(file.toString());
        if (deleted) {
            this.adminAuditService.record(audit, AdminAuditAction.DELETE_TEMPLATE, shortTemplate);
        }
        return new DeleteResponse(deleted);
    }

    public UploadTemplateResponse uploadAndRecord(String shortTemplate, boolean overwrite, File file,
                                                  AdminAuditContext audit) throws IOException {
        UploadTemplateResponse response = installer.upload(shortTemplate, overwrite, file);
        if (response.getError() == 0 && response.getData() != null) {
            this.adminAuditService.record(audit,
                    AdminAuditAction.UPLOAD_TEMPLATE,
                    response.getData().getShortTemplate());
        }
        return response;
    }

    public UpdateRecordResponse saveAndRecord(String template, Map<String, Object> settings,
                                              AdminAuditContext audit) throws SQLException, IOException {
        UpdateRecordResponse response = settingsService.save(template, settings);
        this.adminAuditService.record(audit, AdminAuditAction.UPDATE_TEMPLATE_CONFIG, template);
        return response;
    }

    public TemplateVO loadTemplateConfig(String templateName, HttpRequest request) {
        TemplateVO template = loadTemplateConfig(templateName);
        template.getConfig().values().forEach(value -> {
            if (Objects.equals(value.getContentType(), "html") && value.getValue() instanceof String) {
                value.setPreviewValue(previewValue((String) value.getValue(), request));
            }
        });
        return template;
    }

    public TemplateVO loadTemplateConfig(String templateName) {
        return settingsService.loadTemplateConfig(templateName);
    }

    public String previewValue(String value, HttpRequest request) {
        return new HtmlTemplateProcessor(request, null, "/", pluginCore.get()).transform(value);
    }

    public TemplateDownloadResponse templateCenter(String protocol, String host, String contextPath) {
        return new TemplateDownloadResponse("https://store.zrlog.com/template/index.html?from=" + protocol
                + "://" + host + contextPath + AdminConstants.ADMIN_URI_BASE_PATH + "/template&v="
                + BlogBuildInfoUtil.getVersion() + "&id=" + BlogBuildInfoUtil.getBuildId() + "&upgrade-v3=true");
    }

}
