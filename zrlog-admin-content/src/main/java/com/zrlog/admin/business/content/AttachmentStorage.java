package com.zrlog.admin.business.content;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.web.WebSetup;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;

/** Optional attachment storage supplied by the enabled assets module. */
public interface AttachmentStorage extends WebSetup {
    String saveAttachment(AccountAccess actor, byte[] bytes, String filename, HttpRequest request, AdminTokenVO credential) throws Exception;

}
