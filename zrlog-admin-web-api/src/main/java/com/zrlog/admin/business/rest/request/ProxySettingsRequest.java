package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class ProxySettingsRequest implements Validator {
    private String url;
    private String noProxy;
    public String getUrl() { return url; }
    public String getNoProxy() { return noProxy; }
    @Override public void doValid() {
        if (url == null || noProxy == null || url.length() > 2048 || noProxy.length() > 4096) {
            throw new ArgsException("proxy");
        }
    }
}
