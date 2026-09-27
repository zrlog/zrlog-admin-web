package com.zrlog.admin.util;

import com.google.gson.Gson;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminManifestResponse;
import com.zrlog.admin.web.controller.page.AdminPageController;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.StaticFileCacheUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ManifestUtils {

    public static AdminManifestResponse manifest(HttpRequest request) throws IOException {
        try (InputStream inputStream = AdminPageController.class.getResourceAsStream(AdminConstants.ADMIN_PWA_MANIFEST_JSON)) {
            if (inputStream == null) {
                return new AdminManifestResponse();
            }
            AdminManifestResponse manifest = new Gson().fromJson(IOUtil.getStringInputStream(inputStream), AdminManifestResponse.class);
            PublicWebSiteInfo publicWebSiteInfo = AdminConstants.getPublicWebSiteInfo();
            if (StringUtils.isNotEmpty(publicWebSiteInfo.getTitle())) {
                manifest.setShort_name(publicWebSiteInfo.getTitle());
            }
            manifest.setName(AdminConstants.getAdminDocumentTitleByUri("/", publicWebSiteInfo));
            manifest.setTheme_color(publicWebSiteInfo.getAdmin_color_primary());
            manifest.setDescription(publicWebSiteInfo.getDescription());
            manifest.setId(publicWebSiteInfo.getAppId());
            manifest.setBackground_color(Objects.equals(publicWebSiteInfo.getAdmin_darkMode(), true) ? "#000000" : "#FFFFFF");
            List<AdminManifestResponse.Icon> list = getManifestIcons();
            for (AdminManifestResponse.Icon icon : list) {
                icon.setSrc(WebTools.buildEncodedUrl(request, icon.getSrc()));
            }
            manifest.setIcons(list);
            if (BaseStaticSitePlugin.isStaticPluginRequest(request) && StringUtils.isNotEmpty(manifest.getStart_url())) {
                manifest.setStart_url(manifest.getStart_url().replace("./index", "./index.html"));
            }
            return manifest;
        }
    }

    public static List<AdminManifestResponse.Icon> getManifestIcons() throws IOException {
        try (InputStream inputStream = AdminPageController.class.getResourceAsStream(AdminConstants.ADMIN_PWA_MANIFEST_JSON)) {
            if (inputStream == null) {
                return new ArrayList<>();
            }
            AdminManifestResponse manifest = new Gson().fromJson(IOUtil.getStringInputStream(inputStream), AdminManifestResponse.class);
            List<AdminManifestResponse.Icon> icons = manifest.getIcons();
            if (Objects.isNull(icons)) {
                return new ArrayList<>();
            }
            for (AdminManifestResponse.Icon icon : icons) {
                String src = AdminConstants.ADMIN_URI_BASE_PATH + icon.getSrc().replaceFirst("./", "/");
                File file = PathUtil.getStaticFile(src);
                InputStream srcIn;
                if (file.exists()) {
                    srcIn = new FileInputStream(file);
                } else {
                    srcIn = ManifestUtils.class.getResourceAsStream(src);
                }
                if (Objects.nonNull(srcIn)) {
                    try (srcIn) {
                        String fileTag = StaticFileCacheUtils.getInstance().getStreamTag(srcIn);
                        src += "?v=" + fileTag;
                        icon.setSrc(src);
                    }
                }
            }
            return icons;
        }
    }

}
