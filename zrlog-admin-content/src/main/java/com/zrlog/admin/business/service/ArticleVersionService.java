package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.response.ArticleVersionCompareResponse;
import com.zrlog.admin.business.rest.response.ArticleVersionResponse;
import com.zrlog.admin.business.rest.response.LoadEditArticleResponse;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.exception.NotFindDbEntryException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.repository.LogRepository;
import com.zrlog.admin.data.repository.LogVersionRepository;
import jakarta.inject.Inject;

import java.lang.reflect.Type;
import java.sql.SQLException;
import com.zrlog.model.LogVersion;
import com.zrlog.model.Log;
import com.zrlog.data.dto.ArticleBasicDTO;
import java.util.*;
import java.util.stream.Collectors;

public class ArticleVersionService {
    private static final Gson GSON = new Gson();
    private static final Type PATCH_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();
    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "title", "content", "markdown", "digest", "keywords", "alias", "thumbnail", "arrange_plugin",
            "typeId", "canComment", "recommended", "privacy", "rubbish", "editorType"
    );
    private final jakarta.inject.Provider<LogRepository> logProvider;
    private final jakarta.inject.Provider<LogVersionRepository> logVersionProvider;
    private final AccountPermissionService permissionService;
    @Inject
    public ArticleVersionService(
            AccountPermissionService permissionService,
            jakarta.inject.Provider<LogRepository> logProvider,
            jakarta.inject.Provider<LogVersionRepository> logVersionProvider) {
        this.logProvider = logProvider;
        this.logVersionProvider = logVersionProvider;

        this.permissionService = permissionService;
    }

    private static Map<String, Object> getSnapshotValueMap(LoadEditArticleResponse article) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", article.getTitle());
        map.put("content", article.getContent());
        map.put("markdown", article.getMarkdown());
        map.put("digest", article.getDigest());
        map.put("keywords", article.getKeywords());
        map.put("alias", article.getAlias());
        map.put("arrange_plugin", Objects.toString(article.getArrange_plugin(), ""));
        map.put("thumbnail", article.getThumbnail());
        map.put("typeId", article.getTypeId());
        map.put("canComment", article.isCanComment());
        map.put("recommended", article.isRecommended());
        map.put("privacy", article.isPrivacy());
        map.put("rubbish", article.isRubbish());
        map.put("editorType", article.getEditorType());
        return map;
    }

    private static Object getSnapshotValue(LoadEditArticleResponse article, String field) {
        return getSnapshotValueMap(article).get(field);
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

    /** Immutable document values used only to construct the persisted reverse patch. */
    static final class Revision {
        private final Long id;
        private final Long version;
        private final String title;
        private final Object[] values;

        private Revision(Log article) {
            id = article.getId();
            version = article.getVersion();
            title = article.getTitle();
            values = SNAPSHOT_FIELDS.stream().map(field -> getLogFieldValue(article, field)).toArray();
        }
    }

    Revision beforeUpdate(Log article) {
        return new Revision(article);
    }

    void recordReversePatch(Revision before, Log article, Integer userId) throws SQLException {
        if (!Objects.equals(before.id, article.getId()) || before.version == null
                || article.getVersion() != Math.addExact(before.version, 1)) {
            throw new IllegalArgumentException("History requires the confirmed next article version");
        }
        Map<String, Object> patch = buildReversePatch(before, article);
        if (patch.isEmpty()) return;
        logVersionProvider.get().savePatch(Math.toIntExact(article.getId()), Math.toIntExact(article.getVersion()),
                GSON.toJson(patch), userId, Objects.toString(before.title, ""), new Date(), Math.toIntExact(before.version));
    }

    public void recordKeywordPatch(int logId, int version, String title, String before, String after, int userId) throws SQLException {
        Map<String, Object> patch = buildFieldReversePatch(before, after);
        if (patch == null) return;
        logVersionProvider.get().savePatch(logId, version, GSON.toJson(Map.of("keywords", patch)), userId,
                Objects.toString(title, ""), new Date(), version - 1);
    }

    public List<ArticleVersionResponse> listVersions(AccountAccess actor, Integer logId) throws SQLException {
        permissionService.readArticle(actor, logId);
        List<ArticleVersionResponse> responses = new ArrayList<>();
        responses.add(loadCurrentVersion(logId));

        List<LogVersionRepository.Summary> rows = this.logVersionProvider.get().findVersionList(logId);
        for (LogVersionRepository.Summary row : rows) {
            ArticleVersionResponse item = new ArticleVersionResponse();
            item.setVersion(toInteger(row.fromVersion));
            item.setCreatedAt(ResultValueConvertUtils.parseDate(row.createdAt));
            item.setUserId(toInteger(row.userId));
            item.setTitle(Objects.toString(row.title, ""));
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

    private ArticleVersionResponse loadCurrentVersion(Integer logId) throws SQLException {
        ArticleBasicDTO row = logProvider.get().queryOne(
                "select version,title,last_update_date as lastUpdateDate from log where logId=?", ArticleBasicDTO.class, logId);
        if (row == null) throw new NotFindDbEntryException();
        ArticleVersionResponse currentVersion = new ArticleVersionResponse();
        currentVersion.setVersion(row.getVersion().intValue());
        currentVersion.setCreatedAt(ResultValueConvertUtils.parseDate(row.getLastUpdateDate()));
        currentVersion.setUserId(null);
        currentVersion.setTitle(Objects.toString(row.getTitle(), ""));
        currentVersion.setCurrent(true);
        return currentVersion;
    }

    LoadEditArticleResponse loadVersion(
            Integer logId, Integer targetVersion, LoadEditArticleResponse current)
            throws SQLException {
        if (logId == null || targetVersion == null) {
            throw new ArgsException("logId/targetVersion");
        }
        if (current.getVersion() == null || targetVersion > current.getVersion() || targetVersion < 0) {
            throw new NotFindDbEntryException();
        }
        if (Objects.equals(current.getVersion(), targetVersion)) {
            return current;
        }
        List<LogVersion> rows =
                this.logVersionProvider
                        .get()
                        .findReversePatchesGreaterThanVersion(logId, targetVersion);
        LoadEditArticleResponse snapshot = cloneArticle(current);
        for (LogVersion row : rows) {
            Integer version = toInteger(row.getArticleVersion());
            if (version == null || version > snapshot.getVersion()) {
                continue;
            }
            applyReversePatch(snapshot, Objects.toString(row.getPatchJson(), ""));
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

    public ArticleVersionCompareResponse compare(
            Integer fromVersion,
            Integer toVersion,
            LoadEditArticleResponse from,
            LoadEditArticleResponse to) {
        ArticleVersionCompareResponse response = new ArticleVersionCompareResponse();
        response.setFromVersion(fromVersion);
        response.setToVersion(toVersion);
        response.setFromArticle(from);
        response.setToArticle(to);
        response.setChangedFields(
                SNAPSHOT_FIELDS.stream()
                        .filter(
                                field ->
                                        !Objects.equals(
                                                getSnapshotValue(from, field),
                                                getSnapshotValue(to, field)))
                        .collect(Collectors.toList()));
        return response;
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
            case "arrange_plugin":
                // Historical null values must clear the field, not mean "preserve" on rollback.
                article.setArrange_plugin(Objects.toString(value, ""));
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
            case "recommended":
                article.setRecommended(toBoolean(value));
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

    private Map<String, Object> buildReversePatch(Revision before, Log article) {
        Map<String, Object> patch = new LinkedHashMap<>();
        for (int i = 0; i < SNAPSHOT_FIELDS.size(); i++) {
            String field = SNAPSHOT_FIELDS.get(i);
            Object oldValue = before.values[i];
            Object newValue = getLogFieldValue(article, field);
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

    private static Object getLogFieldValue(Log article, String field) {
        switch (field) {
            case "title": return article.getTitle();
            case "content": return article.getContent();
            case "markdown": return article.getMarkdown();
            case "digest": return article.getDigest();
            case "keywords": return article.getKeywords();
            case "alias": return article.getAlias();
            case "thumbnail": return article.getThumbnail();
            case "arrange_plugin": return Objects.toString(article.getArrangePlugin(), "");
            case "typeId": return article.getTypeId();
            case "canComment": return article.getCanComment();
            case "recommended": return article.getRecommended();
            case "privacy": return article.getPrivacy();
            case "rubbish": return article.getRubbish();
            case "editorType": return article.getEditorType();
            default: throw new IllegalArgumentException(field);
        }
    }
}
