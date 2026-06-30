package com.zrlog.admin.support;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

public class UploadFallbackZrLogConfig extends ZrLogConfig {

    public UploadFallbackZrLogConfig() {
        super(18080, null, "");
        this.plugins.add(new EmptyUploadPluginCorePlugin());
    }

    @Override
    public boolean isInstalled() {
        return false;
    }

    @Override
    public DataSourceWrapper configDatabase() {
        return null;
    }

    @Override
    protected TokenService initTokenService() {
        return null;
    }

    @Override
    public List<IPlugin> getBasePluginList() {
        return new Plugins();
    }

    private static class EmptyUploadPluginCorePlugin implements PluginCorePlugin {

        @Override
        public boolean refreshCache(String cacheVersion, HttpRequest request) {
            return true;
        }

        @Override
        public CloseResponseHandle getContext(String uri, HttpMethod method, HttpRequest request,
                                              AdminTokenVO adminTokenVO)
                throws IOException, URISyntaxException, InterruptedException {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T requestService(HttpRequest inputRequest, Map<String, String[]> params,
                                    AdminTokenVO adminTokenVO, Class<T> clazz)
                throws IOException, URISyntaxException, InterruptedException {
            if (clazz.isArray()) {
                return (T) java.lang.reflect.Array.newInstance(clazz.getComponentType(), 0);
            }
            return null;
        }

        @Override
        public boolean accessPlugin(String uri, HttpRequest request, HttpResponse response,
                                    AdminTokenVO adminTokenVO)
                throws IOException, URISyntaxException, InterruptedException {
            return false;
        }

        @Override
        public String getToken() {
            return "test-token";
        }

        @Override
        public boolean start() {
            return true;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean stop() {
            return true;
        }
    }
}
