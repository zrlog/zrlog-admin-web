package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class AdminDashboardConfigResponse {

    private List<AdminDashboardCardResponse> cards = new ArrayList<>();
    private Boolean autoRefreshEnabled = false;
    private Integer autoRefreshIntervalSeconds = 60;

    public List<AdminDashboardCardResponse> getCards() {
        return cards;
    }

    public void setCards(List<AdminDashboardCardResponse> cards) {
        this.cards = cards;
    }

    public Boolean getAutoRefreshEnabled() {
        return autoRefreshEnabled;
    }

    public void setAutoRefreshEnabled(Boolean autoRefreshEnabled) {
        this.autoRefreshEnabled = autoRefreshEnabled;
    }

    public Integer getAutoRefreshIntervalSeconds() {
        return autoRefreshIntervalSeconds;
    }

    public void setAutoRefreshIntervalSeconds(Integer autoRefreshIntervalSeconds) {
        this.autoRefreshIntervalSeconds = autoRefreshIntervalSeconds;
    }
}
