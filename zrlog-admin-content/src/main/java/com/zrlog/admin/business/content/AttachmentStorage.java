package com.zrlog.admin.business.content;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.common.Constants;
import com.zrlog.web.WebSetup;

/** Optional attachment storage supplied by the enabled assets module. */
public interface AttachmentStorage extends WebSetup {
    String saveAttachment(byte[] bytes, String filename, HttpRequest request) throws Exception;

    static AttachmentStorage current() {
        return Constants.zrLogConfig == null ? null : Constants.zrLogConfig.getWebSetup(AttachmentStorage.class);
    }
}
