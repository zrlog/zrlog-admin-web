package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class GenerateArticleFieldRequest implements Validator {

    private String title;
    private String markdown;
    private String digest;
    private String keywords;
    private String selectedText;
    private String alias;
    private String thumbnail;
    private Boolean transparentPublish;
    private Boolean staticSiteEnabled;
    private Boolean staticSitePluginEnabled;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public void setSelectedText(String selectedText) {
        this.selectedText = selectedText;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public Boolean getTransparentPublish() {
        return transparentPublish;
    }

    public void setTransparentPublish(Boolean transparentPublish) {
        this.transparentPublish = transparentPublish;
    }

    public Boolean getStaticSiteEnabled() {
        return staticSiteEnabled;
    }

    public void setStaticSiteEnabled(Boolean staticSiteEnabled) {
        this.staticSiteEnabled = staticSiteEnabled;
    }

    public Boolean getStaticSitePluginEnabled() {
        return staticSitePluginEnabled;
    }

    public void setStaticSitePluginEnabled(Boolean staticSitePluginEnabled) {
        this.staticSitePluginEnabled = staticSitePluginEnabled;
    }

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(title) && StringUtils.isEmpty(markdown) && StringUtils.isEmpty(digest)
                && StringUtils.isEmpty(selectedText)) {
            throw new ArgsException("markdown");
        }
    }
}
