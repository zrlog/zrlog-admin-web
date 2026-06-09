package com.zrlog.admin.business.rest.response;

public class AdminDashboardCardResponse {

    private String kind;
    private String id;
    private Boolean enabled;
    private Integer sort;
    private String title;
    private Object data;
    private String error;
    private Boolean surfaceLoaded;

    private String type;
    private String surfaceUrl;
    private String actionUrl;
    private String pluginName;
    private String viewUrl;
    private Integer maxItems;
    private Integer height;
    private Integer order;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Boolean getSurfaceLoaded() {
        return surfaceLoaded;
    }

    public void setSurfaceLoaded(Boolean surfaceLoaded) {
        this.surfaceLoaded = surfaceLoaded;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSurfaceUrl() {
        return surfaceUrl;
    }

    public void setSurfaceUrl(String surfaceUrl) {
        this.surfaceUrl = surfaceUrl;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public String getViewUrl() {
        return viewUrl;
    }

    public void setViewUrl(String viewUrl) {
        this.viewUrl = viewUrl;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(Integer maxItems) {
        this.maxItems = maxItems;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }
}
