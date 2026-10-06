package com.zrlog.admin.business.service;

import com.google.gson.*;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.service.UserPreferenceStore;

import java.sql.SQLException;
import java.util.*;

/** Personal configuration only. Never use this JSON for authorization or public resources. */
public class UserPreferenceService {
    private static final Gson GSON = new Gson();
    private static final Set<String> KEYS = Set.of("language", "appearance", "articlePageSize", "editor", "assistant", "session", "articleList");
    private static final Set<String> THEMES = Set.of("default", "antd", "geek", "shadcn", "cartoon", "illustration", "bootstrap", "desk", "glass");
    private final UserPreferenceStore store = new UserPreferenceStore();
    private static final Map<String, Set<String>> SECTIONS = Map.of(
            "appearance", Set.of("language", "appearance"),
            "writing", Set.of("articlePageSize", "editor", "articleList"),
            "assistant", Set.of("assistant"), "session", Set.of("session"));

    public UserPreferencesResponse current() throws SQLException {
        UserPreferencesResponse response = new UserPreferencesResponse();
        response.overrides = read(AccountPermissionService.current().getUserId());
        response.defaults = defaults();
        response.effective = merge(response.defaults, response.overrides);
        return response;
    }

    public UserPreferences effective() {
        UserPreferences defaults = defaults();
        if (AdminTokenThreadLocal.getUser() == null) return defaults;
        try {
            return merge(defaults, read(AccountPermissionService.current().getUserId()));
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read account preferences", e);
        }
    }

    /** Resolve preferences for an already authenticated external account, without a browser session. */
    public UserPreferences effective(int userId) throws SQLException {
        return merge(defaults(), read(userId));
    }

    public UserPreferencesResponse updateBody(String body) throws SQLException { return updateBody(body, null); }

