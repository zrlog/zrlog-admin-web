package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class GenerateArticleTagsResponse {

    private List<String> tags = new ArrayList<>();

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
