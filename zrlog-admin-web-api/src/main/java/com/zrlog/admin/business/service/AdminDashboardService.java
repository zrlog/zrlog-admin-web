package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.request.AdminDashboardConfigRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ThreadUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Assembles the user's dashboard from preferences, content statistics and plugin panels. */
@Singleton
public class AdminDashboardService {
    public static final String DASHBOARD_CONFIG_KEY = "admin_dashboard_config";
    public static final String FIRST_USE_CHECKLIST_KEY = "admin_first_use_v4";
    public static final int FIRST_USE_CHECKLIST_VERSION = 1;
    private static final String FIRST_USE_CHECKLIST_PENDING = "pending";
    private static final String FIRST_USE_CHECKLIST_DISMISSED = "dismissed";
    private static final String ITEM_KIND_CARD = "card";
    private static final Gson GSON = new Gson();
    private static final Type CONFIG_TYPE = new TypeToken<AdminDashboardConfigResponse>() {}.getType();
    private static final Logger LOGGER = Logger.getLogger(AdminDashboardService.class.getName());
    private final UserPreferenceService userPreferenceService;
    private final AccountIdentityService identities;
    private final WebsiteKvService kvService;
    private final AdminStatisticsService statisticsService;
    private final ArticleQueryService articleService;
    private final DashboardPluginLoader plugins;
    private final DashboardLayoutPolicy layout;

    @Inject
    public AdminDashboardService(WebsiteKvService kvService, AdminStatisticsService statisticsService,
                                 ArticleQueryService articleService, DashboardPluginLoader plugins,
                                 UserPreferenceService userPreferenceService, AccountIdentityService identities,
                                 DashboardLayoutPolicy layout) {
        this.kvService = Objects.requireNonNull(kvService);
        this.statisticsService = statisticsService;
        this.articleService = articleService;
        this.plugins = plugins;
        this.userPreferenceService = userPreferenceService;
        this.identities = identities;
        this.layout = layout;
    }

    public AdminDashboardConfigResponse getConfig(HttpRequest request, AdminTokenVO adminTokenVO) {
        return getConfig(request, adminTokenVO, false);
    }

    public FirstUseChecklistResponse getFirstUseChecklist() {
        if (!FIRST_USE_CHECKLIST_PENDING.equals(kvService.getString(FIRST_USE_CHECKLIST_KEY))) {
            return null;
        }
        return new FirstUseChecklistResponse(FIRST_USE_CHECKLIST_VERSION, FIRST_USE_CHECKLIST_PENDING);
    }

    public FirstUseChecklistResponse dismissFirstUseChecklist(int version) throws SQLException {
        if (version != FIRST_USE_CHECKLIST_VERSION) {
            throw new ArgsException("version");
        }
        if (!kvService.putString(FIRST_USE_CHECKLIST_KEY, FIRST_USE_CHECKLIST_DISMISSED)) {
            throw new SQLException("Unable to persist first-use checklist status");
        }
        return new FirstUseChecklistResponse(FIRST_USE_CHECKLIST_VERSION, FIRST_USE_CHECKLIST_DISMISSED);
    }

    public IndexResponse loadIndex(HttpRequest request, AdminTokenVO adminTokenVO) {
        List<String> tips = loadWelcomeTips();
        Collections.shuffle(tips);
        AdminDashboardConfigResponse config = getConfig(request, adminTokenVO, true);
        AccountAccess actor = adminTokenVO == null ? null : DelegatedAccess.restrict(identities.account(adminTokenVO));
        boolean auditTrailEnabled = layout.isCardEnabled(config, "auditTrail");
        boolean activityEnabled = layout.isCardEnabled(config, "activity");
        ExecutorService executor = ThreadUtils.newFixedThreadPool(20);
        try {
            CompletableFuture<StatisticsInfoResponse> statistics =
                    statisticsService.statisticsInfo(executor, auditTrailEnabled, actor);
            CompletableFuture<List<ArticleActivityData>> activity = activityEnabled
                    ? articleService.activityDataList(executor)
                    : CompletableFuture.completedFuture(Collections.emptyList());
            CompletableFuture.allOf(statistics, activity).join();
            attachCardData(config, statistics.join(),
                    I18nUtil.getAdminBackendStringFromRes("admin.index.welcomeTip"),
                    Collections.singletonList(tips.get(0)), BlogBuildInfoUtil.getVersionInfo(), activity.join());
            return new IndexResponse(config, getFirstUseChecklist());
        } finally {
            executor.shutdown();
        }
    }

