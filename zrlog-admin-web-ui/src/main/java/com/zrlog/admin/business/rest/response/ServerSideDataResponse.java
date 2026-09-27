package com.zrlog.admin.business.rest.response;

public class ServerSideDataResponse<T> extends AdminPageDataResponse<T> {

    private final UserInfoResponse user;
    private final AdminResourceInfoResponse resourceInfo;
    private final String key;

    public ServerSideDataResponse(UserInfoResponse user, AdminResourceInfoResponse resourceInfo, T pageData, String key, String documentTitle) {
        super(pageData, "");
        this.user = user;
        this.resourceInfo = resourceInfo;
        this.key = key;
        this.documentTitle = documentTitle;
    }

    public UserInfoResponse getUser() {
        return user;
    }

    public AdminResourceInfoResponse getResourceInfo() {
        return resourceInfo;
    }

    public String getKey() {
        return key;
    }

}
