package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.base.FeatureLabWebSiteInfo;
import java.util.Arrays;
import static com.zrlog.admin.business.service.WebSiteService.*;

/** Assembles the feature settings page across modules. */
public class FeatureLabService {
    public FeatureLabWebSiteInfo featureLab() {
        FeatureLabWebSiteInfo featureLab = com.hibegin.common.dao.ResultBeanUtils.convert(new com.zrlog.model.WebSite().getWebSiteByNameIn(Arrays.asList(FEATURE_RESOURCE_REFERENCE_ENABLED_KEY,
                FEATURE_ARTICLE_EXTENSION_FILTER_ENABLED_KEY,
                FEATURE_WEBHOOK_ENABLED_KEY, FEATURE_PERSONAL_DATA_ENABLED_KEY)), FeatureLabWebSiteInfo.class);
        featureLab.setFeature_webhook_enabled(com.zrlog.common.Constants.zrLogConfig != null
                && com.zrlog.common.Constants.zrLogConfig.getWebSetup(com.zrlog.admin.web.AccessWebSetup.class) != null
                && Boolean.TRUE.equals(new WebhookService().getConfigResponse().getEnabled()));
        featureLab.doValid();
        return featureLab;
    }

}
