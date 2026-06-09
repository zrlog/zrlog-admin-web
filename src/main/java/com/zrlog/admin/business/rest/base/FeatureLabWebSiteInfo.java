package com.zrlog.admin.business.rest.base;

import com.zrlog.common.Validator;

import java.util.Objects;

public class FeatureLabWebSiteInfo implements Validator {

    private Boolean feature_resource_reference_enabled;
    private Boolean feature_webhook_enabled;
    private Boolean feature_personal_data_enabled;

    public Boolean getFeature_resource_reference_enabled() {
        return feature_resource_reference_enabled;
    }

    public void setFeature_resource_reference_enabled(Boolean feature_resource_reference_enabled) {
        this.feature_resource_reference_enabled = feature_resource_reference_enabled;
    }

    public Boolean getFeature_webhook_enabled() {
        return feature_webhook_enabled;
    }

    public void setFeature_webhook_enabled(Boolean feature_webhook_enabled) {
        this.feature_webhook_enabled = feature_webhook_enabled;
    }

    public Boolean getFeature_personal_data_enabled() {
        return feature_personal_data_enabled;
    }

    public void setFeature_personal_data_enabled(Boolean feature_personal_data_enabled) {
        this.feature_personal_data_enabled = feature_personal_data_enabled;
    }

    @Override
    public void doValid() {
        feature_resource_reference_enabled = Objects.equals(feature_resource_reference_enabled, true);
        feature_webhook_enabled = Objects.equals(feature_webhook_enabled, true);
        feature_personal_data_enabled = Objects.equals(feature_personal_data_enabled, true);
    }
}
