package com.zrlog.admin.business.rest.response;

import com.zrlog.common.rest.response.ApiStandardResponse;

public class UploadTemplateResponse extends ApiStandardResponse<UploadTemplateResponse.UploadTemplateData> {

    public static UploadTemplateResponse success(String shortTemplate, String name, String version,
                                                 boolean overwritten, String message) {
        UploadTemplateResponse response = new UploadTemplateResponse();
        response.setData(new UploadTemplateData(shortTemplate, name, version, overwritten));
        response.setMessage(message);
        return response;
    }

    public static UploadTemplateResponse error(String message) {
        UploadTemplateResponse response = new UploadTemplateResponse();
        response.setError(1);
        response.setMessage(message);
        return response;
    }

    public static class UploadTemplateData {

        private final String shortTemplate;
        private final String name;
        private final String version;
        private final boolean overwritten;

        public UploadTemplateData(String shortTemplate, String name, String version, boolean overwritten) {
            this.shortTemplate = shortTemplate;
            this.name = name;
            this.version = version;
            this.overwritten = overwritten;
        }

        public String getShortTemplate() {
            return shortTemplate;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public boolean isOverwritten() {
            return overwritten;
        }
    }
}