    public UserPreferencesResponse updateBody(String body, String section) throws SQLException {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) throw new ArgsException("preferences");
            return update(parsed.getAsJsonObject(), section);
        } catch (JsonParseException e) { throw new ArgsException("preferences"); }
    }

    public UserPreferencesResponse update(JsonObject body) throws SQLException { return update(body, null); }

    public UserPreferencesResponse update(JsonObject body, String section) throws SQLException {
        Set<String> keys = section == null ? KEYS : SECTIONS.get(section);
        if (keys == null) throw new ArgsException("section");
        UserPreferences preferences = validate(body);
        int userId = AccountPermissionService.current().getUserId();
        JsonObject values = GSON.toJsonTree(preferences).getAsJsonObject();
        store.mutate(userId, root -> {
            // Old clients know only autoSaveInterval; preserve newly introduced editor fields.
            JsonObject previousEditor = root.has("editor") && root.get("editor").isJsonObject()
                    ? root.getAsJsonObject("editor").deepCopy() : new JsonObject();
            for (String key : keys) {
                if (section == null && ("session".equals(key) || "articleList".equals(key)) && !body.has(key)) continue;
                root.remove(key);
                if (values.has(key)) root.add(key, values.get(key).deepCopy());
            }
            if (section == null) {
                JsonObject supplied = object(body, "editor");
                for (String key : List.of("linkPreviewEnabled", "publishCheckEnabled", "autoDigestLength", "coverAspectRatio")) {
                    if (previousEditor.has(key) && (supplied == null || !supplied.has(key))) {
                        if (!root.has("editor")) root.add("editor", new JsonObject());
                        root.getAsJsonObject("editor").add(key, previousEditor.get(key));
                    }
                }
            }
        });
        return current();
    }

    private UserPreferences read(int userId) throws SQLException {
        JsonObject root = store.read(userId);
        JsonObject known = new JsonObject();
        for (String key : KEYS) {
            if (!root.has(key)) continue;
            JsonObject section = new JsonObject();
            section.add(key, root.get(key));
            try { validate(section); known.add(key, root.get(key)); }
            catch (ArgsException ignored) { /* A corrupt partition inherits its defaults. */ }
        }
        return validate(known);
    }

    public AdminDashboardConfigResponse dashboard(AdminTokenVO token) {
        if (token == null) return null; // Public static rendering uses the site default only.
        int userId = AccountPermissionService.account(token).getUserId();
        try {
            JsonElement value = store.read(userId).get("dashboard");
            return value == null || !value.isJsonObject() ? null : GSON.fromJson(value, AdminDashboardConfigResponse.class);
        } catch (JsonParseException e) { return null; }
        catch (SQLException e) { throw new IllegalStateException("Unable to read dashboard preferences", e); }
    }

    public void saveDashboard(AdminTokenVO token, AdminDashboardConfigResponse config) {
        int userId = AccountPermissionService.account(token).getUserId();
        try { store.mutate(userId, root -> root.add("dashboard", GSON.toJsonTree(config))); }
        catch (SQLException e) { throw new IllegalStateException("Unable to save dashboard preferences", e); }
    }

    public void apply(AdminResourceInfoResponse resource, UserPreferences preferences) {
        resource.setLang(preferences.language);
        resource.setAdmin_theme(preferences.appearance.theme);
        resource.setAdmin_darkMode(preferences.appearance.darkMode);
        resource.setAdmin_compactMode(preferences.appearance.compactMode);
        resource.setAdmin_color_primary(preferences.appearance.colorPrimary);
    }

    UserPreferences defaults() {
        WebSiteService site = new WebSiteService();
        AdminWebSiteInfo admin = site.adminWebSiteInfo();
        UserPreferences result = new UserPreferences();
        result.language = "en_US".equals(admin.getLanguage()) ? "en_US" : "zh_CN";
        result.appearance = new UserPreferences.Appearance();
        result.appearance.theme = admin.getAdmin_theme() == null ? "default" : admin.getAdmin_theme();
        result.appearance.darkMode = Boolean.TRUE.equals(admin.getAdmin_darkMode());
        result.appearance.compactMode = Boolean.TRUE.equals(admin.getAdmin_compactMode());
        result.appearance.colorPrimary = admin.getAdmin_color_primary();
        result.articlePageSize = admin.getAdmin_article_page_size().intValue();
        result.editor = new UserPreferences.Editor();
        ArticleEditWebSiteInfo editor = site.articleEditWebSiteInfo();
        result.editor.autoSaveInterval = editor.getArticle_edit_auto_save_interval();
        result.editor.linkPreviewEnabled = editor.getArticle_editor_link_preview_enabled();
        result.editor.publishCheckEnabled = editor.getArticle_publish_check_enabled();
        result.editor.autoDigestLength = editor.getArticle_auto_digest_length();
        result.editor.coverAspectRatio = editor.getArticle_cover_aspect_ratio();
        result.session = new UserPreferences.Session();
        result.session.timeoutMinutes = admin.getSession_timeout();
        result.articleList = new UserPreferences.ArticleList();
        result.articleList.sort = "id,DESC";
        result.articleList.status = "";
        result.articleList.columns = List.of("thumbnail", "typeName", "click", "canComment", "commentSize", "releaseTime", "lastUpdateDate");
        result.assistant = assistantDefaults();
        return result;
    }

    private static UserPreferences.Assistant assistantDefaults() {
        UserPreferences.Assistant result = new UserPreferences.Assistant();
        result.knowledgeScope = "own_public";
        return result;
    }

    public UserPreferences.Assistant assistant(AdminTokenVO token) {
        int userId = AccountPermissionService.account(token).getUserId();
        UserPreferences defaults = new UserPreferences();
        defaults.assistant = assistantDefaults();
        try { return merge(defaults, read(userId)).assistant; }
        catch (SQLException e) { throw new IllegalStateException("Unable to read assistant settings", e); }
    }

    static UserPreferences merge(UserPreferences defaults, UserPreferences overrides) {
        JsonObject target = GSON.toJsonTree(defaults).getAsJsonObject();
        overlay(target, GSON.toJsonTree(overrides).getAsJsonObject());
        return GSON.fromJson(target, UserPreferences.class);
    }

    private static void overlay(JsonObject target, JsonObject source) {
        source.entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject() && target.has(entry.getKey()) && target.get(entry.getKey()).isJsonObject()) {
                overlay(target.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
            } else target.add(entry.getKey(), entry.getValue());
        });
    }

    static UserPreferences validate(JsonObject body) {
        if (body == null) throw new ArgsException("preferences");
        only(body, KEYS);
        string(body, "language", Set.of("zh_CN", "en_US"));
        number(body, "articlePageSize", 1, 100, null);
        JsonObject appearance = object(body, "appearance");
        if (appearance != null) {
            only(appearance, Set.of("theme", "darkMode", "compactMode", "colorPrimary"));
            string(appearance, "theme", THEMES);
            bool(appearance, "darkMode"); bool(appearance, "compactMode");
            if (present(appearance, "colorPrimary")) {
                JsonElement value = appearance.get("colorPrimary");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                        || !value.getAsString().matches("#[0-9a-fA-F]{6}")) throw new ArgsException("appearance.colorPrimary");
            }
        }
        JsonObject editor = object(body, "editor");
        if (editor != null) {
            only(editor, Set.of("autoSaveInterval", "linkPreviewEnabled", "publishCheckEnabled", "autoDigestLength", "coverAspectRatio"));
            bool(editor, "linkPreviewEnabled");
            bool(editor, "publishCheckEnabled");
            number(editor, "autoDigestLength", -1, 99999, null);
            string(editor, "coverAspectRatio", Set.of("16:9", "4:3", "3:2", "1:1", "21:9"));
            number(editor, "autoSaveInterval", 2, 10, Set.of(2L, 5L, 10L));
        }
        JsonObject articleList = object(body, "articleList");
        if (articleList != null) {
            only(articleList, Set.of("sort", "status", "columns"));
            string(articleList, "status", Set.of("", "draft", "private", "published"));
            Set<String> sorts = new HashSet<>();
            for (String field : List.of("id", "click", "commentSize", "releaseTime", "lastUpdateDate")) {
                sorts.add(field + ",ASC"); sorts.add(field + ",DESC");
            }
            string(articleList, "sort", sorts);
            if (present(articleList, "columns")) {
                JsonElement columns = articleList.get("columns");
                Set<String> allowed = Set.of("thumbnail", "typeName", "click", "canComment", "commentSize", "releaseTime", "lastUpdateDate");
                if (!columns.isJsonArray() || columns.getAsJsonArray().size() > allowed.size()) throw new ArgsException("columns");
                Set<String> selected = new HashSet<>();
                for (JsonElement value : columns.getAsJsonArray()) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                            || !allowed.contains(value.getAsString()) || !selected.add(value.getAsString())) throw new ArgsException("columns");
                }
            }
        }
        JsonObject session = object(body, "session");
        if (session != null) {
            only(session, Set.of("timeoutMinutes"));
            number(session, "timeoutMinutes", UserPreferenceStore.MIN_SESSION_MINUTES, UserPreferenceStore.MAX_SESSION_MINUTES, null);
        }
        JsonObject assistant = object(body, "assistant");
        if (assistant != null) {
            only(assistant, Set.of("knowledgeScope"));
            string(assistant, "knowledgeScope", Set.of("off", "own_public", "own_all", "accessible_public", "accessible_all"));
        }
        return GSON.fromJson(body, UserPreferences.class);
    }

    private static boolean present(JsonObject body, String key) { return body.has(key) && !body.get(key).isJsonNull(); }
    private static void only(JsonObject body, Set<String> keys) {
        for (String key : body.keySet()) if (!keys.contains(key)) throw new ArgsException("preferences");
    }
    private static JsonObject object(JsonObject body, String key) {
        if (!present(body, key)) return null;
        if (!body.get(key).isJsonObject()) throw new ArgsException(key);
        return body.getAsJsonObject(key);
    }
    private static void string(JsonObject body, String key, Set<String> values) {
        if (!present(body, key)) return;
        JsonElement value = body.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !values.contains(value.getAsString())) throw new ArgsException(key);
    }
    private static void bool(JsonObject body, String key) {
        if (present(body, key) && (!body.get(key).isJsonPrimitive() || !body.getAsJsonPrimitive(key).isBoolean())) throw new ArgsException(key);
    }
    private static void number(JsonObject body, String key, long min, long max, Set<Long> values) {
        if (!present(body, key)) return;
        JsonElement value = body.get(key);
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
            long number = value.getAsBigDecimal().longValueExact();
            if (number < min || number > max || (values != null && !values.contains(number))) throw new ArithmeticException();
        } catch (ArithmeticException | NumberFormatException e) { throw new ArgsException(key); }
    }
}
