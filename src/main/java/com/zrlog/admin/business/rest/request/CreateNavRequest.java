package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.base.AbstractNavEntry;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.net.URI;
import java.util.Objects;

public class CreateNavRequest extends AbstractNavEntry implements Validator {

    @Override
    public void doValid() {
        if (Objects.isNull(url) || url.trim().isEmpty()) {
            throw new ArgsException("url");
        }
        if (Objects.isNull(navName) || navName.trim().isEmpty()) {
            throw new ArgsException("navName");
        }
    }

    @Override
    public void doClean() {
        if (StringUtils.isNotEmpty(this.getNavName())) {
            this.setNavName(Jsoup.clean(this.getNavName(), Safelist.none()));
        }
        if (StringUtils.isNotEmpty(this.getUrl())) {
            this.setUrl(URI.create(this.getUrl()).toString());
        }
    }
}
