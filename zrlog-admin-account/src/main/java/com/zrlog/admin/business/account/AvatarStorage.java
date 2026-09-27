package com.zrlog.admin.business.account;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.web.WebSetup;

/** Upload an account avatar through the enabled asset module. */
public interface AvatarStorage extends WebSetup {
    String saveAvatar(byte[] bytes, String extension, HttpRequest request);
}
