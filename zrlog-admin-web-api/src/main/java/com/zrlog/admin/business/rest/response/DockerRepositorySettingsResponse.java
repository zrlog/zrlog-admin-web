package com.zrlog.admin.business.rest.response;

import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;

/** Internal admin response; error text is localized by the UI. */
public class DockerRepositorySettingsResponse extends ApiStandardResponse<DockerUpdaterProtocol.SettingsResponse> {
    private String errorCode;

    public DockerRepositorySettingsResponse(DockerUpdaterProtocol.SettingsResponse settings) {
        super(settings);
    }

    public static DockerRepositorySettingsResponse failed(String code) {
        DockerRepositorySettingsResponse response = new DockerRepositorySettingsResponse(null);
        response.setError(1);
        response.errorCode = code;
        return response;
    }

    public String getErrorCode() { return errorCode; }
}
