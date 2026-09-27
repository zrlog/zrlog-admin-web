package com.zrlog.admin.business.rest.response;

public class ArticleResponseEntry {

    private String userName;
    private Long id;
    private String title;
    private Integer click;
    private String keywords;
    private String markdown;
    private String digest;
    private String releaseTime;

    private String lastUpdateDate;
    private String typeName;
    private Boolean rubbish;
    private Boolean canComment;
    private Boolean recommended;
    private Long commentSize;
    private Boolean privacy;
    private String url;
    private String alias;
    private String arrange_plugin;
    private String typeAlias;
    private String thumbnail;
    private Long sticky;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getClick() {
        return click;
    }

    public void setClick(Integer click) {
        this.click = click;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
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

    public String getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(String releaseTime) {
        this.releaseTime = releaseTime;
    }

    public String getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(String lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Boolean getRubbish() {
        return rubbish;
    }

    public void setRubbish(Boolean rubbish) {
        this.rubbish = rubbish;
    }

    public Boolean getCanComment() {
        return canComment;
    }

    public void setCanComment(Boolean canComment) {
        this.canComment = canComment;
    }

    public Boolean getRecommended() {
        return recommended;
    }

    public void setRecommended(Boolean recommended) {
        this.recommended = recommended;
    }

    public Long getCommentSize() {
        return commentSize;
    }

    public void setCommentSize(Long commentSize) {
        this.commentSize = commentSize;
    }

    public Boolean getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Boolean privacy) {
        this.privacy = privacy;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getArrange_plugin() {
        return arrange_plugin;
    }

    public void setArrange_plugin(String arrange_plugin) {
        this.arrange_plugin = arrange_plugin;
    }

    public String getTypeAlias() {
        return typeAlias;
    }

    public void setTypeAlias(String typeAlias) {
        this.typeAlias = typeAlias;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public Long getSticky() {
        return sticky;
    }

    public void setSticky(Long sticky) {
        this.sticky = sticky;
    }
}
