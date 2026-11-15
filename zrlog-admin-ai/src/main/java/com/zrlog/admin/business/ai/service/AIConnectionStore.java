package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.request.AISettingsRequest;
import com.zrlog.admin.business.rest.response.AIConnectionResponse;
import com.zrlog.admin.business.rest.response.AIWebSiteInfoResponse;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.repository.WebSiteRepository;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/** Saved connections supplement the existing active website fields; reads never migrate data. */
public final class AIConnectionStore {
    static final String CONNECTIONS = "ai_connections";
    static final String TEXT_ID = "ai_connection_id";
    static final String IMAGE_ID = "ai_image_connection_id";
    private static final Gson JSON = new Gson();
    private static final int MAX_CONNECTIONS = 64;
    private static final List<String> KEYS = new ArrayList<>(AIConfigService.AI_WEBSITE_INFO_KEYS);

    static {
        KEYS.addAll(List.of(CONNECTIONS, TEXT_ID, IMAGE_ID));
    }

    /** Stored only on the server. Never serialize this type into an HTTP response. */
    public static class Connection {
        public String ai_connection_id;
        public String ai_connection_name;
        public String ai_kind;
        public AIProviderType ai_provider;
        public String ai_model;
        public String ai_base_url;
        public String ai_api_key;

        AIConnectionResponse response() {
            AIConnectionResponse value = BeanUtil.convert(this, AIConnectionResponse.class);
            value.hasAiApiKey = StringUtils.isNotEmpty(ai_api_key);
            return value;
        }
    }

    private final WebSiteRepository websites;
    final AIWebSiteInfo current;
    private final List<Connection> connections;
    private final Connection text;
    private final Connection image;

    AIConnectionStore(WebSiteRepository websites) {
        this.websites = websites;
        Map<String, Object> values = websites.getWebSiteByNameIn(KEYS);
        current =
                AIConfigService.normalizeAIWebSiteInfo(
                        ResultBeanUtils.convert(values, AIWebSiteInfo.class));
        String saved = Objects.toString(values.get(CONNECTIONS), "");
        connections = readConnections(saved);
        text = overlayCurrent(current, false, Objects.toString(values.get(TEXT_ID), ""));
        image = overlayCurrent(current, true, Objects.toString(values.get(IMAGE_ID), ""));
    }

    private static List<Connection> readConnections(String saved) {
        if (saved.isBlank()) return new ArrayList<>();
        try {
            Connection[] entries = JSON.fromJson(saved, Connection[].class);
            if (entries == null || entries.length > MAX_CONNECTIONS)
                throw new IllegalArgumentException();
            Set<String> ids = new HashSet<>();
            for (Connection entry : entries) {
                if (entry == null
                        || entry.ai_provider == null
                        || !Set.of("text", "image").contains(entry.ai_kind)
                        || entry.ai_connection_id == null
                        || !ids.add(entry.ai_connection_id)) {
                    throw new IllegalArgumentException();
                }
            }
            return new ArrayList<>(Arrays.asList(entries));
        } catch (RuntimeException invalid) {
            // Never replace unreadable saved credentials with an empty collection.
            throw new ArgsException(CONNECTIONS);
        }
    }

    private Connection overlayCurrent(AIWebSiteInfo info, boolean image, String id) {
        Connection active = fromSettings(info, image);
        if (active.ai_provider == null) return null;
        Connection saved = find(id, image);
        if (saved != null
                && sameEndpoint(active, saved)
                && Objects.equals(active.ai_api_key, saved.ai_api_key)) {
            active.ai_connection_id = saved.ai_connection_id;
            active.ai_connection_name = saved.ai_connection_name;
        } else {
            active.ai_connection_id = image ? "current-image" : "current-text";
            active.ai_connection_name = active.ai_provider.name().toLowerCase(Locale.ROOT);
        }
        upsert(active);
        return active;
    }

    void fillResponse(AIWebSiteInfoResponse response) {
        response.ai_connections =
                connections.stream().map(Connection::response).collect(Collectors.toList());
        response.ai_connection_id = text == null ? null : text.ai_connection_id;
        response.ai_image_connection_id = image == null ? null : image.ai_connection_id;
    }

    AIWebSiteInfo retainedSettings(AISettingsRequest request) {
        AIWebSiteInfo retained = new AIWebSiteInfo();
        apply(retained, selected(request.ai_connection_id, false), false);
        apply(retained, selected(request.ai_image_connection_id, true), true);
        return retained;
    }

    private Connection selected(String id, boolean image) {
        if (id == null) return image ? this.image : text; // Legacy client.
        if (id.isEmpty()) return null; // Explicitly adding another connection/account.
        Connection selected = find(id, image);
        if (selected == null) throw new ArgsException(image ? IMAGE_ID : TEXT_ID);
        return selected;
    }

