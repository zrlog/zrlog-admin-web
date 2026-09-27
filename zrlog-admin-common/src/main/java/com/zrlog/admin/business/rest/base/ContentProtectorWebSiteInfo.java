package com.zrlog.admin.business.rest.base;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.data.util.WebSiteUtils;

import java.util.Arrays;
import java.util.List;

public class ContentProtectorWebSiteInfo implements Validator {

    private static final List<String> SUPPORTED_LICENSE_TYPES = Arrays.asList(
            "ALL_RIGHTS_RESERVED",
            "CC_BY_4_0",
            "CC_BY_SA_4_0",
            "CC_BY_ND_4_0",
            "CC_BY_NC_4_0",
            "CC_BY_NC_SA_4_0",
            "CC_BY_NC_ND_4_0"
    );

    private Boolean content_protector_enabled;
    private String content_protector_license_type;
    private String content_protector_template;

    public Boolean getContent_protector_enabled() {
        return content_protector_enabled;
    }

    public void setContent_protector_enabled(Boolean content_protector_enabled) {
        this.content_protector_enabled = content_protector_enabled;
    }

    public String getContent_protector_license_type() {
        return content_protector_license_type;
    }

    public void setContent_protector_license_type(String content_protector_license_type) {
        this.content_protector_license_type = content_protector_license_type;
    }

    public String getContent_protector_template() {
        return content_protector_template;
    }

    public void setContent_protector_template(String content_protector_template) {
        this.content_protector_template = content_protector_template;
    }

    @Override
    public void doValid() {
        content_protector_enabled = Boolean.TRUE.equals(content_protector_enabled);
        if (!SUPPORTED_LICENSE_TYPES.contains(content_protector_license_type)) {
            content_protector_license_type = WebSiteUtils.DEFAULT_CONTENT_PROTECTOR_LICENSE_TYPE;
        }
        if (StringUtils.isEmpty(content_protector_template)) {
            content_protector_template = WebSiteUtils.DEFAULT_CONTENT_PROTECTOR_TEMPLATE;
        }
    }
}
