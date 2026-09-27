package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;

import java.util.ArrayList;
import java.util.List;

public class AdminDashboardConfigRequest implements Validator {

    private List<AdminDashboardCardRequest> cards = new ArrayList<>();
    private Boolean autoRefreshEnabled = false;
    private Integer autoRefreshIntervalSeconds = 60;

    public List<AdminDashboardCardRequest> getCards() {
        return cards;
    }

    public void setCards(List<AdminDashboardCardRequest> cards) {
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

    @Override
    public void doValid() {

    }
}
