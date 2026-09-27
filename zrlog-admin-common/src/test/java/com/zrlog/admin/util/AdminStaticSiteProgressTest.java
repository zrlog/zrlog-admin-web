package com.zrlog.admin.util;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.response.StaticSiteProgressResponse;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class AdminStaticSiteProgressTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldAggregateStaticSiteProgressAcrossConfiguredPlugins() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        Map<String, StaticSitePlugin.HandleState> blogStates = new LinkedHashMap<>();
        blogStates.put("/handled.html", StaticSitePlugin.HandleState.HANDLED);
        blogStates.put("/handing.html", StaticSitePlugin.HandleState.HANDING);
        blogStates.put("/retry.html", StaticSitePlugin.HandleState.RE_FETCH);
        blogStates.put("/pending.html", StaticSitePlugin.HandleState.NEW);
        Map<String, StaticSitePlugin.HandleState> adminStates = new LinkedHashMap<>();
        adminStates.put("/admin.html", StaticSitePlugin.HandleState.HANDLED);
        try {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-root").getAbsolutePath());
            Constants.zrLogConfig = new StaticPluginConfig(List.of(
                    new FakeStaticSitePlugin(StaticSiteType.BLOG, blogStates),
                    new FakeStaticSitePlugin(StaticSiteType.ADMIN, adminStates)
            ));

            StaticSiteProgressResponse blogSnapshot = AdminStaticSiteProgress.snapshot(StaticSiteType.BLOG);
            assertEquals(4, blogSnapshot.getTotal());
            assertEquals(1, blogSnapshot.getHandled());
            assertEquals(1, blogSnapshot.getHanding());
            assertEquals(1, blogSnapshot.getRetrying());
            assertEquals(1, blogSnapshot.getPending());
            assertEquals(List.of("BLOG"), blogSnapshot.getSiteTypes());

            StaticSiteProgressResponse allSnapshot =
                    AdminStaticSiteProgress.snapshot(List.of(StaticSiteType.BLOG, StaticSiteType.ADMIN));
            assertEquals(5, allSnapshot.getTotal());
            assertEquals(2, allSnapshot.getHandled());
            assertEquals(List.of("BLOG", "ADMIN"), allSnapshot.getSiteTypes());
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    private static class StaticPluginConfig extends ZrLogConfig {

        private final List<StaticSitePlugin> plugins;

        StaticPluginConfig(List<StaticSitePlugin> plugins) {
            super(18080, null, "");
            this.plugins = plugins;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }

        @Override
        public <T extends IPlugin> List<T> getPluginsByClazz(Class<T> pluginClass) {
            return plugins.stream()
                    .filter(pluginClass::isInstance)
                    .map(pluginClass::cast)
                    .collect(Collectors.toList());
        }
    }

    private static class FakeStaticSitePlugin implements StaticSitePlugin {

        private final StaticSiteType type;
        private final Map<String, HandleState> stateMap;
        private final Lock lock = new ReentrantLock();

        FakeStaticSitePlugin(StaticSiteType type, Map<String, HandleState> stateMap) {
            this.type = type;
            this.stateMap = stateMap;
        }

        @Override
        public String getVersionFileName() {
            return "version.txt";
        }

        @Override
        public String getDbCacheKey() {
            return type.name();
        }

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public String getDefaultLang() {
            return "zh_CN";
        }

        @Override
        public Map<String, HandleState> getHandleStatusPageMap() {
            return stateMap;
        }

        @Override
        public Lock getParseLock() {
            return lock;
        }

        @Override
        public Executor getExecutorService() {
            return Runnable::run;
        }

        @Override
        public List<File> getCacheFiles() {
            return new ArrayList<>();
        }

        @Override
        public StaticSiteType getType() {
            return type;
        }

        @Override
        public File loadCacheFile(HttpRequest request) {
            return null;
        }

        @Override
        public boolean start() {
            return true;
        }

        @Override
        public boolean isStarted() {
            return false;
        }

        @Override
        public boolean stop() {
            return true;
        }
    }
}
