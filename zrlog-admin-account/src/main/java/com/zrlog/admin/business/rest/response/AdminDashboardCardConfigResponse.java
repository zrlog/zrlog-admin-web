package com.zrlog.admin.business.rest.response;

public class AdminDashboardCardConfigResponse {

    private String id;
    private Boolean enabled;
    private Integer sort;
    private String title;
    private Object data;

    public AdminDashboardCardConfigResponse() {
    }

    public AdminDashboardCardConfigResponse(String id, Boolean enabled, Integer sort) {
        this.id = id;
        this.enabled = enabled;
        this.sort = sort;
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
}
