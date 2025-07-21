package com.zrlog.admin.web.interceptor;

import com.hibegin.common.util.FileUtils;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.MimeTypeUtil;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.common.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * 对 pwa 需要资源和，数据进行特殊转化和处理
 */
public class PwaInterceptor implements HandleAbleInterceptor {
    @Override
    public boolean isHandleAble(HttpRequest request) {
        return Arrays.asList(AdminConstants.FAVICON_ICO_URI_PATH, AdminConstants.FAVICON_PNG_PWA_192_URI_PATH, AdminConstants.FAVICON_PNG_PWA_512_URI_PATH).contains(request.getUri());
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws IOException {
        File file = PathUtil.getStaticFile(request.getUri());
        //优先使用外部配置的
        if (file.exists()) {
            response.write(new FileInputStream(file), 200);
            return false;
        }
        try (InputStream resourceAsStream = PwaInterceptor.class.getResourceAsStream(request.getUri())) {
            response.addHeader("Content-Type", MimeTypeUtil.getMimeStrByExt(FileUtils.getFileExt(request.getUri())));
            if (Objects.isNull(resourceAsStream)) {
                response.write(PwaInterceptor.class.getResourceAsStream(AdminConstants.FAVICON_PNG_PWA_192_URI_PATH), 200);
                return false;
            }
            response.write(resourceAsStream, 200);
            return false;
        }
    }
}
