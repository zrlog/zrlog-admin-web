package com.zrlog.admin.business.rest.response;

import com.zrlog.util.NetworkProxy;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProxySettingsResponse {
    private final String url;
    private final String noProxy;
    private final String source;
    private final String httpsSource;
    private final String noProxySource;
    private final String httpVariable;
    private final String httpsVariable;
    private final String noProxyVariable;
    private final String activeUrl;
    private final String activeHttpsUrl;
    private final String activeNoProxy;
    private final boolean restartRequired;
    private final boolean writable;

    public ProxySettingsResponse(NetworkProxy.Settings active, NetworkProxy.Settings configured) {
        NetworkProxy.Settings stored = NetworkProxy.fileSettings();
        url = stored.getUrl();
        noProxy = stored.getNoProxy();
        source = configured.getSource();
        httpsSource = configured.getHttpsSource();
        noProxySource = configured.getNoProxySource();
        httpVariable = configured.getHttpVariable();
        httpsVariable = configured.getHttpsVariable();
        noProxyVariable = configured.getNoProxyVariable();
        activeUrl = active.getUrl();
        activeHttpsUrl = active.getHttpsUrl();
        activeNoProxy = active.getNoProxy();
        restartRequired = !active.samePolicy(configured);
        Path file = NetworkProxy.configFile();
        Path parent = file.toAbsolutePath().getParent();
        while (parent != null && !Files.exists(parent)) parent = parent.getParent();
        writable = parent != null && Files.isWritable(parent) && (!Files.exists(file) || Files.isWritable(file));
    }

    public String getUrl() { return url; }
    public String getNoProxy() { return noProxy; }
    public String getSource() { return source; }
    public String getHttpsSource() { return httpsSource; }
    public String getNoProxySource() { return noProxySource; }
    public String getHttpVariable() { return httpVariable; }
    public String getHttpsVariable() { return httpsVariable; }
    public String getNoProxyVariable() { return noProxyVariable; }
    public String getActiveUrl() { return activeUrl; }
    public String getActiveHttpsUrl() { return activeHttpsUrl; }
    public String getActiveNoProxy() { return activeNoProxy; }
    public boolean isRestartRequired() { return restartRequired; }
    public boolean isWritable() { return writable; }
}
