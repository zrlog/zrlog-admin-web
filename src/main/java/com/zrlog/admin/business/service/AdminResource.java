package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;

import java.io.InputStream;
import java.util.Set;

public interface AdminResource  {

    String ADMIN_ASSET_MANIFEST_JSON = AdminConstants.ADMIN_URI_BASE_PATH + "/asset-manifest.json";

    Set<String> getAdminStaticResourceUris();

    Set<String> getAdminPageUris();

    Set<String> getAdminStaticCacheUris();

    Set<String> getAdminCacheableApiUris();

    InputStream renderServiceWorker(HttpRequest request);

    String getStaticResourceBuildId();

    AdminResourceInfoResponse adminResourceInfo(HttpRequest request);
}
