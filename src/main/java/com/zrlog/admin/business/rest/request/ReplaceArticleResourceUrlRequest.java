package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

import java.util.Objects;

public class ReplaceArticleResourceUrlRequest implements Validator {

    private String fromUrl;
    private String toUrl;

    public String getFromUrl() {
        return fromUrl;
    }

    public void setFromUrl(String fromUrl) {
        this.fromUrl = fromUrl == null ? null : fromUrl.trim();
    }

    public String getToUrl() {
        return toUrl;
    }

    public void setToUrl(String toUrl) {
        this.toUrl = toUrl == null ? null : toUrl.trim();
    }

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(fromUrl)) {
            throw new ArgsException("fromUrl");
        }
        if (toUrl == null) {
            throw new ArgsException("toUrl");
        }
        if (Objects.equals(fromUrl.trim(), toUrl.trim())) {
            throw new ArgsException("toUrl");
        }
    }

    @Override
    public void doClean() {
        if (fromUrl != null) {
            fromUrl = fromUrl.trim();
        }
        if (toUrl != null) {
            toUrl = toUrl.trim();
        }
    }
}
