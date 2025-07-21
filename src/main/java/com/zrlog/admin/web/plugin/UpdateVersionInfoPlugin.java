package com.zrlog.admin.web.plugin;

import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.http.HttpUtil;
import com.zrlog.admin.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.admin.web.type.AutoUpgradeVersionType;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.Version;
import com.zrlog.plugin.IPlugin;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ThreadUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于插件的实现，使用定时器，定时检查是否有新的版本发布。
 */
public class UpdateVersionInfoPlugin implements IPlugin {

    private static final Logger LOGGER = LoggerUtil.getLogger(UpdateVersionInfoPlugin.class);

    private ScheduledExecutorService scheduledExecutorService;

    private UpdateVersionTimerTask updateVersionTimerTask;

    private static boolean isPreviewVersion(Version version) {
        return Objects.nonNull(version) && StringUtils.isNotEmpty(version.getVersion())
                && version.getVersion().contains("-SNAPSHOT");
    }

    public static Version normalizeVersionForDisplay(Version version) {
        if (Objects.isNull(version)) {
            return null;
        }
        Version copyVersion = BeanUtil.convert(version,Version.class);
        copyVersion.setType(I18nUtil.getAdminBackendStringFromRes(
                isPreviewVersion(version) ? "versionType.preview" : "versionType.release"));
        //不在页面展示SNAPSHOT
        copyVersion.setVersion(version.getVersion().replaceAll("-SNAPSHOT", ""));
        return copyVersion;
    }

    @Override
    public boolean autoStart() {
        return !EnvKit.isFaaSMode();
    }

    public static String getCurrentChangeLog(Map<String, Object> res) {
        String version = BlogBuildInfoUtil.getVersion();
        try {
            String changeLogMd = HttpUtil.getInstance().getSuccessTextByUrl("https://www.zrlog.com/changelog/" +
                    version + "-" + BlogBuildInfoUtil.getBuildId() + ".md?lang=" +
                    I18nUtil.getCurrentLocale() + "&v=" + BlogBuildInfoUtil.getBuildId());
            if (StringUtils.isNotEmpty(changeLogMd) && !UpdateVersionTimerTask.isHtml(changeLogMd)) {
                return changeLogMd;
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        String uriPath = "94fzb/zrlog/commits/" + BlogBuildInfoUtil.getBuildId();
        String changeUrl = "https://github.com/" + uriPath;
        return (res.get("upgradeNoChangeLog") + "\n[" + uriPath + "](" + changeUrl + ")").replace("diff", "commit");
    }

    private void initExecutorService() {
        scheduledExecutorService = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = ThreadUtils.unstarted(r);
            thread.setName("update-version-plugin-thread");
            return thread;
        });
    }

    @Override
    public boolean start() {
        if (isStarted()) {
            return true;
        }
        UpgradeWebSiteInfo upgradeWebSiteInfo = new WebSiteService().upgradeWebSiteInfo();
        AutoUpgradeVersionType autoUpgradeVersionType = AutoUpgradeVersionType.cycle(upgradeWebSiteInfo.getAutoUpgradeVersion());
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
        //开启了定时检查，定时器开始工作
        if (autoUpgradeVersionType != AutoUpgradeVersionType.NEVER) {
            initExecutorService();
            updateVersionTimerTask = new UpdateVersionTimerTask(upgradeWebSiteInfo.getUpgradePreview(), Constants.getLanguage());
            scheduledExecutorService.scheduleAtFixedRate(updateVersionTimerTask, 0, autoUpgradeVersionType.getCycle(), TimeUnit.SECONDS);
        }
        LOGGER.info("UpdateVersionPlugin start autoUpgradeVersionType " + autoUpgradeVersionType);
        return true;
    }

    @Override
    public boolean isStarted() {
        return Objects.nonNull(scheduledExecutorService);
    }

    @Override
    public boolean stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
        return true;
    }

    /**
     * 获取最新的版本信息。
     *
     * @param fetch 是否发起新的请求
     * @return 版本信息
     */
    public Version getLastVersion(boolean fetch) {
        if (updateVersionTimerTask == null) {
            UpgradeWebSiteInfo upgradeWebSiteInfo = new WebSiteService().upgradeWebSiteInfo();
            updateVersionTimerTask = new UpdateVersionTimerTask(upgradeWebSiteInfo.getUpgradePreview(), Constants.getLanguage());
        }
        if (fetch) {
            try {
                updateVersionTimerTask.run();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
        return normalizeVersionForDisplay(updateVersionTimerTask.getVersion());
    }
}
