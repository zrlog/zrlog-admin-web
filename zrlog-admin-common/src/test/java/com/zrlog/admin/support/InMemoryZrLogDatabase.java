package com.zrlog.admin.support;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.web.token.AdminTokenService;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.di.DataComponent;
import com.zrlog.data.service.UserPreferenceStore;
import com.zrlog.model.User;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupLoader;
import com.zrlog.web.WebSetupProvider;
import com.zrlog.web.WebSiteInfoProvider;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/** Admin identity, resources and seed data on top of the shared database fixture. */
public class InMemoryZrLogDatabase implements AutoCloseable {

    private final ZrLogTestDatabase database;
    private final TestZrLogConfig config;
    private final AdminResource resources = new TestAdminResource();
    private final AdminTokenVO previousToken = AdminTokenThreadLocal.getUser();
    private final TestCacheService cacheService = new TestCacheService();

    private InMemoryZrLogDatabase(ZrLogTestDatabase database) throws Exception {
        this.database = database;
        try {
            config = new TestZrLogConfig(database.dataSource(), cacheService);
            seedBaseData();
            AdminTokenVO token = new AdminTokenVO();
            token.setUserId(1);
            token.setSessionId("session-1");
            setToken(token);
        } catch (Exception | Error failure) {
            try { close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static InMemoryZrLogDatabase open() throws Exception {
        return new InMemoryZrLogDatabase(ZrLogTestDatabase.open());
    }

    public static InMemoryZrLogDatabase openSqlite() throws Exception {
        return new InMemoryZrLogDatabase(ZrLogTestDatabase.open(ZrLogTestDatabase.DatabaseType.SQLITE));
    }

    public static InMemoryZrLogDatabase openWebApi() throws Exception {
        return new InMemoryZrLogDatabase(ZrLogTestDatabase.openWebApi());
    }

    public void loadAdminWebModules() {
        config.webModules(null);
    }

    /** Register controlled host boundaries after modules, before database-ready callbacks freeze bindings. */
    public void loadAdminWebModules(java.util.function.Consumer<ServerConfig> configure) {
        config.webModules(java.util.Objects.requireNonNull(configure));
    }

    public void beforeWebApiUpdate(ZrLogTestDatabase.SqlHook hook) { database.beforeWebApiUpdate(hook); }
    public void afterWebApiQuery(ZrLogTestDatabase.SqlHook hook) { database.afterWebApiQuery(hook); }
    public DataSourceWrapper dataSource() { return database.dataSource(); }
    public CacheService cacheService() {
        return component(DataComponent.class).cache();
    }
    /** Explicit fake for isolated unit tests; never substitute it into a loaded application. */
    public TestCacheService unitCacheService() {
        if (config.modulesLoaded) throw new IllegalStateException("Use the production cache after loading modules");
        return cacheService;
    }
    /** Resolve a production graph after all of the application's WebSetup registrations. */
    public <T> T component(Class<T> type) {
        loadAdminWebModules();
        return config.getServerConfig().getBindings().build().get(type);
    }
    public ZrLogConfig config() { return config; }
    public AdminResource resources() { return resources; }
    public boolean execute(String sql, Object... params) throws SQLException { return database.execute(sql, params); }
    public Object scalar(String sql, Object... params) throws SQLException { return database.scalar(sql, params); }
    public Map<String, Object> queryOne(String sql, Object... params) throws SQLException { return database.queryOne(sql, params); }
    public List<Map<String, Object>> queryList(String sql, Object... params) throws SQLException { return database.queryList(sql, params); }
    public void putWebsite(String name, Object value) throws SQLException { database.putWebsite(name, value); }

    private static void setToken(AdminTokenVO token) throws Exception {
        AdminTokenThreadLocal.remove();
        if (token != null) {
            var setter = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
            setter.setAccessible(true);
            setter.invoke(null, token);
        }
    }

    @Override public void close() throws Exception {
        try { database.close(); }
        finally {
            setToken(previousToken);
        }
    }

    private void seedBaseData() throws SQLException {
        execute("insert into user(userId, email, password, userName, header, role) values(?, ?, ?, ?, ?, ?)",
                1, "admin@example.com", "password", "admin", "/avatar.png", "admin");
        execute("insert into type(typeId, alias, typeName, remark) values(?, ?, ?, ?)",
                1, "default", "Default", "Default type");
        putWebsite("title", "ZrLog Test");
        putWebsite("host", "localhost:18080");
        putWebsite("language", Constants.DEFAULT_LANGUAGE);
        putWebsite("article_auto_digest_length", 80);

        TypeDTO type = new TypeDTO();
        type.setId(1L);
        type.setAlias("default");
        type.setTypeName("Default");
        type.setRemark("Default type");
        cacheService.articleTypes.add(type);
    }

    public static class TestCacheService implements CacheService {
        private final PublicWebSiteInfo publicInfo = new PublicWebSiteInfo();
        private final List<TypeDTO> articleTypes = new ArrayList<>();
        private final List<TagDTO> tags = new ArrayList<>();
        private int refreshCount;

        public TestCacheService() {
            publicInfo.setTitle("ZrLog Test");
            publicInfo.setHost("localhost:18080");
            publicInfo.setLanguage(Constants.DEFAULT_LANGUAGE);
            publicInfo.setGenerator_html_status(false);
            publicInfo.setDisable_comment_status(false);
            publicInfo.setArticle_thumbnail_status(true);
            publicInfo.setArticle_auto_digest_length(80L);
        }

        @Override
        public long getCurrentSqlVersion() {
            return 0;
        }

        @Override
        public long getWebSiteVersion() {
            return 0;
        }

        @Override
        public BaseDataInitVO getInitData() {
            return new BaseDataInitVO();
        }

        @Override
        public BaseDataInitVO refreshInitData() {
            refreshCount++;
            return getInitData();
        }

        public int getRefreshCount() {
            return refreshCount;
        }

        @Override
        public PublicWebSiteInfo getPublicWebSiteInfo() {
            return publicInfo;
        }

        @Override
        public List<TypeDTO> getArticleTypes() {
            return articleTypes;
        }

        @Override
        public List<TagDTO> getTags() {
            return tags;
        }

        @Override
        public UserBasicDTO getUserInfoById(Long userId) {
            UserBasicDTO user = new UserBasicDTO();
            user.setUserId(userId);
            user.setUserName("admin");
            user.setHeader("/avatar.png");
            return user;
        }

        @Override
        public Map<String, Object> getTemplateConfigMapWithCache(String template) {
            return Map.of();
        }
    }

    private static class TestZrLogConfig extends ZrLogConfig {
        private final DataSourceWrapper testDataSource;
        private final CacheService cacheService;
        private boolean modulesLoaded;

        @Override
        public <T> T getWebSetup(Class<T> capability) {
            if (!modulesLoaded && capability == WebSiteInfoProvider.class) {
                return capability.cast((WebSiteInfoProvider) cacheService::getPublicWebSiteInfo);
            }
            return super.getWebSetup(capability);
        }

        private TestZrLogConfig(DataSourceWrapper dataSource, CacheService cacheService) {
            super(18080, null, "");
            this.testDataSource = dataSource;
            this.dataSource = dataSource;
            this.cacheService = cacheService;
            var context = new WebSetupContext(this, dbPropertiesFile, installLockFile, "", null);
            ServiceLoader.load(WebSetupProvider.class)
                    .forEach(
                            provider -> {
                                if (provider.name().startsWith("admin-"))
                                    this.webSetups.add(provider.create(context));
                            });
        }

        private synchronized void webModules(java.util.function.Consumer<ServerConfig> configure) {
            if (modulesLoaded) {
                if (configure != null) throw new IllegalStateException("Configure test boundaries before loading modules");
                return;
            }
            this.webSetups.clear();
            this.webSetups.addAll(
                    WebSetupLoader.load(
                            new WebSetupContext(this, dbPropertiesFile, installLockFile, "", null),
                            provider ->
                                    provider.name().equals("data")
                                            || provider.name().equals("services")
                                            || provider.name().equals("admin")
                                            || provider.name().startsWith("admin-")));
            if (configure != null) {
                this.webSetups.add(new WebSetup() {
                    @Override public void setup() { configure.accept(serverConfig); }
                });
            }
            modulesLoaded = true;
            initializeWebSetups();
        }

        @Override
        public boolean isInstalled() {
            return modulesLoaded;
        }

        @Override
        public DataSourceWrapper configDatabase() throws Exception {
            return modulesLoaded ? super.configDatabase() : testDataSource;
        }

        private boolean staticHtmlEnabled() {
            return Boolean.TRUE.equals(cacheService.getPublicWebSiteInfo().getGenerator_html_status());
        }

        private TokenService testTokens;

        @Override
        protected TokenService initTokenService() {
            if (modulesLoaded) return super.initTokenService();
            if (testTokens == null) {
                testTokens = new AdminTokenService(60, this::isInstalled, this::staticHtmlEnabled,
                        () -> new User(testDataSource), () -> new UserPreferenceStore(() -> new User(testDataSource)));
            }
            return testTokens;
        }

        @Override
        public ServerConfig getServerConfig() {
            return serverConfig;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }

    public static class TestAdminResource implements AdminResource {
        @Override
        public java.util.Set<String> getAdminStaticResourceUris() {
            return java.util.Set.of();
        }

        @Override
        public java.util.Set<String> getAdminPageUris() {
            return java.util.Set.of();
        }

        @Override
        public java.util.Set<String> getAdminStaticCacheUris() {
            return java.util.Set.of();
        }

        @Override
        public java.util.Set<String> getAdminCacheableApiUris() {
            return java.util.Set.of("/api/admin/website", "/api/admin/user");
        }

        @Override
        public InputStream renderServiceWorker(HttpRequest request) {
            return InputStream.nullInputStream();
        }

        @Override
        public String getStaticResourceBuildId() {
            return "test";
        }

    }
}
