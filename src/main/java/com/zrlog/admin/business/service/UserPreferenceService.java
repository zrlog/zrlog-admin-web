package com.zrlog.admin.business.service;

import com.google.gson.*;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.model.User;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

/** Personal configuration only. Never use this JSON for authorization or public resources. */
public class UserPreferenceService {
    private static final Gson GSON = new Gson();
    private static final Set<String> KEYS = Set.of("language", "appearance", "articlePageSize", "editor", "assistant");
    private static final Set<String> THEMES = Set.of("default", "antd", "geek", "shadcn", "cartoon", "illustration", "bootstrap", "desk", "glass");

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

    public UserPreferencesResponse updateBody(String body) throws SQLException {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) throw new ArgsException("preferences");
            return update(parsed.getAsJsonObject());
        } catch (JsonParseException e) { throw new ArgsException("preferences"); }
    }

    public UserPreferencesResponse update(JsonObject body) throws SQLException {
        UserPreferences preferences = validate(body);
        int userId = AccountPermissionService.current().getUserId();
        JsonObject values = GSON.toJsonTree(preferences).getAsJsonObject();
        mutate(userId, root -> {
            for (String key : KEYS) {
                root.remove(key);
                if (values.has(key)) root.add(key, values.get(key));
            }
        });
        return current();
    }

    private UserPreferences read(int userId) throws SQLException {
        JsonObject root = parseStored(raw(userId));
        JsonObject known = new JsonObject();
        for (String key : KEYS) if (root.has(key)) known.add(key, root.get(key));
        try { return validate(known); }
        catch (ArgsException e) { return new UserPreferences(); }
    }

    private String raw(int userId) throws SQLException {
        Object value = new User().queryFirstObj("select preferences from user where userId=?", userId);
        return value == null ? null : value.toString();
    }

    private JsonObject parseStored(String raw) {
        if (raw == null || raw.isBlank()) return new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (JsonParseException e) { return new JsonObject(); }
    }

    private void mutate(int userId, Consumer<JsonObject> change) throws SQLException {
        for (int attempt = 0; attempt < 8; attempt++) {
            String previous = raw(userId);
            JsonObject root = parseStored(previous);
            change.accept(root);
            String updated = GSON.toJson(root);
            if (updated.getBytes(StandardCharsets.UTF_8).length > 60000) throw new ArgsException("preferences");
            if (updated.equals(previous)) return;
            boolean saved = previous == null
                    ? new User().execute("update user set preferences=? where userId=? and preferences is null", updated, userId)
                    : new User().execute("update user set preferences=? where userId=? and preferences=?", updated, userId, previous);
            if (saved) return;
        }
        throw new SQLException("Concurrent account preference update; please retry");
    }

    public AdminDashboardConfigResponse dashboard(AdminTokenVO token) {
        if (token == null) return null; // Public static rendering uses the site default only.
        int userId = AccountPermissionService.account(token).getUserId();
        try {
            JsonElement value = parseStored(raw(userId)).get("dashboard");
            return value == null || !value.isJsonObject() ? null : GSON.fromJson(value, AdminDashboardConfigResponse.class);
        } catch (JsonParseException e) { return null; }
        catch (SQLException e) { throw new IllegalStateException("Unable to read dashboard preferences", e); }
    }

    public void saveDashboard(AdminTokenVO token, AdminDashboardConfigResponse config) {
        int userId = AccountPermissionService.account(token).getUserId();
        try { mutate(userId, root -> root.add("dashboard", GSON.toJsonTree(config))); }
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
        result.editor.autoSaveInterval = site.articleEditWebSiteInfo().getArticle_edit_auto_save_interval();
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
            only(editor, Set.of("autoSaveInterval"));
            number(editor, "autoSaveInterval", 2, 10, Set.of(2L, 5L, 10L));
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
