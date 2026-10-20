package com.zrlog.admin.web.plugin;

import com.hibegin.common.BaseLockObject;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.FileUtils;
import com.hibegin.common.util.LoggerUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.StaticSiteRenderer;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.dto.FaviconBase64DTO;
import com.zrlog.model.WebSite;
import com.zrlog.util.ThreadUtils;
import jakarta.inject.Inject;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class AdminStaticResourcePlugin extends BaseLockObject implements StaticSitePlugin {
    private static final Logger LOGGER = LoggerUtil.getLogger(AdminStaticResourcePlugin.class);
    private static final String ID_CACHE_KEY = "admin_static_version";
    private final jakarta.inject.Provider<WebSite> webSiteProvider;
    private final AdminResource adminResource;
    private final Map<String, StaticSitePlugin.HandleState> handleStatusPageMap = new ConcurrentHashMap<>();
    private final String contextPath;
    private final StaticSiteRenderer renderer;
    private final Supplier<PublicWebSiteInfo> site;
    private final ReentrantLock parseLock = new ReentrantLock();
    private final ExecutorService executorService = ThreadUtils.newFixedThreadPool(20);
    private final List<File> cacheFiles = new CopyOnWriteArrayList<>();

    @Inject
    public AdminStaticResourcePlugin(
            StaticSiteRenderer renderer,
            Supplier<PublicWebSiteInfo> site,
            AdminResource adminResource,
            String contextPath,
            jakarta.inject.Provider<WebSite> webSiteProvider) {
        this.webSiteProvider = webSiteProvider;

        this.adminResource = adminResource;
        this.contextPath = contextPath;
        this.renderer = Objects.requireNonNull(renderer);
        this.site = Objects.requireNonNull(site);
        File cacheFolder = getCacheFile(AdminConstants.ADMIN_URI_BASE_PATH);
        if (cacheFolder.exists()) {
            FileUtils.deleteFile(cacheFolder.toString());
        }
    }

    @Override
    public PublicWebSiteInfo getSiteInfo() {
        return site.get();
    }

    @Override
    public boolean autoStart() {
        return !isDisabled() && !EnvKit.isFaaSMode();
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
        FaviconBase64DTO faviconBase64DTO = this.webSiteProvider.get().faviconBase64DTO();
        faviconHandle(faviconBase64DTO.getFavicon_png_pwa_192_base64(), AdminConstants.FAVICON_PNG_PWA_192_URI_PATH, ResultValueConvertUtils.toBoolean(faviconBase64DTO.getGenerator_html_status()));
        faviconHandle(faviconBase64DTO.getFavicon_png_pwa_512_base64(), AdminConstants.FAVICON_PNG_PWA_512_URI_PATH, ResultValueConvertUtils.toBoolean(faviconBase64DTO.getGenerator_html_status()));
    }

    private void doGenerator() {
        if (isDisabled()) {
            return;
        }
        copyFavicon();
        handleStatusPageMap.clear();
        //从首页开始查找
        adminResource.getAdminStaticCacheUris().forEach(uri -> {
            handleStatusPageMap.put(uri, StaticSitePlugin.HandleState.NEW);
        });
        adminResource.getAdminStaticResourceUris().forEach(uri -> {
            handleStatusPageMap.put(uri, StaticSitePlugin.HandleState.NEW);
        });
        doFetch(renderer);
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