    void save(AISettingsRequest request) throws SQLException {
        Connection nextText =
                updated(request, false, request.ai_connection_id, request.ai_connection_name);
        Connection nextImage =
                updated(
                        request,
                        true,
                        request.ai_image_connection_id,
                        request.ai_image_connection_name);
        upsert(nextText);
        if (nextImage != null) upsert(nextImage);
        for (Connection connection : connections) {
            if (connection.ai_connection_id.startsWith("current-")) {
                connection.ai_connection_id = UUID.randomUUID().toString();
            }
        }
        String serialized = asciiJson(JSON.toJson(connections));
        if (connections.size() > MAX_CONNECTIONS || serialized.length() > 60000) {
            throw new ArgsException(CONNECTIONS);
        }
        // Fixed setting keys form a dynamic SQL value boundary, not an HTTP data contract.
        Map<String, Object> values =
                new LinkedHashMap<>(
                        BeanUtil.convert(
                                BeanUtil.convert(request, AIWebSiteInfo.class), Map.class));
        values.keySet().retainAll(AIConfigService.AI_WEBSITE_INFO_KEYS);
        for (String key : AIConfigService.AI_WEBSITE_INFO_KEYS) values.putIfAbsent(key, null);
        values.put(CONNECTIONS, serialized);
        values.put(TEXT_ID, nextText.ai_connection_id);
        values.put(IMAGE_ID, nextImage == null ? "" : nextImage.ai_connection_id);
        saveAtomically(values);
    }

    private Connection updated(AISettingsRequest request, boolean image, String id, String name) {
        Connection next = fromSettings(request, image);
        if (next.ai_provider == null) return null;
        Connection selected = selected(id, image);
        boolean reuse = selected != null && sameEndpoint(next, selected);
        next.ai_connection_id = reuse ? selected.ai_connection_id : UUID.randomUUID().toString();
        next.ai_connection_name =
                name == null || name.isBlank()
                        ? reuse
                                ? selected.ai_connection_name
                                : next.ai_provider.name().toLowerCase(Locale.ROOT)
                        : name.trim();
        if (next.ai_connection_name.length() > 80) throw new ArgsException("ai_connection_name");
        return next;
    }

    private void saveAtomically(Map<String, Object> values) throws SQLException {
        // Add missing dictionary slots with null values only. One UPDATE then publishes the
        // complete configuration on JDBC and WebAPI alike, without multi-request transactions.
        for (String key : values.keySet()) {
            if (websites.findByName(key) == null) websites.updateByKV(key, null);
        }
        StringBuilder sql = new StringBuilder("UPDATE website SET value=CASE name");
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> value : values.entrySet()) {
            sql.append(" WHEN ? THEN ?");
            args.add(value.getKey());
            Object entry = value.getValue();
            args.add(
                    entry instanceof Number
                            ? Long.toString(((Number) entry).longValue())
                            : entry == null ? null : entry.toString());
        }
        sql.append(" ELSE value END, updatedAt=?, version=version+1 WHERE name IN (");
        args.add(new Timestamp(System.currentTimeMillis()));
        sql.append(String.join(",", Collections.nCopies(values.size(), "?"))).append(")");
        args.addAll(values.keySet());
        if (!websites.execute(sql.toString(), args.toArray()))
            throw new SQLException("AI settings were not saved");
    }

    private static String asciiJson(String json) {
        StringBuilder result = new StringBuilder(json.length());
        for (char c : json.toCharArray()) {
            if (c > 127) result.append(String.format("\\u%04x", (int) c));
            else result.append(c);
        }
        return result.toString();
    }

    private Connection find(String id, boolean image) {
        return connections.stream()
                .filter(
                        c ->
                                Objects.equals(c.ai_connection_id, id)
                                        && Objects.equals(c.ai_kind, image ? "image" : "text"))
                .findFirst()
                .orElse(null);
    }

    private void upsert(Connection connection) {
        for (int i = 0; i < connections.size(); i++) {
            if (Objects.equals(connections.get(i).ai_connection_id, connection.ai_connection_id)) {
                connections.set(i, connection);
                return;
            }
        }
        connections.add(connection);
    }

    private static boolean sameEndpoint(Connection a, Connection b) {
        return a.ai_provider == b.ai_provider
                && Objects.equals(
                        AIWebSiteInfo.normalizeBaseUrl(a.ai_base_url),
                        AIWebSiteInfo.normalizeBaseUrl(b.ai_base_url));
    }

    private static Connection fromSettings(AIWebSiteInfo info, boolean image) {
        Connection value = new Connection();
        value.ai_kind = image ? "image" : "text";
        value.ai_provider = image ? info.getAi_image_provider() : info.getAi_provider();
        value.ai_model = image ? info.getAi_image_model() : info.getAi_model();
        value.ai_base_url = image ? info.getAi_image_base_url() : info.getAi_base_url();
        value.ai_api_key = image ? info.getAi_image_api_key() : info.getAi_api_key();
        return value;
    }

    private static void apply(AIWebSiteInfo info, Connection connection, boolean image) {
        if (connection == null) return;
        if (image) {
            info.setAi_image_provider(connection.ai_provider);
            info.setAi_image_model(connection.ai_model);
            info.setAi_image_base_url(connection.ai_base_url);
            info.setAi_image_api_key(connection.ai_api_key);
        } else {
            info.setAi_provider(connection.ai_provider);
            info.setAi_model(connection.ai_model);
            info.setAi_base_url(connection.ai_base_url);
            info.setAi_api_key(connection.ai_api_key);
        }
    }
}
