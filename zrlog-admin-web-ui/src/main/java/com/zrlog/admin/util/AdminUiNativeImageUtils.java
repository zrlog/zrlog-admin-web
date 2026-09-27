package com.zrlog.admin.util;

import com.hibegin.http.server.util.NativeImageUtils;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.business.rest.response.*;
import java.util.ArrayList;
import java.util.List;

public final class AdminUiNativeImageUtils {
    private AdminUiNativeImageUtils() { }

    public static List<String> getResources(AdminResource resources) {
        List<String> uris = new ArrayList<>(resources.getAdminStaticResourceUris());
        uris.addAll(List.of(AdminConstants.ADMIN_HTML_PAGE, AdminConstants.ADMIN_PWA_MANIFEST_JSON,
                AdminConstants.ADMIN_SERVICE_WORKER_JS, AdminResource.ADMIN_ASSET_MANIFEST_JSON,
                AdminConstants.FAVICON_PNG_PWA_192_URI_PATH, AdminConstants.FAVICON_PNG_PWA_512_URI_PATH));
        return uris;
    }

    public static void reg(AdminResource resources) {
        NativeImageUtils.doResourceLoadByResourceNames(getResources(resources));
        NativeImageUtils.gsonNativeAgentByClazz(List.of(AdminManifestResponse.class, AdminManifestResponse.Icon.class,
                ServerSideDataResponse.class, AdminStaticSiteSyncResponse.class));
    }
}