    void attachCardData(AdminDashboardConfigResponse config, StatisticsInfoResponse statistics,
                        String welcomeTip, List<String> tips, String versionInfo,
                        List<ArticleActivityData> activity) {
        if (config == null || config.getCards() == null) {
            return;
        }
        for (AdminDashboardCardResponse item : config.getCards()) {
            if (!Objects.equals(ITEM_KIND_CARD, item.getKind())) {
                continue;
            }
            if (Objects.equals("welcome", item.getId())) {
                item.setData(new AdminDashboardWelcomeDataResponse(welcomeTip, tips, versionInfo));
            } else if (Objects.equals("quickAction", item.getId())) {
                item.setData(new AdminDashboardQuickActionDataResponse(statistics.getDraftCount()));
            } else if (Objects.equals("statistics", item.getId())) {
                item.setData(statistics);
            } else if (Objects.equals("activity", item.getId())) {
                item.setData(activity);
            } else if (Objects.equals("auditTrail", item.getId())) {
                item.setData(new AdminDashboardAuditTrailDataResponse(statistics.getAuditLogs(), false));
            } else if (Objects.equals("dataInsights", item.getId())) {
                item.setData(new AdminDashboardDataInsightsResponse(statistics.getTypeData(), statistics.getTagData()));
            }
        }
    }

    List<String> loadWelcomeTips() {
        List<String> tips = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String tip = I18nUtil.getAdminBackendStringFromRes("admin.index.welcomeTips." + i);
            if (tip == null || tip.trim().isEmpty()) {
                break;
            }
            tips.add(tip);
        }
        return tips;
    }

    public AdminDashboardConfigResponse getConfig(HttpRequest request, AdminTokenVO adminTokenVO, boolean preloadSurfaces) {
        AdminDashboardConfigResponse savedConfig =
                this.userPreferenceService.dashboard(adminTokenVO == null ? null : identities.account(adminTokenVO));
        if (savedConfig == null) savedConfig = readSavedConfig();
        List<AdminDashboardCardConfigResponse> cards = layout.mergeCards(layout.extractCardItems(savedConfig.getCards()));
        List<AdminDashboardCardResponse> pluginPanels = layout.mergePluginPanels(
                layout.extractPluginItems(savedConfig.getCards()), plugins.loadStandardPluginPanels(request, adminTokenVO));
        if (preloadSurfaces) {
            plugins.preloadSurfaceData(pluginPanels, request, adminTokenVO);
        }
        AdminDashboardConfigResponse config = new AdminDashboardConfigResponse();
        config.setCards(layout.toItems(cards, pluginPanels));
        AccountAccess actor = adminTokenVO == null ? null : DelegatedAccess.restrict(identities.account(adminTokenVO));
        if (actor == null || !AccountAction.SYSTEM_MANAGE.allowed(actor)) {
            config.getCards().removeIf(item -> "auditTrail".equals(item.getId()));
        }
        layout.fillRefreshConfig(config, savedConfig);
        return config;
    }

    public AdminDashboardConfigResponse saveConfig(AdminDashboardConfigRequest request, HttpRequest httpRequest,
                                                   AdminTokenVO adminTokenVO) {
        List<AdminDashboardCardResponse> requestCards = layout.toCardResponses(request.getCards());
        List<AdminDashboardCardConfigResponse> cards = layout.mergeCards(layout.extractCardItems(requestCards));
        List<AdminDashboardCardResponse> pluginPanels = layout.mergePluginPanels(
                layout.extractPluginItems(requestCards), plugins.loadStandardPluginPanels(httpRequest, adminTokenVO));
        List<AdminDashboardCardResponse> dashboardCards = layout.toItems(cards, pluginPanels);
        layout.clearRuntimeData(dashboardCards);
        AdminDashboardConfigResponse config = new AdminDashboardConfigResponse();
        config.setCards(dashboardCards);
        layout.fillRefreshConfig(config, request);
        this.userPreferenceService.saveDashboard(identities.account(adminTokenVO), config);
        return getConfig(httpRequest, adminTokenVO, true);
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

}
