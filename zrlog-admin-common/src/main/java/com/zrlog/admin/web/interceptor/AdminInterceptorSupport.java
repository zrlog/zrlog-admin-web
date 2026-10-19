package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.AdminFullTokenVO;

import java.util.Objects;

final class AdminInterceptorSupport {

    private AdminInterceptorSupport() {
    }

    static AdminFullTokenVO getAdminToken(TokenService tokenService, HttpRequest request) {
        if (Objects.isNull(tokenService)) {
            return null;
        }
        return tokenService.getAdminTokenVO(request);
    }

}
