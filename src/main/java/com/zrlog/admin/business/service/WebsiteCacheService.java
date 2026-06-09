package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.response.WebsiteKvEntryResponse;
import com.zrlog.business.service.WebsiteKvService;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebsiteCacheService {

    public static final String KEY_PREFIX = "admin_cache:";
    private static final Logger LOGGER = LoggerUtil.getLogger(WebsiteCacheService.class);
    private static final Gson GSON = new Gson();
    private final WebsiteKvService kvService = new WebsiteKvService();

    public <T> T getJson(String key, Class<T> clazz) {
        String json = getString(key);
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return GSON.fromJson(json, clazz);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read website JSON cache failed, key=" + key, e);
            return null;
        }
    }

    public <T> T getJson(String key, Type type) {
        String json = getString(key);
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return GSON.fromJson(json, type);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read website JSON cache failed, key=" + key, e);
            return null;
        }
    }

    public boolean putJson(String key, Object value) {
        return putString(key, GSON.toJson(value));
    }

    public String getString(String key) {
        try {
            return kvService.getString(normalizeKey(key));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read website cache failed, key=" + key, e);
            return null;
        }
    }

    public boolean putString(String key, String value) {
        return kvService.putStringQuietly(normalizeKey(key), value);
    }

    public boolean remove(String key) {
        return kvService.removeQuietly(normalizeKey(key));
    }

    public List<WebsiteKvEntryResponse> listEntries() {
        List<WebsiteKvEntryResponse> entries = new ArrayList<>();
        for (Map<String, Object> row : kvService.listByPrefix(KEY_PREFIX)) {
            WebsiteKvEntryResponse entry = new WebsiteKvEntryResponse();
            entry.setKey(Objects.toString(row.get("name"), ""));
            entry.setValue(Objects.toString(row.get("value"), ""));
            entry.setSize(toLong(row.get("size")));
            entries.add(entry);
        }
        return entries;
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(Objects.toString(value, "0"));
    }

    private String normalizeKey(String key) {
        return key.startsWith(KEY_PREFIX) ? key : KEY_PREFIX + key;
    }
}
