package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class AddArticleAIContextRequest implements Validator {

    private String title;
    private String markdown;
    private String digest;
    private String keywords;
    private Integer articleVersion;

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

    public Integer getArticleVersion() {
        return articleVersion;
    }

    public void setArticleVersion(Integer articleVersion) {
        this.articleVersion = articleVersion;
    }

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(title) && StringUtils.isEmpty(markdown)
                && StringUtils.isEmpty(digest) && StringUtils.isEmpty(keywords)) {
            throw new ArgsException("markdown");
        }
    }
}
