package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.UrlEncodeUtils;
import com.zrlog.admin.business.rest.request.TagManageRequest;
import com.zrlog.admin.business.rest.response.TagManagementArticleImpactResponse;
import com.zrlog.admin.business.rest.response.TagManagementEntryResponse;
import com.zrlog.admin.business.rest.response.TagManagementPreviewResponse;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.model.Log;
import com.zrlog.model.Tag;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TagManagementService {

    private static final int PREVIEW_LIMIT = 10;

    public PageData<TagManagementEntryResponse> find(String homeUrl, PageRequest pageRequest, String key) throws SQLException {
        List<TagManagementEntryResponse> allTags = new Tag().findAll().stream()
                .filter(tag -> StringUtils.isEmpty(key) || tag.getText().toLowerCase(Locale.ROOT)
                        .contains(key.toLowerCase(Locale.ROOT)))
                .sorted(Comparator
                        .comparing((TagDTO tag) -> Objects.requireNonNullElse(tag.getCount(), 0L)).reversed()
                        .thenComparing(TagDTO::getText, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(tag -> toEntry(homeUrl, tag))
                .collect(Collectors.toList());
        long page = Math.max(1L, pageRequest.getPage());
        long size = pageRequest.getSize() <= 0 ? 10L : pageRequest.getSize();
        int fromIndex = Math.toIntExact(Math.min((page - 1) * size, allTags.size()));
        int toIndex = Math.toIntExact(Math.min(fromIndex + size, allTags.size()));
        PageData<TagManagementEntryResponse> pageData = new PageData<>();
        pageData.setRows(allTags.subList(fromIndex, toIndex));
        pageData.setPage(page);
        pageData.setSize(size);
        pageData.setTotalElements(allTags.size());
        pageData.setKey(key);
        pageData.setDefaultPageSize(10L);
        pageData.setSort(List.of());
        return pageData;
    }

    private TagManagementEntryResponse toEntry(String homeUrl, TagDTO tag) {
        TagManagementEntryResponse response = new TagManagementEntryResponse();
        response.setId(tag.getId());
        response.setText(tag.getText());
        response.setCount(tag.getCount());
        String suffix = Constants.isStaticHtmlStatus() ? ".html" : "";
        response.setUrl(homeUrl + UrlEncodeUtils.encodeUrl(Constants.getArticleUri() + "tag/" + tag.getText()) + suffix);
        return response;
    }

    public TagManagementPreviewResponse preview(TagManageRequest request, String operation) throws SQLException {
        return buildPreview(request, operation, false);
    }

    public TagManagementPreviewResponse execute(TagManageRequest request, String operation) throws SQLException {
        TagManagementPreviewResponse preview = buildPreview(request, operation, true);
        if (preview.getUpdatedArticleCount() > 0) {
            new Tag().refreshTag();
        }
        return preview;
    }

    private TagManagementPreviewResponse buildPreview(TagManageRequest request, String operation, boolean execute)
            throws SQLException {
        String sourceTag = normalizeTag(request.getSourceTag());
        String targetTag = normalizeTag(request.getTargetTag());
        boolean remove = Objects.equals(operation, "delete");
        List<Map<String, Object>> rows = new Log().queryListWithParams(
                "select logId,title,keywords from " + Log.TABLE_NAME
                        + " where keywords is not null and keywords <> ''");
        TagManagementPreviewResponse response = new TagManagementPreviewResponse();
        response.setSourceTag(sourceTag);
        response.setTargetTag(remove ? "" : targetTag);
        response.setOperation(operation);
        Log logDao = new Log();
        long affected = 0;
        long updated = 0;
        for (Map<String, Object> row : rows) {
            String beforeKeywords = Objects.toString(row.get("keywords"), "");
            List<String> tags = parseTags(beforeKeywords);
            if (!tags.contains(sourceTag)) {
                continue;
            }
            String afterKeywords = joinTags(replaceTag(tags, sourceTag, targetTag, remove));
            if (Objects.equals(beforeKeywords, afterKeywords)) {
                continue;
            }
            affected += 1;
            if (response.getArticles().size() < PREVIEW_LIMIT) {
                response.getArticles().add(toImpact(row, beforeKeywords, afterKeywords));
            }
            if (execute) {
                Number logId = (Number) row.get("logId");
                logDao.set("keywords", afterKeywords.isEmpty() ? null : afterKeywords)
                        .set("last_update_date", new Date())
                        .updateById(logId);
                updated += 1;
            }
        }
        response.setAffectedArticleCount(affected);
        response.setUpdatedArticleCount(updated);
        response.setHasMore(affected > response.getArticles().size());
        response.setExecuted(execute);
        return response;
    }

    TagManagementArticleImpactResponse toImpact(Map<String, Object> row, String beforeKeywords,
                                                String afterKeywords) {
        TagManagementArticleImpactResponse impact = new TagManagementArticleImpactResponse();
        Number logId = (Number) row.get("logId");
        impact.setId(logId == null ? null : logId.longValue());
        impact.setTitle(Objects.toString(row.get("title"), ""));
        impact.setBeforeKeywords(beforeKeywords);
        impact.setAfterKeywords(afterKeywords);
        return impact;
    }

    List<String> parseTags(String keywords) {
        Set<String> tags = new LinkedHashSet<>();
        if (StringUtils.isEmpty(keywords)) {
            return new ArrayList<>();
        }
        for (String tag : keywords.split(",")) {
            String normalized = normalizeTag(tag);
            if (StringUtils.isNotEmpty(normalized)) {
                tags.add(normalized);
            }
        }
        return new ArrayList<>(tags);
    }

    List<String> replaceTag(List<String> tags, String sourceTag, String targetTag, boolean remove) {
        Set<String> replaced = new LinkedHashSet<>();
        for (String tag : tags) {
            if (Objects.equals(tag, sourceTag)) {
                if (!remove && StringUtils.isNotEmpty(targetTag)) {
                    replaced.add(targetTag);
                }
                continue;
            }
            replaced.add(tag);
        }
        return new ArrayList<>(replaced);
    }

    String joinTags(List<String> tags) {
        return String.join(",", tags);
    }

    String normalizeTag(String tag) {
        return Objects.requireNonNullElse(tag, "").trim();
    }
}
