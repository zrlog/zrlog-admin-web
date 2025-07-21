package com.zrlog.admin.util;

import com.google.gson.Gson;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.web.controller.page.AdminPageController;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.StaticFileCacheUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ManifestUtils {

    public static Map<String, Object> manifest(HttpRequest request) throws IOException {
        try (InputStream inputStream = AdminPageController.class.getResourceAsStream(AdminConstants.ADMIN_PWA_MANIFEST_JSON)) {
            if (inputStream == null) {
                return new HashMap<>();
            }
            Map map = new Gson().fromJson(IOUtil.getStringInputStream(inputStream), Map.class);
            PublicWebSiteInfo publicWebSiteInfo = AdminConstants.getPublicWebSiteInfo();
            if (StringUtils.isNotEmpty(publicWebSiteInfo.getTitle())) {
                map.put("short_name", publicWebSiteInfo.getTitle());
            }
            map.put("name", AdminConstants.getAdminDocumentTitleByUri("/", publicWebSiteInfo));
            map.put("theme_color", publicWebSiteInfo.getAdmin_color_primary());
            map.put("description", publicWebSiteInfo.getDescription());
            map.put("id", publicWebSiteInfo.getAppId());
            map.put("background_color", Objects.equals(publicWebSiteInfo.getAdmin_darkMode(), true) ? "#000000" : "#FFFFFF");
            List<Map<String, Object>> list = getManifestIcons();
            for (Map<String, Object> icon : list) {
                icon.put("src", WebTools.buildEncodedUrl(request, (String) icon.get("src")));
            }
            map.put("icons", list);
            if (BaseStaticSitePlugin.isStaticPluginRequest(request)) {
                map.put("start_url", ((String) map.get("start_url")).replace("./index", "./index.html"));
            }
            return map;
        }
    }

    public static List<Map<String, Object>> getManifestIcons() throws IOException {
        try (InputStream inputStream = AdminPageController.class.getResourceAsStream(AdminConstants.ADMIN_PWA_MANIFEST_JSON)) {
            if (inputStream == null) {
                return new ArrayList<>();
            }
            Map map = new Gson().fromJson(IOUtil.getStringInputStream(inputStream), Map.class);
            List<Map<String, Object>> icons = (List<Map<String, Object>>) map.get("icons");
            for (Map<String, Object> icon : icons) {
                String src = "/admin" + ((String) icon.get("src")).replaceFirst("./", "/");
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
                        icon.put("src", src);
                    }
                }
            }
            return icons;
        }
    }

    public static void main(String[] args) throws IOException {
        List<Map<String, Object>> manifestIcons = getManifestIcons();
        System.out.println(manifestIcons);
    }
}
