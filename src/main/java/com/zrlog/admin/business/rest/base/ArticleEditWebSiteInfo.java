package com.zrlog.admin.business.rest.base;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.data.util.WebSiteUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ArticleEditWebSiteInfo implements Validator {

    public static final String DEFAULT_ARTICLE_COVER_ASPECT_RATIO = "16:9";
    public static final Long DEFAULT_ARTICLE_EDIT_AUTO_SAVE_INTERVAL = 5L;
    private static final List<String> SUPPORTED_ARTICLE_COVER_ASPECT_RATIOS = Arrays.asList("16:9", "4:3", "3:2", "1:1", "21:9");
    private static final List<Long> SUPPORTED_ARTICLE_EDIT_AUTO_SAVE_INTERVALS = Arrays.asList(2L, 5L, 10L);

    private Long article_auto_digest_length;
    private Long article_edit_auto_save_interval;
    private Boolean article_editor_link_preview_enabled;
    private Boolean article_publish_check_enabled;
    private String article_cover_aspect_ratio;

    public Long getArticle_auto_digest_length() {
        return article_auto_digest_length;
    }

    public void setArticle_auto_digest_length(Long article_auto_digest_length) {
        this.article_auto_digest_length = article_auto_digest_length;
    }

    public Long getArticle_edit_auto_save_interval() {
        return article_edit_auto_save_interval;
    }

    public void setArticle_edit_auto_save_interval(Long article_edit_auto_save_interval) {
        this.article_edit_auto_save_interval = article_edit_auto_save_interval;
    }

    public Boolean getArticle_editor_link_preview_enabled() {
        return article_editor_link_preview_enabled;
    }

    public void setArticle_editor_link_preview_enabled(Boolean article_editor_link_preview_enabled) {
        this.article_editor_link_preview_enabled = article_editor_link_preview_enabled;
    }

    public Boolean getArticle_publish_check_enabled() {
        return article_publish_check_enabled;
    }

    public void setArticle_publish_check_enabled(Boolean article_publish_check_enabled) {
        this.article_publish_check_enabled = article_publish_check_enabled;
    }

    public String getArticle_cover_aspect_ratio() {
        return article_cover_aspect_ratio;
    }

    public void setArticle_cover_aspect_ratio(String article_cover_aspect_ratio) {
        this.article_cover_aspect_ratio = article_cover_aspect_ratio;
    }

    public static String normalizeArticleCoverAspectRatio(String aspectRatio) {
        if (StringUtils.isEmpty(aspectRatio) || !SUPPORTED_ARTICLE_COVER_ASPECT_RATIOS.contains(aspectRatio)) {
            return DEFAULT_ARTICLE_COVER_ASPECT_RATIO;
        }
        return aspectRatio;
    }

    public static Long normalizeArticleEditAutoSaveInterval(Long interval) {
        if (Objects.isNull(interval) || !SUPPORTED_ARTICLE_EDIT_AUTO_SAVE_INTERVALS.contains(interval)) {
            return DEFAULT_ARTICLE_EDIT_AUTO_SAVE_INTERVAL;
        }
        return interval;
    }

    @Override
    public void doValid() {
        if (Objects.isNull(article_auto_digest_length)) {
            article_auto_digest_length = WebSiteUtils.DEFAULT_ARTICLE_DIGEST_LENGTH;
        }
        article_edit_auto_save_interval = normalizeArticleEditAutoSaveInterval(article_edit_auto_save_interval);
        article_editor_link_preview_enabled = Objects.equals(article_editor_link_preview_enabled, true);
        article_publish_check_enabled = !Objects.equals(article_publish_check_enabled, false);
        article_cover_aspect_ratio = normalizeArticleCoverAspectRatio(article_cover_aspect_ratio);
    }
}
