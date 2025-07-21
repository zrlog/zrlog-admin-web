package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.request.ArticleVersionRollbackRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.ArticleVersionCompareResponse;
import com.zrlog.admin.business.rest.response.ArticleVersionResponse;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.admin.business.rest.response.LoadEditArticleResponse;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.exception.NotFindDbEntryException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.model.Log;
import com.zrlog.model.LogVersion;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class ArticleVersionService {

    private static final Gson GSON = new Gson();
    private static final Type PATCH_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();
    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "title", "content", "markdown", "digest", "keywords", "alias", "thumbnail",
            "typeId", "canComment", "privacy", "rubbish", "editorType"
    );

    private final AdminArticleService adminArticleService;
    private final LogVersion logVersion = new LogVersion();

    public ArticleVersionService() {
        this(new AdminArticleService());
    }

    public ArticleVersionService(AdminArticleService adminArticleService) {
        this.adminArticleService = adminArticleService;
    }

    public void recordReversePatch(Map<String, Object> oldLog, Map<String, Object> newLog, Integer userId) throws SQLException {
        if (oldLog == null || newLog == null) {
            return;
        }
        Integer logId = toInteger(newLog.get("logId"));
        Integer articleVersion = toInteger(newLog.get("version"));
        if (logId == null || articleVersion == null || articleVersion <= 0) {
            return;
        }
        Map<String, Object> patch = buildReversePatch(oldLog, newLog);
        if (patch.isEmpty()) {
            return;
        }
        logVersion.deleteByLogIdAndArticleVersion(logId, articleVersion);
        logVersion.savePatch(
                logId,
                articleVersion,
                GSON.toJson(patch),
                userId,
                Objects.toString(oldLog.get("title"), ""),
                new Date(),
                articleVersion - 1
        );
    }

    public List<ArticleVersionResponse> listVersions(Integer logId) throws SQLException {
        LoadEditArticleResponse current = adminArticleService.loadDetail(logId.toString(), null);
        List<ArticleVersionResponse> responses = new ArrayList<>();
        ArticleVersionResponse currentVersion = new ArticleVersionResponse();
        currentVersion.setVersion(current.getVersion());
        currentVersion.setCreatedAt(current.getLastUpdateDate());
        currentVersion.setUserId(null);
        currentVersion.setTitle(current.getTitle());
        currentVersion.setCurrent(true);
        responses.add(currentVersion);

        List<Map<String, Object>> rows = logVersion.findVersionList(logId);
        for (Map<String, Object> row : rows) {
            ArticleVersionResponse item = new ArticleVersionResponse();
            item.setVersion(toInteger(row.get("from_version")));
            item.setCreatedAt(ResultValueConvertUtils.parseDate(row.get("created_at")));
            item.setUserId(toInteger(row.get("user_id")));
            item.setTitle(Objects.toString(row.get("title"), ""));
            item.setCurrent(false);
            if (item.getVersion() != null) {
                responses.add(item);
            }
        }
        return responses.stream()
                .filter(e -> e.getVersion() != null)
                .collect(Collectors.toMap(ArticleVersionResponse::getVersion, e -> e, (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(ArticleVersionResponse::getVersion).reversed())
                .collect(Collectors.toList());
    }

    public ArticleVersionCompareResponse compare(Integer logId, Integer fromVersion, Integer toVersion) throws SQLException {
        LoadEditArticleResponse from = loadVersion(logId, fromVersion);
        LoadEditArticleResponse to = loadVersion(logId, toVersion);
        ArticleVersionCompareResponse response = new ArticleVersionCompareResponse();
        response.setFromVersion(fromVersion);
        response.setToVersion(toVersion);
        response.setFromArticle(from);
        response.setToArticle(to);
        response.setChangedFields(SNAPSHOT_FIELDS.stream()
                .filter(field -> !Objects.equals(getSnapshotValue(from, field), getSnapshotValue(to, field)))
                .collect(Collectors.toList()));
        return response;
    }

    public CreateOrUpdateArticleResponse rollback(AdminTokenVO user, ArticleVersionRollbackRequest request) throws SQLException {
        if (request.getLogId() == null || request.getVersion() == null || request.getTargetVersion() == null) {
            throw new ArgsException("logId/version/targetVersion");
        }
        LoadEditArticleResponse target = loadVersion(request.getLogId(), request.getTargetVersion());
        UpdateArticleRequest updateArticleRequest = BeanUtil.convert(target, UpdateArticleRequest.class);
        updateArticleRequest.setLogId(request.getLogId());
        updateArticleRequest.setVersion(request.getVersion());
        return adminArticleService.update(user, updateArticleRequest);
    }

    public LoadEditArticleResponse loadVersion(Integer logId, Integer targetVersion) throws SQLException {
        if (logId == null || targetVersion == null) {
            throw new ArgsException("logId/targetVersion");
        }
        LoadEditArticleResponse current = adminArticleService.loadDetail(logId.toString(), null);
        if (current.getVersion() == null || targetVersion > current.getVersion() || targetVersion < 0) {
            throw new NotFindDbEntryException();
        }
        if (Objects.equals(current.getVersion(), targetVersion)) {
            return current;
        }
        List<Map<String, Object>> rows = logVersion.findReversePatchesGreaterThanVersion(logId, targetVersion);
        LoadEditArticleResponse snapshot = cloneArticle(current);
        for (Map<String, Object> row : rows) {
            Integer version = toInteger(row.get("article_version"));
            if (version == null || version > snapshot.getVersion()) {
                continue;
            }
            applyReversePatch(snapshot, Objects.toString(row.get("patch_json"), ""));
            snapshot.setVersion(version - 1);
            if (Objects.equals(snapshot.getVersion(), targetVersion)) {
                return snapshot;
            }
        }
        if (!Objects.equals(snapshot.getVersion(), targetVersion)) {
            throw new NotFindDbEntryException();
        }
        return snapshot;
    }

    private static Map<String, Object> getSnapshotValueMap(LoadEditArticleResponse article) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", article.getTitle());
        map.put("content", article.getContent());
        map.put("markdown", article.getMarkdown());
        map.put("digest", article.getDigest());
        map.put("keywords", article.getKeywords());
        map.put("alias", article.getAlias());
        map.put("thumbnail", article.getThumbnail());
        map.put("typeId", article.getTypeId());
        map.put("canComment", article.isCanComment());
        map.put("privacy", article.isPrivacy());
        map.put("rubbish", article.isRubbish());
        map.put("editorType", article.getEditorType());
        return map;
    }

    private static Object getSnapshotValue(LoadEditArticleResponse article, String field) {
        return getSnapshotValueMap(article).get(field);
    }

    private LoadEditArticleResponse cloneArticle(LoadEditArticleResponse current) {
        LoadEditArticleResponse snapshot = BeanUtil.convert(current, LoadEditArticleResponse.class);
        snapshot.setVersion(current.getVersion());
        snapshot.setLogId(current.getLogId());
        snapshot.setPreviewUrl(current.getPreviewUrl());
        snapshot.setLastUpdateDate(current.getLastUpdateDate());
        return snapshot;
    }

    private void applyReversePatch(LoadEditArticleResponse article, String patchJson) {
        if (StringUtils.isEmpty(patchJson)) {
            return;
        }
        Map<String, Object> patch = GSON.fromJson(patchJson, PATCH_TYPE);
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            setSnapshotValue(article, entry.getKey(), entry.getValue());
        }
    }

    private void setSnapshotValue(LoadEditArticleResponse article, String field, Object value) {
        if (value instanceof Map) {
            Map<String, Object> delta = (Map<String, Object>) value;
            String type = Objects.toString(delta.get("type"), "");
            if (Objects.equals(type, "text")) {
                String current = Objects.toString(getSnapshotValue(article, field), "");
                value = applyTextReversePatch(current, delta);
            } else if (Objects.equals(type, "value")) {
                value = delta.get("old");
            }
        }
        switch (field) {
            case "title":
                article.setTitle(toStringValue(value));
                break;
            case "content":
                article.setContent(toStringValue(value));
                break;
            case "markdown":
                article.setMarkdown(toStringValue(value));
                break;
            case "digest":
                article.setDigest(toStringValue(value));
                break;
            case "keywords":
                article.setKeywords(toStringValue(value));
                break;
            case "alias":
                article.setAlias(toStringValue(value));
                break;
            case "thumbnail":
                article.setThumbnail(toStringValue(value));
                break;
            case "typeId":
                Integer typeId = toInteger(value);
                article.setTypeId(typeId == null ? null : typeId.longValue());
                break;
            case "canComment":
                article.setCanComment(toBoolean(value));
                break;
            case "privacy":
                article.setPrivacy(toBoolean(value));
                break;
            case "rubbish":
                article.setRubbish(toBoolean(value));
                break;
            case "editorType":
                article.setEditorType(toStringValue(value));
                break;
            default:
        }
    }

    private static String applyTextReversePatch(String current, Map<String, Object> delta) {
        Integer prefixValue = toInteger(delta.get("prefixLength"));
        Integer suffixValue = toInteger(delta.get("suffixLength"));
        int prefix = Objects.requireNonNullElse(prefixValue, 0);
        int suffix = Objects.requireNonNullElse(suffixValue, 0);
        String oldMiddle = Objects.toString(delta.get("oldMiddle"), "");
        if (current == null) {
            current = "";
        }
        if (prefix + suffix > current.length()) {
            return current;
        }
        return current.substring(0, prefix) + oldMiddle + current.substring(current.length() - suffix);
    }

    private Map<String, Object> buildReversePatch(Map<String, Object> oldLog, Map<String, Object> newLog) {
        Map<String, Object> patch = new LinkedHashMap<>();
        for (String field : SNAPSHOT_FIELDS) {
            Object oldValue = getLogFieldValue(oldLog, field);
            Object newValue = getLogFieldValue(newLog, field);
            Map<String, Object> fieldPatch = buildFieldReversePatch(oldValue, newValue);
            if (fieldPatch != null) {
                patch.put(field, fieldPatch);
            }
        }
        return patch;
    }

    private Map<String, Object> buildFieldReversePatch(Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return null;
        }
        if (oldValue instanceof String && newValue instanceof String) {
            String oldStr = (String) oldValue;
            String newStr = (String) newValue;
            int prefixLength = commonPrefixLength(oldStr, newStr);
            int suffixLength = commonSuffixLength(oldStr, newStr, prefixLength);
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("type", "text");
            patch.put("prefixLength", prefixLength);
            patch.put("suffixLength", suffixLength);
            patch.put("oldMiddle", oldStr.substring(prefixLength, oldStr.length() - suffixLength));
            return patch;
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("type", "value");
        patch.put("old", oldValue);
        return patch;
    }

    private Object getLogFieldValue(Map<String, Object> log, String field) {
        switch (field) {
            case "typeId":
                return log.get("typeId");
            case "editorType":
                return log.get("editor_type");
            default:
                return log.get(field);
        }
    }

    private static int commonPrefixLength(String oldStr, String newStr) {
        int max = Math.min(oldStr.length(), newStr.length());
        int index = 0;
        while (index < max && oldStr.charAt(index) == newStr.charAt(index)) {
            index++;
        }
        return index;
    }

    private static int commonSuffixLength(String oldStr, String newStr, int prefixLength) {
        int max = Math.min(oldStr.length(), newStr.length()) - prefixLength;
        int index = 0;
        while (index < max &&
                oldStr.charAt(oldStr.length() - 1 - index) == newStr.charAt(newStr.length() - 1 - index)) {
            index++;
        }
        return index;
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
