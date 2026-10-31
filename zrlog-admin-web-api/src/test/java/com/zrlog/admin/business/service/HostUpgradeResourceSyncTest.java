package com.zrlog.admin.business.service;

import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.sun.net.httpserver.HttpServer;
import com.zrlog.admin.business.automation.TaskExecutionContext;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.common.CacheService;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class HostUpgradeResourceSyncTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private InMemoryZrLogDatabase db;
    private HttpServer server;
    private PublicWebSiteInfo info;
    private WebsiteKvService settings;
    private HostUpgradeResourceSync sync;
    private TaskExecutionContext context;
    private final AtomicLong now = new AtomicLong();
    private final AtomicReference<String> delivered = new AtomicReference<>("old-resources");
    private final List<String> events = new ArrayList<>();
    private boolean hasUi = true, hasGenerator = true, failDelivery, rejectVersion;

    @Before public void open() throws Exception {
        db = InMemoryZrLogDatabase.open();
        settings = new WebsiteKvService(() -> new WebSite(db.dataSource()));
        info = new PublicWebSiteInfo();
        info.setGenerator_html_status(true);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        info.setHost("127.0.0.1:" + server.getAddress().getPort());
        server.createContext("/sub/admin-version.txt", exchange -> {
            byte[] bytes = (rejectVersion ? "old-resources" : delivered.get()).getBytes(StandardCharsets.UTF_8);
            if (rejectVersion) now.set(30000);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        var resource = folder.newFile("admin.js");
        StaticSitePlugin site = new StaticSitePlugin() {
            public StaticSiteType getType() { return StaticSiteType.ADMIN; }
            public PublicWebSiteInfo getSiteInfo() { return info; }
            public String getContextPath() { return "/sub"; }
            public String getDefaultLang() { return "en_US"; }
            public String getVersionFileName() { return "admin-version.txt"; }
            public String getDbCacheKey() { return "admin_static_version"; }
            public List<java.io.File> getCacheFiles() { return List.of(resource); }
            public java.util.Map<String, HandleState> getHandleStatusPageMap() { throw new AssertionError(); }
            public java.util.concurrent.locks.Lock getParseLock() { throw new AssertionError(); }
            public java.util.concurrent.Executor getExecutorService() { throw new AssertionError("No background work"); }
            public boolean isStarted() { return true; }
            public boolean start() { throw new AssertionError("No unbounded generation"); }
            public boolean stop() { return true; }
            public boolean startWithinBudget(com.zrlog.business.plugin.StaticGenerationBudget budget) throws Exception {
                budget.check();
                events.add("generate");
                Files.writeString(resource.toPath(), "new admin assets");
                return true;
            }
        };
        PluginCorePlugin plugins = (PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isStarted")) return true;
                    if (method.getName().equals("refreshCacheWithinBudget")) {
                        assertEquals("upgrade-run", args[1]);
                        assertEquals(1, args[2]);
                        assertTrue((long) args[3] > 0 && (long) args[3] <= 30000);
                        events.add(events.contains("generate") ? "deliver" : "refresh");
                        if (events.contains("generate")) {
                            if (failDelivery) return false;
                            delivered.set((String) args[0]);
                        }
                        return true;
                    }
                    throw new AssertionError("Unbounded plugin method: " + method.getName());
                });
        CacheService cache = (CacheService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{CacheService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getPublicWebSiteInfo")) return info;
                    if (method.getName().equals("refreshInitData")) return new BaseDataInitVO();
                    throw new AssertionError(method.getName());
                });
        var refresh = new CacheRefreshService(cache, () -> plugins, () -> hasGenerator ? List.of(site) : List.of(),
                () -> new WebSite(db.dataSource()));
        var serverConfig = new ServerConfig();
        serverConfig.setContextPath("/sub");
        sync = new HostUpgradeResourceSync(refresh, () -> hasGenerator ? List.of(site) : List.of(), () -> hasUi,
                () -> HostUpgradeResourceSync.request(info.getHost(), "", new RequestConfig(), new ApplicationContext(serverConfig)),
                new AdminAuditService(settings));
        var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
        context = new TaskExecutionContext("upgrade-run", actor, 30000, now::get, () -> { });
    }

    @After public void close() throws Exception {
        if (server != null) server.stop(0);
        if (db != null) db.close();
    }

    @Test public void completesGenerationDeliveryAndPublicVersionCheckBeforeAudit() throws Exception {
        sync.synchronize(context);
        assertEquals(List.of("refresh", "generate", "deliver"), events);
        assertEquals(delivered.get(), settings.getString("admin_static_version"));
        assertTrue(settings.getString("admin_audit_log").contains("SYNC_ADMIN_STATIC_SITE"));
        var log = new AdminAuditService(settings).getRecentLogs().get(0);
        assertEquals("upgrade-run", log.getAssistantRunId());
        assertEquals("", log.getContent());
    }

    @Test public void failedDeliveryAndUnconfirmedPublicResourcesNeverStampSuccess() throws Exception {
        failDelivery = true;
        assertThrows(IllegalStateException.class, () -> sync.synchronize(context));
        assertTrue(settings.getString("admin_static_version").isEmpty());
        assertTrue(settings.getString("admin_audit_log").isEmpty());
        failDelivery = false;
        rejectVersion = true;
        assertThrows(IllegalStateException.class, () -> sync.synchronize(context));
        assertTrue(settings.getString("admin_static_version").isEmpty());
        assertTrue(settings.getString("admin_audit_log").isEmpty());
    }

    @Test public void dynamicAndApiOnlyDeploymentsNeedNoResourcePublishing() throws Exception {
        info.setGenerator_html_status(false);
        sync.synchronize(context);
        info.setGenerator_html_status(true);
        hasUi = false;
        sync.synchronize(context);
        assertTrue(events.isEmpty());
        assertTrue(settings.getString("admin_audit_log").isEmpty());
    }

    @Test public void missingGeneratorOrRevokedAuthorityDoesNotPerformPublishing() {
        hasGenerator = false;
        assertThrows(IllegalStateException.class, () -> sync.synchronize(context));
        hasGenerator = true;
        var restricted = new TaskExecutionContext("upgrade-run", context.actor().restrictActions(List.of("account.session")),
                30000, now::get, () -> { });
        assertThrows(com.zrlog.admin.business.exception.PermissionErrorException.class, () -> sync.synchronize(restricted));
        assertTrue(events.isEmpty());
    }

    @Test public void usesThePublicSiteProtocolWithoutCopyingCdnOrBackendPaths() throws Exception {
        var config = new ServerConfig();
        config.setContextPath("/sub");
        var application = new ApplicationContext(config);
        var http = HostUpgradeResourceSync.request("blog.example", "http://blog.example/assets", new RequestConfig(), application);
        assertEquals("http", http.getScheme());
        assertEquals("/sub", http.getContextPath());
        var https = HostUpgradeResourceSync.request("blog.example", "http://cdn.example/assets", new RequestConfig(), application);
        assertEquals("https", https.getScheme());
        assertThrows(IllegalArgumentException.class, () -> HostUpgradeResourceSync.request("blog.example",
                "javascript:alert(1)", new RequestConfig(), application));
    }
}
