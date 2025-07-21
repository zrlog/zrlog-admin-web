package com.zrlog.admin.web.plugin;

import com.hibegin.common.BaseLockObject;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.FileUtils;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.ApplicationContext;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.dto.FaviconBase64DTO;
import com.zrlog.model.WebSite;
import com.zrlog.util.ThreadUtils;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class AdminStaticResourcePlugin extends BaseLockObject implements StaticSitePlugin {

    private static final Logger LOGGER = LoggerUtil.getLogger(AdminStaticResourcePlugin.class);

    private final AdminResource adminResource;

    private static final String ID_CACHE_KEY = "admin_static_version";

    private final Map<String, StaticSitePlugin.HandleState> handleStatusPageMap = new ConcurrentHashMap<>();
    private final String contextPath;
    private final ZrLogConfig zrLogConfig;
    private final ApplicationContext applicationContext;
    private final ReentrantLock parseLock = new ReentrantLock();
    private final ExecutorService executorService = ThreadUtils.newFixedThreadPool(20);
    private final List<File> cacheFiles = new CopyOnWriteArrayList<>();


    public AdminStaticResourcePlugin(ZrLogConfig zrLogConfig, AdminResource adminResource, String contextPath) {
        this.adminResource = adminResource;
        this.contextPath = contextPath;
        this.zrLogConfig = zrLogConfig;
        this.applicationContext = new ApplicationContext(zrLogConfig.getServerConfig());
        this.applicationContext.init();
        File cacheFolder = getCacheFile("/admin");
        if (cacheFolder.exists()) {
            FileUtils.deleteFile(cacheFolder.toString());
        }
    }

    @Override
    public boolean autoStart() {
        return !StaticSitePlugin.isDisabled() && !EnvKit.isFaaSMode();
    }

    @Override
    public boolean start() {
        cacheFiles.clear();
        lock.lock();
        try {
            //do fetch
            doGenerator();
        } finally {
            lock.unlock();
        }
        return true;
    }

    @Override
    public boolean isStarted() {
        return lock.isLocked();
    }

    @Override
    public boolean stop() {
        return false;
    }

    private void copyFavicon() {
        FaviconBase64DTO faviconBase64DTO = new WebSite().faviconBase64DTO();
        faviconHandle(faviconBase64DTO.getFavicon_png_pwa_192_base64(), AdminConstants.FAVICON_PNG_PWA_192_URI_PATH, ResultValueConvertUtils.toBoolean(faviconBase64DTO.getGenerator_html_status()));
        faviconHandle(faviconBase64DTO.getFavicon_png_pwa_512_base64(), AdminConstants.FAVICON_PNG_PWA_512_URI_PATH, ResultValueConvertUtils.toBoolean(faviconBase64DTO.getGenerator_html_status()));
    }

    private void doGenerator() {
        if (StaticSitePlugin.isDisabled()) {
            return;
        }
        copyFavicon();
        handleStatusPageMap.clear();
        //从首页开始查找
        AdminConstants.adminResource.getAdminStaticCacheUris().forEach(uri -> {
            handleStatusPageMap.put(uri, StaticSitePlugin.HandleState.NEW);
        });
        //admin resource
        adminResource.getAdminStaticResourceUris().forEach(uri -> {
            handleStatusPageMap.put(uri, StaticSitePlugin.HandleState.NEW);
        });
        doFetch(zrLogConfig, applicationContext);
    }

    @Override
    public String getVersionFileName() {
        return "admin-version.txt";
    }

    @Override
    public String getSiteVersion() {
        return adminResource.getStaticResourceBuildId();
    }

    @Override
    public String getDbCacheKey() {
        return ID_CACHE_KEY;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getDefaultLang() {
        return "zh_CN";
    }

    @Override
    public Map<String, HandleState> getHandleStatusPageMap() {
        return handleStatusPageMap;
    }

    @Override
    public Lock getParseLock() {
        return parseLock;
    }

    @Override
    public Executor getExecutorService() {
        return executorService;
    }

    @Override
    public List<File> getCacheFiles() {
        return cacheFiles;
    }

    @Override
    public StaticSiteType getType() {
        return StaticSiteType.ADMIN;
    }
}
