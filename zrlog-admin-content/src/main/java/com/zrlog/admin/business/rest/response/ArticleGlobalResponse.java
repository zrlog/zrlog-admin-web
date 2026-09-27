package com.zrlog.admin.business.rest.response;

import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;

import java.util.List;

public class ArticleGlobalResponse {

    private List<TagDTO> tags;
    private List<TypeDTO> types;
    private LoadEditArticleResponse article;
    private Boolean linkPreviewEnabled;
    private Boolean publishCheckEnabled;
    private String articleCoverAspectRatio;
    private Long articleEditAutoSaveInterval;

    public LoadEditArticleResponse getArticle() {
        return article;
    }

    public void setArticle(LoadEditArticleResponse article) {
        this.article = article;
    }

    public List<TagDTO> getTags() {
        return tags;
    }

    public void setTags(List<TagDTO> tags) {
        this.tags = tags;
    }

    public List<TypeDTO> getTypes() {
        return types;
    }

    public void setTypes(List<TypeDTO> types) {
        this.types = types;
    }

    public Boolean getLinkPreviewEnabled() {
        return linkPreviewEnabled;
    }

    public void setLinkPreviewEnabled(Boolean linkPreviewEnabled) {
        this.linkPreviewEnabled = linkPreviewEnabled;
    }

    public Boolean getPublishCheckEnabled() {
        return publishCheckEnabled;
    }

    public void setPublishCheckEnabled(Boolean publishCheckEnabled) {
        this.publishCheckEnabled = publishCheckEnabled;
    }

    public String getArticleCoverAspectRatio() {
        return articleCoverAspectRatio;
    }

    public void setArticleCoverAspectRatio(String articleCoverAspectRatio) {
        this.articleCoverAspectRatio = articleCoverAspectRatio;
    }

    public Long getArticleEditAutoSaveInterval() {
        return articleEditAutoSaveInterval;
    }

    public void setArticleEditAutoSaveInterval(Long articleEditAutoSaveInterval) {
        this.articleEditAutoSaveInterval = articleEditAutoSaveInterval;
    }
}
