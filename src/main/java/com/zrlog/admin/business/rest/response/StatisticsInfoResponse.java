package com.zrlog.admin.business.rest.response;

import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import java.util.List;

public class StatisticsInfoResponse {

    private Long commCount;
    private Long toDayCommCount;
    private Long clickCount;
    private Long articleCount;
    private Long draftCount;
    private Long privateCount;
    private Long publishedCount;
    private List<TypeDTO> typeData;
    private List<TagDTO> tagData;
    private List<java.util.Map<String, Object>> auditLogs;
    private String usedCacheSpace;
    private String usedDiskSpace;

    public Long getCommCount() {
        return commCount;
    }

    public void setCommCount(Long commCount) {
        this.commCount = commCount;
    }

    public Long getToDayCommCount() {
        return toDayCommCount;
    }

    public void setToDayCommCount(Long toDayCommCount) {
        this.toDayCommCount = toDayCommCount;
    }

    public Long getClickCount() {
        return clickCount;
    }

    public void setClickCount(Long clickCount) {
        this.clickCount = clickCount;
    }

    public Long getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(Long articleCount) {
        this.articleCount = articleCount;
    }

    public Long getDraftCount() {
        return draftCount;
    }

    public void setDraftCount(Long draftCount) {
        this.draftCount = draftCount;
    }

    public Long getPrivateCount() {
        return privateCount;
    }

    public void setPrivateCount(Long privateCount) {
        this.privateCount = privateCount;
    }

    public Long getPublishedCount() {
        return publishedCount;
    }

    public void setPublishedCount(Long publishedCount) {
        this.publishedCount = publishedCount;
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

    public List<java.util.Map<String, Object>> getAuditLogs() {
        return auditLogs;
    }

    public void setAuditLogs(List<java.util.Map<String, Object>> auditLogs) {
        this.auditLogs = auditLogs;
    }

    public String getUsedCacheSpace() {
        return usedCacheSpace;
    }

    public void setUsedCacheSpace(String usedCacheSpace) {
        this.usedCacheSpace = usedCacheSpace;
    }

    public String getUsedDiskSpace() {
        return usedDiskSpace;
    }

    public void setUsedDiskSpace(String usedDiskSpace) {
        this.usedDiskSpace = usedDiskSpace;
    }
}
