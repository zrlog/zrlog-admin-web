package com.zrlog.admin.business.rest.response;

import java.util.List;

public class StaticSiteProgressResponse {

    private int total;
    private int handled;
    private int handing;
    private int pending;
    private int retrying;
    private List<String> siteTypes;

    public StaticSiteProgressResponse() {
    }

    public StaticSiteProgressResponse(int total, int handled, int handing, int pending, int retrying,
                                      List<String> siteTypes) {
        this.total = total;
        this.handled = handled;
        this.handing = handing;
        this.pending = pending;
        this.retrying = retrying;
        this.siteTypes = siteTypes;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getHandled() {
        return handled;
    }

    public void setHandled(int handled) {
        this.handled = handled;
    }

    public int getHanding() {
        return handing;
    }

    public void setHanding(int handing) {
        this.handing = handing;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public int getRetrying() {
        return retrying;
    }

    public void setRetrying(int retrying) {
        this.retrying = retrying;
    }

    public List<String> getSiteTypes() {
        return siteTypes;
    }

    public void setSiteTypes(List<String> siteTypes) {
        this.siteTypes = siteTypes;
    }
}
