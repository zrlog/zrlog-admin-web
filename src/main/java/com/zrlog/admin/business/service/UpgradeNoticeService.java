package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.response.CheckVersionResponse;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.web.plugin.UpdateVersionInfoPlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.Version;
import com.zrlog.model.WebSite;
import com.zrlog.util.ZrLogUtil;

import java.util.Objects;
import java.util.logging.Logger;

public class UpgradeNoticeService {

    private static final Logger LOGGER = LoggerUtil.getLogger(UpgradeNoticeService.class);
    private static final String UPGRADE_NOTICE_KEY = "admin_message_center_upgrade_notice";
    private static final String VERSION_UPDATE_NOTICE_TYPE = "versionUpdate";
    private static final String VERSION_UPDATE_NOTICE_STATUS = "notice";

    private final Gson gson = new Gson();

    public void sync(Version version) {
        if (!Constants.zrLogConfig.isInstalled()) {
            return;
        }
        try {
            CheckVersionResponse response = buildResponse(version);
            if (!Boolean.TRUE.equals(response.getUpgrade()) || Objects.isNull(version)) {
                clearStoredUpgradeNotice();
                return;
            }
            StoredUpgradeNotice storedUpgradeNotice = new StoredUpgradeNotice();
            storedUpgradeNotice.setVersion(version);
            storedUpgradeNotice.setUpdatedAt(System.currentTimeMillis());
            new WebSite().updateByKV(UPGRADE_NOTICE_KEY, gson.toJson(storedUpgradeNotice));
        } catch (Exception e) {
            LOGGER.warning("Sync upgrade notice state error " + e.getMessage());
        }
    }

    public CheckVersionResponse getNotice() {
        try {
            StoredUpgradeNotice state = getStoredUpgradeNotice();
            if (Objects.isNull(state) || Objects.isNull(state.getVersion())) {
                return buildResponse(null);
            }
            return buildResponse(state.getVersion());
        } catch (Exception e) {
            LOGGER.warning("Read upgrade notice state error " + e.getMessage());
            return buildResponse(null);
        }
    }

    public MessageCenterNoticeResponse getMessageCenterNotice() {
        try {
            StoredUpgradeNotice storedUpgradeNotice = getStoredUpgradeNotice();
            if (Objects.isNull(storedUpgradeNotice) || Objects.isNull(storedUpgradeNotice.getVersion())) {
                return null;
            }
            CheckVersionResponse checkVersionResponse = buildResponse(storedUpgradeNotice.getVersion());
            if (!Boolean.TRUE.equals(checkVersionResponse.getUpgrade()) || Objects.isNull(checkVersionResponse.getVersion())) {
                clearStoredUpgradeNotice();
                return null;
            }
            MessageCenterNoticeResponse response = new MessageCenterNoticeResponse();
            response.setTaskKey("server.system.version-update");
            response.setType(VERSION_UPDATE_NOTICE_TYPE);
            response.setStatus(VERSION_UPDATE_NOTICE_STATUS);
            response.setUpdatedAt(Objects.requireNonNullElse(storedUpgradeNotice.getUpdatedAt(), System.currentTimeMillis()));
            response.setVersion(checkVersionResponse.getVersion());
            return response;
        } catch (Exception e) {
            LOGGER.warning("Build message center notice error " + e.getMessage());
            return null;
        }
    }

    private CheckVersionResponse buildResponse(Version version) {
        CheckVersionResponse checkVersionResponse = new CheckVersionResponse();
        if (Objects.isNull(version) || Objects.isNull(version.getBuildDate())) {
            checkVersionResponse.setUpgrade(false);
            return checkVersionResponse;
        }
        checkVersionResponse.setUpgrade(ZrLogUtil.greatThenCurrentVersion(
                version.getBuildId(),
                version.getBuildDate(),
                version.getVersion()
        ));
        checkVersionResponse.setVersion(UpdateVersionInfoPlugin.normalizeVersionForDisplay(version));
        return checkVersionResponse;
    }

    private StoredUpgradeNotice getStoredUpgradeNotice() {
        String json = new WebSite().getStringValueByName(UPGRADE_NOTICE_KEY);
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        return gson.fromJson(json, StoredUpgradeNotice.class);
    }

    private void clearStoredUpgradeNotice() {
        try {
            new WebSite().updateByKV(UPGRADE_NOTICE_KEY, "");
        } catch (Exception e) {
            LOGGER.warning("Clear upgrade notice state error " + e.getMessage());
        }
    }

    private static class StoredUpgradeNotice {
        private Version version;
        private Long updatedAt;

        public Version getVersion() {
            return version;
        }

        public void setVersion(Version version) {
            this.version = version;
        }

        public Long getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(Long updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
