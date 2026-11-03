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
import com.zrlog.data.repository.LogRepository;
import com.zrlog.data.repository.TagRepository;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;

import java.sql.SQLException;
import com.zrlog.data.dto.ArticleBasicDTO;
import java.util.*;
import java.util.stream.Collectors;

public class TagManagementService {
    private static final int PREVIEW_LIMIT = 10;
    private final jakarta.inject.Provider<LogRepository> logProvider;
    private final jakarta.inject.Provider<TagRepository> tagProvider;
    private final ArticleVersionService versions;

    @Inject
    public TagManagementService(
            jakarta.inject.Provider<LogRepository> logProvider, jakarta.inject.Provider<TagRepository> tagProvider,
            ArticleVersionService versions) {
        this.logProvider = logProvider;
        this.tagProvider = tagProvider;
        this.versions = versions;
    }

    public PageData<TagManagementEntryResponse> find(String homeUrl, PageRequest pageRequest, String key, boolean staticHtml) throws SQLException {
        List<TagManagementEntryResponse> allTags =
                this.tagProvider.get().findAll().stream()
                        .filter(
                                tag ->
                                        StringUtils.isEmpty(key)
                                                || tag.getText()
                                                .toLowerCase(Locale.ROOT)
                                                .contains(key.toLowerCase(Locale.ROOT)))
                        .sorted(
                                Comparator.comparing(
                                                (TagDTO tag) ->
                                                        Objects.requireNonNullElse(
                                                                tag.getCount(), 0L))
                                        .reversed()
                                        .thenComparing(
                                                TagDTO::getText,
                                                Comparator.nullsLast(String::compareToIgnoreCase)))
                        .map(tag -> toEntry(homeUrl, tag, staticHtml))
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

    private TagManagementEntryResponse toEntry(String homeUrl, TagDTO tag, boolean staticHtml) {
        TagManagementEntryResponse response = new TagManagementEntryResponse();
        response.setId(tag.getId());
        response.setText(tag.getText());
        response.setCount(tag.getCount());
        String suffix = staticHtml ? ".html" : "";
        response.setUrl(homeUrl + UrlEncodeUtils.encodeUrl(Constants.getArticleUri() + "tag/" + tag.getText()) + suffix);
        return response;
    }

    public TagManagementPreviewResponse preview(TagManageRequest request, String operation) throws SQLException {
        return buildPreview(request, operation, null);
    }

    public TagManagementPreviewResponse execute(AccountAccess actor, TagManageRequest request, String operation) throws SQLException {
        AccountPermissionService.requireAction(actor, AccountAction.TAXONOMY_MANAGE.getId());
        TagManagementPreviewResponse preview = buildPreview(request, operation, actor);
        if (preview.getUpdatedArticleCount() > 0) {
            this.tagProvider.get().refreshTag();
        }
        return preview;
    }

    private TagManagementPreviewResponse buildPreview(TagManageRequest request, String operation, AccountAccess actor)
            throws SQLException {
        boolean execute = actor != null;
        String sourceTag = normalizeTag(request.getSourceTag());
        String targetTag = normalizeTag(request.getTargetTag());
        boolean remove = Objects.equals(operation, "delete");
        List<ArticleBasicDTO> rows =
                this.logProvider
                        .get()
                        .queryList(
                                "select logId,title,keywords,version from "
                                        + LogRepository.TABLE_NAME
                                        + " where keywords is not null and keywords <> ''", ArticleBasicDTO.class);
        TagManagementPreviewResponse response = new TagManagementPreviewResponse();
        response.setSourceTag(sourceTag);
        response.setTargetTag(remove ? "" : targetTag);
        response.setOperation(operation);
        LogRepository logDao = this.logProvider.get();
        long affected = 0;
        long updated = 0;
        for (ArticleBasicDTO row : rows) {
            String beforeKeywords = Objects.toString(row.getKeywords(), "");
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
                Number logId = (Number) row.getLogId();
                int version = ((Number) row.getVersion()).intValue();
                // This explicit projection supports a conditional bulk edit without loading article bodies.
                if (version == Integer.MAX_VALUE || !logDao.execute("UPDATE log SET keywords=?,last_update_date=?,version=version+1 WHERE logId=? AND keywords=? AND version=?",
                        afterKeywords.isEmpty() ? null : afterKeywords,
                        new java.sql.Timestamp(System.currentTimeMillis()).toString(), logId, beforeKeywords, version)) continue;
                versions.recordKeywordPatch(logId.intValue(), version + 1, row.getTitle(),
                        beforeKeywords, afterKeywords.isEmpty() ? null : afterKeywords, actor.getUserId());
                updated += 1;
            }
        }
        response.setAffectedArticleCount(affected);
        response.setUpdatedArticleCount(updated);
        response.setHasMore(affected > response.getArticles().size());
        response.setExecuted(execute);
        return response;
    }

    TagManagementArticleImpactResponse toImpact(ArticleBasicDTO row, String beforeKeywords,
                                                String afterKeywords) {
        TagManagementArticleImpactResponse impact = new TagManagementArticleImpactResponse();
        Number logId = (Number) row.getLogId();
        impact.setId(logId == null ? null : logId.longValue());
        impact.setTitle(Objects.toString(row.getTitle(), ""));
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
