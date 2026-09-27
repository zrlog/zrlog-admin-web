package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class TagManageRequest implements Validator {

    private String sourceTag;
    private String targetTag;

    public String getSourceTag() {
        return sourceTag;
    }

    public void setSourceTag(String sourceTag) {
        this.sourceTag = sourceTag;
    }

    public String getTargetTag() {
        return targetTag;
    }

    public void setTargetTag(String targetTag) {
        this.targetTag = targetTag;
    }

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(sourceTag) || sourceTag.trim().isEmpty()) {
            throw new ArgsException("sourceTag");
        }
    }

    @Override
    public void doClean() {
        if (StringUtils.isNotEmpty(sourceTag)) {
            sourceTag = Jsoup.clean(sourceTag.trim(), Safelist.none());
        }
        if (StringUtils.isNotEmpty(targetTag)) {
            targetTag = Jsoup.clean(targetTag.trim(), Safelist.none());
        }
    }
}
