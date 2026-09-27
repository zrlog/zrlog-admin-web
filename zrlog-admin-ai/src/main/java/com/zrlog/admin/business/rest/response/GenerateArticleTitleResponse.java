package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class GenerateArticleTitleResponse {

    private List<String> titles = new ArrayList<>();

    public List<String> getTitles() {
        return titles;
    }

    public void setTitles(List<String> titles) {
        this.titles = titles;
    }
}
