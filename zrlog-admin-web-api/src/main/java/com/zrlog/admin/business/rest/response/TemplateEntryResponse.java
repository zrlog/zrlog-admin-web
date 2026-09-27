package com.zrlog.admin.business.rest.response;

import com.zrlog.common.vo.BaseTemplateVO;

public class TemplateEntryResponse extends BaseTemplateVO {

    private boolean builtIn;

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }
}
