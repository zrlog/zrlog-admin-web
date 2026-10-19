package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.base.FeatureLabWebSiteInfo;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import static com.zrlog.admin.business.service.WebSiteService.*;

/** Assembles the feature settings page across modules. */
public class FeatureLabService {
    private final BooleanSupplier accessAvailable;

    public FeatureLabService(BooleanSupplier accessAvailable) {
        this.accessAvailable = Objects.requireNonNull(accessAvailable);
    }

    public FeatureLabWebSiteInfo featureLab() {
        FeatureLabWebSiteInfo featureLab = com.hibegin.common.dao.ResultBeanUtils.convert(new com.zrlog.model.WebSite().getWebSiteByNameIn(Arrays.asList(FEATURE_RESOURCE_REFERENCE_ENABLED_KEY,
                FEATURE_ARTICLE_EXTENSION_FILTER_ENABLED_KEY,
                FEATURE_ARTICLE_ARRANGEMENT_ENABLED_KEY,
                FEATURE_WEBHOOK_ENABLED_KEY, FEATURE_PERSONAL_DATA_ENABLED_KEY)), FeatureLabWebSiteInfo.class);
        featureLab.setFeature_webhook_enabled(accessAvailable.getAsBoolean()
                && Boolean.TRUE.equals(new WebhookService().getConfigResponse().getEnabled()));
        featureLab.doValid();
        return featureLab;
    }

    public void updateWebhook(Boolean enabled) {
        if (!accessAvailable.getAsBoolean()) return;
        var webhook = new com.zrlog.admin.business.rest.request.WebhookConfigRequest();
        webhook.setEnabled(enabled);
        new WebhookService().updateConfig(webhook);
    }

}
