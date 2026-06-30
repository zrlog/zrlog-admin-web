package com.zrlog.admin.business.rest.response;

import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;

import java.util.List;

public class AdminDashboardDataInsightsResponse {

    private List<TypeDTO> typeData;
    private List<TagDTO> tagData;

    public AdminDashboardDataInsightsResponse() {
    }

    public AdminDashboardDataInsightsResponse(List<TypeDTO> typeData, List<TagDTO> tagData) {
        this.typeData = typeData;
        this.tagData = tagData;
    }

    public List<TypeDTO> getTypeData() {
        return typeData;
    }

    public void setTypeData(List<TypeDTO> typeData) {
        this.typeData = typeData;
    }

    public List<TagDTO> getTagData() {
        return tagData;
    }

    public void setTagData(List<TagDTO> tagData) {
        this.tagData = tagData;
    }
}
