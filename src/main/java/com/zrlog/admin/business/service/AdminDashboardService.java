package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.request.AdminDashboardCardRequest;
import com.zrlog.admin.business.rest.request.AdminDashboardConfigRequest;
import com.zrlog.admin.business.rest.response.AdminDashboardCardConfigResponse;
import com.zrlog.admin.business.rest.response.AdminDashboardCardResponse;
import com.zrlog.admin.business.rest.response.AdminDashboardConfigResponse;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ThreadUtils;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class AdminDashboardService {

    public static final String DASHBOARD_CONFIG_KEY = "admin_dashboard_config";
    private static final List<String> DEFAULT_CARD_IDS = Arrays.asList(
            "welcome", "localDraft", "quickAction", "statistics", "activity", "auditTrail", "dataInsights"
    );
    private static final String PANEL_TYPE_SURFACE = "surface";
    private static final String PANEL_TYPE_VIEW = "view";
    private static final String ITEM_KIND_CARD = "card";
    private static final String ITEM_KIND_PLUGIN = "plugin";
    private static final int DEFAULT_AUTO_REFRESH_INTERVAL_SECONDS = 60;
    private static final int MIN_AUTO_REFRESH_INTERVAL_SECONDS = 10;
    private static final Pattern PLUGIN_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Gson GSON = new Gson();
    private static final Type CONFIG_TYPE = new TypeToken<AdminDashboardConfigResponse>() {
    }.getType();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();
    private static final Logger LOGGER = LoggerUtil.getLogger(AdminDashboardService.class);

    private final WebsiteKvService kvService = new WebsiteKvService();

    public AdminDashboardConfigResponse getConfig(HttpRequest request, AdminTokenVO adminTokenVO) {
        return getConfig(request, adminTokenVO, false);
    }

    public AdminDashboardConfigResponse getConfig(HttpRequest request, AdminTokenVO adminTokenVO, boolean preloadSurfaces) {
        AdminDashboardConfigResponse savedConfig = readSavedConfig();
        List<AdminDashboardCardConfigResponse> cards = mergeCards(extractCardItems(savedConfig.getCards()));
        List<AdminDashboardCardResponse> pluginPanels = mergePluginPanels(
                extractPluginItems(savedConfig.getCards()), loadStandardPluginPanels(request, adminTokenVO));
        if (preloadSurfaces) {
            preloadSurfaceData(pluginPanels, request, adminTokenVO);
        }
        AdminDashboardConfigResponse config = new AdminDashboardConfigResponse();
        config.setCards(toItems(cards, pluginPanels));
        fillRefreshConfig(config, savedConfig);
        return config;
    }

    public AdminDashboardConfigResponse saveConfig(AdminDashboardConfigRequest request, HttpRequest httpRequest,
                                                   AdminTokenVO adminTokenVO) {
        List<AdminDashboardCardResponse> requestCards = toCardResponses(request.getCards());
        List<AdminDashboardCardConfigResponse> cards = mergeCards(extractCardItems(requestCards));
        List<AdminDashboardCardResponse> pluginPanels = mergePluginPanels(
                extractPluginItems(requestCards), loadStandardPluginPanels(httpRequest, adminTokenVO));
        List<AdminDashboardCardResponse> dashboardCards = toItems(cards, pluginPanels);
        clearRuntimeData(dashboardCards);
        AdminDashboardConfigResponse config = new AdminDashboardConfigResponse();
        config.setCards(dashboardCards);
        fillRefreshConfig(config, request);
        kvService.putStringQuietly(DASHBOARD_CONFIG_KEY, GSON.toJson(config));
        return getConfig(httpRequest, adminTokenVO, true);
    }

    private List<AdminDashboardCardResponse> toCardResponses(List<AdminDashboardCardRequest> requestCards) {
        List<AdminDashboardCardResponse> cards = new ArrayList<>();
        if (requestCards == null) {
            return cards;
        }
        for (AdminDashboardCardRequest requestCard : requestCards) {
            AdminDashboardCardResponse card = new AdminDashboardCardResponse();
            card.setKind(requestCard.getKind());
            card.setId(requestCard.getId());
            card.setEnabled(requestCard.getEnabled());
            card.setSort(requestCard.getSort());
            card.setType(requestCard.getType());
            card.setPluginName(requestCard.getPluginName());
            card.setSurfaceUrl(requestCard.getSurfaceUrl());
            card.setActionUrl(requestCard.getActionUrl());
            card.setViewUrl(requestCard.getViewUrl());
            card.setMaxItems(requestCard.getMaxItems());
            card.setHeight(requestCard.getHeight());
            card.setOrder(requestCard.getOrder());
            cards.add(card);
        }
        return cards;
    }

    private void fillRefreshConfig(AdminDashboardConfigResponse target, AdminDashboardConfigResponse source) {
        target.setAutoRefreshEnabled(Objects.equals(source.getAutoRefreshEnabled(), true));
        Integer interval = source.getAutoRefreshIntervalSeconds();
        target.setAutoRefreshIntervalSeconds(interval == null || interval < MIN_AUTO_REFRESH_INTERVAL_SECONDS
                ? DEFAULT_AUTO_REFRESH_INTERVAL_SECONDS
                : interval);
    }

    private void fillRefreshConfig(AdminDashboardConfigResponse target, AdminDashboardConfigRequest source) {
        target.setAutoRefreshEnabled(Objects.equals(source.getAutoRefreshEnabled(), true));
        Integer interval = source.getAutoRefreshIntervalSeconds();
        target.setAutoRefreshIntervalSeconds(interval == null || interval < MIN_AUTO_REFRESH_INTERVAL_SECONDS
                ? DEFAULT_AUTO_REFRESH_INTERVAL_SECONDS
                : interval);
    }

    private AdminDashboardConfigResponse readSavedConfig() {
        String json = kvService.getString(DASHBOARD_CONFIG_KEY);
        if (StringUtils.isEmpty(json)) {
            return new AdminDashboardConfigResponse();
        }
        try {
            AdminDashboardConfigResponse config = GSON.fromJson(json, CONFIG_TYPE);
            return Objects.requireNonNullElseGet(config, AdminDashboardConfigResponse::new);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read admin dashboard config failed", e);
            return new AdminDashboardConfigResponse();
        }
    }

    private List<AdminDashboardCardConfigResponse> mergeCards(List<AdminDashboardCardConfigResponse> savedCards) {
        Map<String, AdminDashboardCardConfigResponse> savedMap = new HashMap<>();
        if (savedCards != null) {
            for (AdminDashboardCardConfigResponse card : savedCards) {
                if (StringUtils.isNotEmpty(card.getId())) {
                    savedMap.put(card.getId(), card);
                }
            }
        }
        List<AdminDashboardCardConfigResponse> cards = new ArrayList<>();
        for (int i = 0; i < DEFAULT_CARD_IDS.size(); i++) {
            String id = DEFAULT_CARD_IDS.get(i);
            AdminDashboardCardConfigResponse savedCard = savedMap.get(id);
            Integer sort = savedCard == null || savedCard.getSort() == null ? i * 10 : savedCard.getSort();
            boolean enabled = "welcome".equals(id) || savedCard == null || !Objects.equals(savedCard.getEnabled(), false);
            AdminDashboardCardConfigResponse card = new AdminDashboardCardConfigResponse(id, enabled, sort);
            card.setTitle(id);
            card.setData(null);
            cards.add(card);
        }
        cards.sort(Comparator.comparingInt(card -> Objects.requireNonNullElse(card.getSort(), 0)));
        return cards;
    }

    private List<AdminDashboardCardResponse> mergePluginPanels(List<AdminDashboardCardResponse> savedPanels,
                                                               List<AdminDashboardCardResponse> standardPanels) {
        Map<String, AdminDashboardCardResponse> panels = new LinkedHashMap<>();
        for (AdminDashboardCardResponse panel : standardPanels) {
            panels.put(panel.getId(), panel);
        }
        if (savedPanels != null) {
            for (AdminDashboardCardResponse panel : savedPanels) {
                AdminDashboardCardResponse normalizedPanel = normalizePanel(panel);
                if (normalizedPanel == null) {
                    continue;
                }
                AdminDashboardCardResponse standardPanel = panels.get(normalizedPanel.getId());
                if (standardPanel != null) {
                    fillMissingPanelFields(normalizedPanel, standardPanel);
                }
                panels.put(normalizedPanel.getId(), normalizedPanel);
            }
        }
        List<AdminDashboardCardResponse> panelList = new ArrayList<>(panels.values());
        panelList.sort(Comparator.comparingInt(panel -> Objects.requireNonNullElse(panel.getSort(),
                Objects.requireNonNullElse(panel.getOrder(), 0))));
        return panelList;
    }

    private void clearRuntimeData(List<AdminDashboardCardResponse> items) {
        if (items != null) {
            for (AdminDashboardCardResponse item : items) {
                item.setData(null);
                item.setError(null);
                item.setSurfaceLoaded(null);
            }
        }
    }

    private List<AdminDashboardCardResponse> toItems(List<AdminDashboardCardConfigResponse> cards,
                                                     List<AdminDashboardCardResponse> panels) {
        List<AdminDashboardCardResponse> items = new ArrayList<>();
        for (AdminDashboardCardConfigResponse card : cards) {
            AdminDashboardCardResponse item = new AdminDashboardCardResponse();
            item.setKind(ITEM_KIND_CARD);
            item.setId(card.getId());
            item.setEnabled(card.getEnabled());
            item.setSort(card.getSort());
            item.setTitle(card.getTitle());
            item.setData(card.getData());
            items.add(item);
        }
        for (AdminDashboardCardResponse panel : panels) {
            panel.setKind(ITEM_KIND_PLUGIN);
            items.add(panel);
        }
        items.sort(Comparator.comparingInt(item -> Objects.requireNonNullElse(item.getSort(),
                Objects.requireNonNullElse(item.getOrder(), 0))));
        return items;
    }

    private List<AdminDashboardCardConfigResponse> extractCardItems(List<AdminDashboardCardResponse> items) {
        List<AdminDashboardCardConfigResponse> cards = new ArrayList<>();
        if (items == null) {
            return cards;
        }
        for (AdminDashboardCardResponse item : items) {
            if (!Objects.equals(ITEM_KIND_CARD, item.getKind())) {
                continue;
            }
            AdminDashboardCardConfigResponse card = new AdminDashboardCardConfigResponse();
            card.setId(item.getId());
            card.setEnabled(item.getEnabled());
            card.setSort(item.getSort());
            card.setTitle(item.getTitle());
            card.setData(item.getData());
            cards.add(card);
        }
        return cards;
    }

    private List<AdminDashboardCardResponse> extractPluginItems(List<AdminDashboardCardResponse> items) {
        List<AdminDashboardCardResponse> panels = new ArrayList<>();
        if (items == null) {
            return panels;
        }
        for (AdminDashboardCardResponse item : items) {
            if (!Objects.equals(ITEM_KIND_PLUGIN, item.getKind())) {
                continue;
            }
            AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
            panel.setKind(ITEM_KIND_PLUGIN);
            panel.setId(item.getId());
            panel.setEnabled(item.getEnabled());
            panel.setSort(item.getSort());
            panel.setTitle(item.getTitle());
            panel.setData(item.getData());
            panel.setError(item.getError());
            panel.setSurfaceLoaded(item.getSurfaceLoaded());
            panel.setType(item.getType());
            panel.setPluginName(item.getPluginName());
            panel.setSurfaceUrl(item.getSurfaceUrl());
            panel.setActionUrl(item.getActionUrl());
            panel.setViewUrl(item.getViewUrl());
            panel.setMaxItems(item.getMaxItems());
            panel.setHeight(item.getHeight());
            panel.setOrder(item.getOrder());
            panels.add(panel);
        }
        return panels;
    }

    private void preloadSurfaceData(List<AdminDashboardCardResponse> panels, HttpRequest request,
                                    AdminTokenVO adminTokenVO) {
        if (panels == null) {
            return;
        }
        List<AdminDashboardCardResponse> loadablePanels = new ArrayList<>();
        for (AdminDashboardCardResponse panel : panels) {
            if (isLoadableSurfacePanel(panel)) {
                loadablePanels.add(panel);
            }
        }
        if (loadablePanels.isEmpty()) {
            return;
        }
        ExecutorService executor = ThreadUtils.newFixedThreadPool(Math.min(loadablePanels.size(), 8));
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (AdminDashboardCardResponse panel : loadablePanels) {
                futures.add(CompletableFuture.runAsync(() -> preloadSurfacePanel(panel, request, adminTokenVO), executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }

    private boolean isLoadableSurfacePanel(AdminDashboardCardResponse panel) {
        return panel != null && PANEL_TYPE_SURFACE.equals(panel.getType()) && !Objects.equals(panel.getEnabled(), false)
                && StringUtils.isNotEmpty(panel.getSurfaceUrl());
    }

    private void preloadSurfacePanel(AdminDashboardCardResponse panel, HttpRequest request, AdminTokenVO adminTokenVO) {
        SurfaceLoadResult result = loadSurfaceData(panel.getSurfaceUrl(), request, adminTokenVO);
        panel.setData(result.data);
        panel.setError(result.error);
        panel.setSurfaceLoaded(result.loaded);
        Object data = result.data;
        if (StringUtils.isEmpty(panel.getTitle()) && data instanceof Map) {
            Object title = ((Map<?, ?>) data).get("title");
            if (title instanceof String && StringUtils.isNotEmpty((String) title)) {
                panel.setTitle((String) title);
            }
        }
    }

    private SurfaceLoadResult loadSurfaceData(String surfaceUrl, HttpRequest request, AdminTokenVO adminTokenVO) {
        try {
            PluginCorePlugin pluginCorePlugin = Constants.zrLogConfig.getPlugin(PluginCorePlugin.class);
            if (pluginCorePlugin == null) {
                return SurfaceLoadResult.error(adminMessage("admin.pluginSurface.error.coreUnavailable"));
            }
            CloseResponseHandle handle = pluginCorePlugin.getContext(surfaceUrl, HttpMethod.GET, request, adminTokenVO);
            if (handle.getT() == null || handle.getT().body() == null) {
                return SurfaceLoadResult.error(adminMessage("admin.pluginSurface.error.noResponse"));
            }
            try (InputStream inputStream = handle.getT().body()) {
                Map<String, Object> response = GSON.fromJson(new String(IOUtil.getByteByInputStream(inputStream)), MAP_TYPE);
                if (response == null || !Objects.equals(response.get("success"), true)) {
                    String message = response == null ? null : Objects.toString(response.get("message"), "");
                    return SurfaceLoadResult.error(StringUtils.isEmpty(message)
                            ? adminMessage("admin.pluginSurface.error.loadFailed") : message);
                }
                return SurfaceLoadResult.data(response.get("data"));
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Load dashboard plugin surface failed", e);
            return SurfaceLoadResult.error(StringUtils.isEmpty(e.getMessage())
                    ? adminMessage("admin.pluginSurface.error.loadFailed") : e.getMessage());
        }
    }

    private static String adminMessage(String key) {
        return I18nUtil.getAdminBackendStringFromRes(key);
    }

    private static class SurfaceLoadResult {
        private final Object data;
        private final String error;
        private final boolean loaded;

        private SurfaceLoadResult(Object data, String error, boolean loaded) {
            this.data = data;
            this.error = error;
            this.loaded = loaded;
        }

        private static SurfaceLoadResult data(Object data) {
            return new SurfaceLoadResult(data, null, true);
        }

        private static SurfaceLoadResult error(String error) {
            return new SurfaceLoadResult(null, error, false);
        }
    }

    private void fillMissingPanelFields(AdminDashboardCardResponse target, AdminDashboardCardResponse source) {
        if (StringUtils.isEmpty(target.getType())) {
            target.setType(source.getType());
        }
        if (StringUtils.isEmpty(target.getPluginName())) {
            target.setPluginName(source.getPluginName());
        }
        if (StringUtils.isEmpty(target.getTitle())) {
            target.setTitle(source.getTitle());
        }
        if (StringUtils.isEmpty(target.getSurfaceUrl())) {
            target.setSurfaceUrl(source.getSurfaceUrl());
        }
        if (StringUtils.isEmpty(target.getActionUrl())) {
            target.setActionUrl(source.getActionUrl());
        }
        if (StringUtils.isEmpty(target.getViewUrl())) {
            target.setViewUrl(source.getViewUrl());
        }
    }

    static AdminDashboardCardResponse normalizePanel(AdminDashboardCardResponse panel) {
        if (panel == null) {
            return null;
        }
        panel.setId(trimToNull(panel.getId()));
        panel.setPluginName(normalizePluginName(panel.getPluginName()));
        panel.setSurfaceUrl(normalizePluginContextPath(panel.getSurfaceUrl()));
        panel.setActionUrl(normalizePluginContextPath(panel.getActionUrl()));
        panel.setViewUrl(normalizePluginViewUrl(panel.getViewUrl()));

        String type = StringUtils.isEmpty(panel.getType()) ? PANEL_TYPE_SURFACE : panel.getType().trim();
        if (!PANEL_TYPE_SURFACE.equals(type) && !PANEL_TYPE_VIEW.equals(type)) {
            return null;
        }
        panel.setType(type);
        if (Objects.isNull(panel.getEnabled())) {
            panel.setEnabled(true);
        }
        if (Objects.isNull(panel.getMaxItems()) || panel.getMaxItems() <= 0) {
            panel.setMaxItems(5);
        }
        if (Objects.isNull(panel.getSort())) {
            panel.setSort(panel.getOrder());
        }
        if (PANEL_TYPE_VIEW.equals(type) && (Objects.isNull(panel.getHeight()) || panel.getHeight() <= 0)) {
            panel.setHeight(360);
        }
        if (StringUtils.isEmpty(panel.getId())) {
            if (StringUtils.isNotEmpty(panel.getPluginName())) {
                panel.setId(panel.getPluginName());
            } else if (StringUtils.isNotEmpty(panel.getSurfaceUrl())) {
                panel.setId(panel.getSurfaceUrl());
            } else if (StringUtils.isNotEmpty(panel.getViewUrl())) {
                panel.setId(panel.getViewUrl());
            }
        }
        if (StringUtils.isEmpty(panel.getId())) {
            return null;
        }
        if (PANEL_TYPE_SURFACE.equals(type)) {
            if (StringUtils.isNotEmpty(panel.getPluginName())) {
                if (StringUtils.isEmpty(panel.getSurfaceUrl())) {
                    panel.setSurfaceUrl(pluginUrl(panel.getPluginName(), "surface"));
                }
                if (StringUtils.isEmpty(panel.getActionUrl())) {
                    panel.setActionUrl(pluginUrl(panel.getPluginName(), "surfaceAction"));
                }
            }
            if (StringUtils.isEmpty(panel.getSurfaceUrl()) || StringUtils.isEmpty(panel.getActionUrl())) {
                return null;
            }
        }
        if (PANEL_TYPE_VIEW.equals(type) && StringUtils.isNotEmpty(panel.getPluginName()) && StringUtils.isEmpty(panel.getViewUrl())) {
            panel.setViewUrl("index");
        }
        return panel;
    }

    private static String trimToNull(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizePluginName(String pluginName) {
        String value = trimToNull(pluginName);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        return PLUGIN_NAME_PATTERN.matcher(value).matches() ? value : null;
    }

    private static String normalizePluginContextPath(String value) {
        String path = trimToNull(value);
        if (StringUtils.isEmpty(path) || !path.startsWith("/") || path.startsWith("//")) {
            return null;
        }
        try {
            URI uri = URI.create(path);
            if (StringUtils.isNotEmpty(uri.getScheme()) || StringUtils.isNotEmpty(uri.getHost())) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizePluginViewUrl(String value) {
        String viewUrl = trimToNull(value);
        if (StringUtils.isEmpty(viewUrl) || viewUrl.startsWith("//")) {
            return null;
        }
        try {
            URI uri = URI.create(viewUrl);
            String scheme = uri.getScheme();
            if (StringUtils.isNotEmpty(scheme)) {
                if (("http".equals(scheme) || "https".equals(scheme)) && StringUtils.isNotEmpty(uri.getHost())) {
                    return uri.toString();
                }
                return null;
            }
            String path = Objects.requireNonNullElse(uri.getPath(), "").replaceFirst("^/+", "");
            if (StringUtils.isEmpty(path) || hasParentPathSegment(path)) {
                return null;
            }
            String query = uri.getQuery();
            return StringUtils.isEmpty(query) ? path : path + "?" + query;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean hasParentPathSegment(String path) {
        for (String segment : path.split("/")) {
            if (Objects.equals(segment, "..")) {
                return true;
            }
        }
        return false;
    }

    private List<AdminDashboardCardResponse> loadStandardPluginPanels(HttpRequest request, AdminTokenVO adminTokenVO) {
        Map<String, Object> pluginResponse = loadPluginResponse(request, adminTokenVO);
        Object pluginsObj = pluginResponse.get("plugins");
        if (!(pluginsObj instanceof List)) {
            return Collections.emptyList();
        }
        List<AdminDashboardCardResponse> panels = new ArrayList<>();
        int index = 0;
        for (Object pluginObj : (List<?>) pluginsObj) {
            if (!(pluginObj instanceof Map)) {
                continue;
            }
            Map<?, ?> plugin = (Map<?, ?>) pluginObj;
            String pluginName = Objects.toString(plugin.get("shortName"), "");
            if (StringUtils.isEmpty(pluginName) || !hasSurfaceMetadata(plugin)) {
                continue;
            }
            AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
            panel.setKind(ITEM_KIND_PLUGIN);
            panel.setId(pluginName);
            panel.setEnabled(true);
            panel.setType(PANEL_TYPE_SURFACE);
            panel.setPluginName(pluginName);
            panel.setTitle(Objects.toString(plugin.get("name"), ""));
            panel.setSurfaceUrl(pluginUrl(pluginName, "surface"));
            panel.setActionUrl(pluginUrl(pluginName, "surfaceAction"));
            panel.setViewUrl(Objects.toString(plugin.get("indexPage"), "index"));
            panel.setMaxItems(5);
            panel.setSort(index * 10);
            panel.setOrder(index++);
            panels.add(panel);
        }
        return panels;
    }

    private boolean hasSurfaceMetadata(Map<?, ?> plugin) {
        return hasEntry(plugin.get("paths"), "surface") || hasEntry(plugin.get("actions"), "surface")
                || hasEntry(plugin.get("paths"), "surfaceAction") || hasEntry(plugin.get("actions"), "surfaceAction");
    }

    private boolean hasEntry(Object value, String target) {
        if (!(value instanceof List)) {
            return false;
        }
        for (Object entry : (List<?>) value) {
            String normalized = Objects.toString(entry, "").replaceFirst("^/+", "");
            if (Objects.equals(normalized, target) || normalized.startsWith(target + "?")) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> loadPluginResponse(HttpRequest request, AdminTokenVO adminTokenVO) {
        try {
            PluginCorePlugin pluginCorePlugin = Constants.zrLogConfig.getPlugin(PluginCorePlugin.class);
            if (pluginCorePlugin == null) {
                return Collections.emptyMap();
            }
            CloseResponseHandle handle = pluginCorePlugin.getContext("/api/plugins", HttpMethod.GET, request, adminTokenVO);
            if (handle.getT() == null || handle.getT().body() == null) {
                return Collections.emptyMap();
            }
            try (InputStream inputStream = handle.getT().body()) {
                return GSON.fromJson(new String(IOUtil.getByteByInputStream(inputStream)), MAP_TYPE);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Load dashboard plugin panels failed", e);
            return Collections.emptyMap();
        }
    }

    private static String pluginUrl(String pluginName, String action) {
        return "/admin/plugins/" + pluginName + "/" + action;
    }
}
