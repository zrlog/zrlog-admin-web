package com.zrlog.admin.business.service;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.request.AdminDashboardCardRequest;
import com.zrlog.admin.business.rest.request.AdminDashboardConfigRequest;
import com.zrlog.admin.business.rest.response.*;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Pure layout normalization and merging; no database or plugin requests. */
public final class DashboardLayoutPolicy {
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

    @Inject
    public DashboardLayoutPolicy() {
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

    static String normalizePluginName(String pluginName) {
        String value = trimToNull(pluginName);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        return PLUGIN_NAME_PATTERN.matcher(value).matches() ? value : null;
    }

    static String normalizePluginContextPath(String value) {
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

    static String normalizePluginViewUrl(String value) {
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

    static boolean hasParentPathSegment(String path) {
        for (String segment : path.split("/")) {
            if (Objects.equals(segment, "..")) {
                return true;
            }
        }
        return false;
    }

    static String pluginUrl(String pluginName, String action) {
        return "/admin/plugins/" + pluginName + "/" + action;
    }

    boolean isCardEnabled(AdminDashboardConfigResponse config, String cardId) {
        if (config == null || config.getCards() == null) {
            return true;
        }
        for (AdminDashboardCardResponse item : config.getCards()) {
            if (Objects.equals(ITEM_KIND_CARD, item.getKind()) && Objects.equals(cardId, item.getId())) {
                return !Objects.equals(item.getEnabled(), false);
            }
        }
        return true;
    }

    List<AdminDashboardCardResponse> toCardResponses(List<AdminDashboardCardRequest> requestCards) {
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

    void fillRefreshConfig(AdminDashboardConfigResponse target, AdminDashboardConfigResponse source) {
        target.setAutoRefreshEnabled(Objects.equals(source.getAutoRefreshEnabled(), true));
        Integer interval = source.getAutoRefreshIntervalSeconds();
        target.setAutoRefreshIntervalSeconds(interval == null || interval < MIN_AUTO_REFRESH_INTERVAL_SECONDS
                ? DEFAULT_AUTO_REFRESH_INTERVAL_SECONDS
                : interval);
    }

    void fillRefreshConfig(AdminDashboardConfigResponse target, AdminDashboardConfigRequest source) {
        target.setAutoRefreshEnabled(Objects.equals(source.getAutoRefreshEnabled(), true));
        Integer interval = source.getAutoRefreshIntervalSeconds();
        target.setAutoRefreshIntervalSeconds(interval == null || interval < MIN_AUTO_REFRESH_INTERVAL_SECONDS
                ? DEFAULT_AUTO_REFRESH_INTERVAL_SECONDS
                : interval);
    }

    List<AdminDashboardCardConfigResponse> mergeCards(List<AdminDashboardCardConfigResponse> savedCards) {
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

    List<AdminDashboardCardResponse> mergePluginPanels(List<AdminDashboardCardResponse> savedPanels,
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

    void clearRuntimeData(List<AdminDashboardCardResponse> items) {
        if (items != null) {
            for (AdminDashboardCardResponse item : items) {
                item.setData(null);
                item.setError(null);
                item.setSurfaceLoaded(null);
            }
        }
    }

    List<AdminDashboardCardResponse> toItems(List<AdminDashboardCardConfigResponse> cards,
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

    List<AdminDashboardCardConfigResponse> extractCardItems(List<AdminDashboardCardResponse> items) {
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

    List<AdminDashboardCardResponse> extractPluginItems(List<AdminDashboardCardResponse> items) {
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

}
