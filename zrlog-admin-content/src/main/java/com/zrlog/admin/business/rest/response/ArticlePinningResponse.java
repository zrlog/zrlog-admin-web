package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class ArticlePinningResponse {

    private List<ArticlePinningEntryResponse> items = new ArrayList<>();

    public List<ArticlePinningEntryResponse> getItems() {
        return items;
    }

    public void setItems(List<ArticlePinningEntryResponse> items) {
        this.items = items;
    }
}
