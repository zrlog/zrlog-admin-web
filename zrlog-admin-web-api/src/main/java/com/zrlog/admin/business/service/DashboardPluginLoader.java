package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.response.AdminDashboardCardResponse;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ThreadUtils;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Fetches plugin panel metadata and surfaces with a bounded per-request deadline. */
public final class DashboardPluginLoader {
    private static final String PANEL_TYPE_SURFACE = "surface";
    private static final String ITEM_KIND_PLUGIN = "plugin";
    private static final long DEFAULT_SURFACE_PRELOAD_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final Logger LOGGER = Logger.getLogger(DashboardPluginLoader.class.getName());
    private final Supplier<PluginCorePlugin> pluginCore;
    private final long surfacePreloadTimeoutMillis;

    @Inject
    public DashboardPluginLoader(Supplier<PluginCorePlugin> pluginCore) {
        this(pluginCore, DEFAULT_SURFACE_PRELOAD_TIMEOUT_MILLIS);
    }

    DashboardPluginLoader(Supplier<PluginCorePlugin> pluginCore, long timeoutMillis) {
        this.pluginCore = Objects.requireNonNull(pluginCore);
        this.surfacePreloadTimeoutMillis = timeoutMillis <= 0 ? DEFAULT_SURFACE_PRELOAD_TIMEOUT_MILLIS : timeoutMillis;
    }

    private static String adminMessage(String key) {
        return I18nUtil.getAdminBackendStringFromRes(key);
    }

    void preloadSurfaceData(List<AdminDashboardCardResponse> panels, HttpRequest request,
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
        List<CompletableFuture<SurfacePanelLoadResult>> futures = new ArrayList<>();
        boolean forceShutdown = false;
        String unfinishedError = adminMessage("admin.pluginSurface.error.loadFailed");
        try {
            for (AdminDashboardCardResponse panel : loadablePanels) {
                futures.add(CompletableFuture.supplyAsync(() -> loadSurfacePanel(panel, request, adminTokenVO), executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(surfacePreloadTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            forceShutdown = true;
            unfinishedError = adminMessage("admin.pluginSurface.error.timeout");
            LOGGER.log(Level.FINE, "Load dashboard plugin surface timed out", e);
        } catch (InterruptedException e) {
            forceShutdown = true;
            Thread.currentThread().interrupt();
            LOGGER.log(Level.FINE, "Load dashboard plugin surface interrupted", e);
        } catch (ExecutionException e) {
            forceShutdown = true;
            LOGGER.log(Level.FINE, "Load dashboard plugin surface failed", e);
        } finally {
            applySurfacePreloadResults(loadablePanels, futures, unfinishedError);
            if (forceShutdown) {
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
        }
    }

    private boolean isLoadableSurfacePanel(AdminDashboardCardResponse panel) {
        return panel != null && PANEL_TYPE_SURFACE.equals(panel.getType()) && !Objects.equals(panel.getEnabled(), false)
                && StringUtils.isNotEmpty(panel.getSurfaceUrl());
    }

    private SurfacePanelLoadResult loadSurfacePanel(AdminDashboardCardResponse panel, HttpRequest request,
                                                    AdminTokenVO adminTokenVO) {
        SurfaceLoadResult result = loadSurfaceData(panel.getSurfaceUrl(), request, adminTokenVO);
        Object data = result.data;
        String title = null;
        if (StringUtils.isEmpty(panel.getTitle()) && data instanceof Map) {
            Object titleValue = ((Map<?, ?>) data).get("title");
            if (titleValue instanceof String && StringUtils.isNotEmpty((String) titleValue)) {
                title = (String) titleValue;
            }
        }
        return new SurfacePanelLoadResult(result, title);
    }

    private void applySurfacePreloadResults(List<AdminDashboardCardResponse> panels,
                                            List<CompletableFuture<SurfacePanelLoadResult>> futures,
                                            String unfinishedError) {
        for (int i = 0; i < panels.size(); i++) {
            AdminDashboardCardResponse panel = panels.get(i);
            CompletableFuture<SurfacePanelLoadResult> future = futures.get(i);
            if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
                applySurfacePanelLoadResult(panel, future.join());
                continue;
            }
            future.cancel(true);
            markSurfacePanelError(panel, unfinishedError);
        }
    }

    private void applySurfacePanelLoadResult(AdminDashboardCardResponse panel, SurfacePanelLoadResult preloadResult) {
        SurfaceLoadResult result = preloadResult.result;
        panel.setData(result.data);
        panel.setError(result.error);
        panel.setSurfaceLoaded(result.loaded);
        if (StringUtils.isNotEmpty(preloadResult.title)) {
            panel.setTitle(preloadResult.title);
        }
    }

    private void markSurfacePanelError(AdminDashboardCardResponse panel, String error) {
        panel.setData(null);
        panel.setError(error);
        panel.setSurfaceLoaded(false);
    }

    private SurfaceLoadResult loadSurfaceData(String surfaceUrl, HttpRequest request, AdminTokenVO adminTokenVO) {
        try {
            PluginCorePlugin pluginCorePlugin = pluginCore.get();
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

    List<AdminDashboardCardResponse> loadStandardPluginPanels(HttpRequest request, AdminTokenVO adminTokenVO) {
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
            panel.setSurfaceUrl(DashboardLayoutPolicy.pluginUrl(pluginName, "surface"));
            panel.setActionUrl(DashboardLayoutPolicy.pluginUrl(pluginName, "surfaceAction"));
            panel.setViewUrl(Objects.toString(plugin.get("indexPage"), "index"));
            panel.setMaxItems(5);
            panel.setSort(index * 10);
            panel.setOrder(index++);
            panels.add(panel);
        }
        return panels;
    }

    boolean hasSurfaceMetadata(Map<?, ?> plugin) {
        return hasEntry(plugin.get("paths"), "surface") || hasEntry(plugin.get("actions"), "surface")
                || hasEntry(plugin.get("paths"), "surfaceAction") || hasEntry(plugin.get("actions"), "surfaceAction");
    }

    boolean hasEntry(Object value, String target) {
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
            PluginCorePlugin pluginCorePlugin = pluginCore.get();
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

    private static class SurfacePanelLoadResult {
        private final SurfaceLoadResult result;
        private final String title;

        private SurfacePanelLoadResult(SurfaceLoadResult result, String title) {
            this.result = result;
            this.title = title;
        }
    }}
