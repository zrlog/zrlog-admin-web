package com.zrlog.admin.util;

import com.hibegin.http.server.util.NativeImageUtils;
import java.util.*;

public final class AssetsNativeImageUtils {
    private AssetsNativeImageUtils() { }
    public static void reg() {
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(
                com.zrlog.admin.business.rest.request.ReplaceArticleResourceUrlRequest.class,
                com.zrlog.admin.business.rest.response.UploadFileResponse.class,
                com.zrlog.admin.plugin.rest.response.UploadServiceResponseEntity.class,
                com.zrlog.admin.business.type.FileEntryAccess.class,
                com.zrlog.admin.business.type.FileEntryAction.class,
                com.zrlog.admin.business.type.FileDirectoryAction.class,
                com.zrlog.admin.business.rest.response.FileEntryVO.class,
                com.zrlog.admin.business.rest.response.FileReferenceVO.class,
                com.zrlog.admin.business.rest.response.FileReferenceIndexCacheVO.class,
                com.zrlog.admin.business.rest.response.FileManagerResponse.class,
                com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse.class));
    }
}
