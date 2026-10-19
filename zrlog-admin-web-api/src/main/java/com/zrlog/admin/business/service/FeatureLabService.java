package com.zrlog.admin.business.service;

import static com.zrlog.admin.business.service.WebSiteService.*;

import com.hibegin.common.dao.ResultBeanUtils;
import com.zrlog.admin.business.rest.base.FeatureLabWebSiteInfo;
import com.zrlog.admin.business.rest.request.WebhookConfigRequest;
import com.zrlog.model.WebSite;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Assembles the feature settings page across modules. */
@Singleton
public class FeatureLabService {
    private final WebhookService webhookService;
    private final jakarta.inject.Provider<WebSite> webSiteProvider;

    private final BooleanSupplier accessAvailable;

    @Inject
    public FeatureLabService(
            @Named("accessAvailable") BooleanSupplier accessAvailable,
            WebhookService webhookService,
            jakarta.inject.Provider<WebSite> webSiteProvider) {
        this.webhookService = webhookService;
        this.webSiteProvider = webSiteProvider;

        this.accessAvailable = Objects.requireNonNull(accessAvailable);
    }

    public FeatureLabWebSiteInfo featureLab() {
        FeatureLabWebSiteInfo featureLab =
                ResultBeanUtils.convert(
                        this.webSiteProvider
                                .get()
                                .getWebSiteByNameIn(
                                        Arrays.asList(
                                                FEATURE_RESOURCE_REFERENCE_ENABLED_KEY,
                                                FEATURE_ARTICLE_EXTENSION_FILTER_ENABLED_KEY,
                                                FEATURE_ARTICLE_ARRANGEMENT_ENABLED_KEY,
                                                FEATURE_WEBHOOK_ENABLED_KEY,
                                                FEATURE_PERSONAL_DATA_ENABLED_KEY)),
                        FeatureLabWebSiteInfo.class);
        featureLab.setFeature_webhook_enabled(
                accessAvailable.getAsBoolean()
                        && Boolean.TRUE.equals(
                                this.webhookService.getConfigResponse().getEnabled()));
        featureLab.doValid();
        return featureLab;
    }

    public void updateWebhook(Boolean enabled) {
        if (!accessAvailable.getAsBoolean()) return;
        var webhook = new WebhookConfigRequest();
        webhook.setEnabled(enabled);
        this.webhookService.updateConfig(webhook);
    }

}
