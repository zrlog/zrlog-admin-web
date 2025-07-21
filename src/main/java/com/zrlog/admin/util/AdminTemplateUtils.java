package com.zrlog.admin.util;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.business.template.util.TemplateDownloadUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Logger;

public class AdminTemplateUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(AdminTemplateUtils.class);


    public static String loadTemplatePathByRequestInfo(BaseController controller) {
        String template = PathUtil.safeAppendFilePath(Constants.TEMPLATE_BASE_PATH, controller.getParamWithEmptyCheck("shortTemplate")).toString().replace("\\", "/");
        if (TemplateInfoHelper.isDefaultTemplate(template)) {
            return template;
        }
        try {
            TemplateDownloadUtils.installByTemplateName(template, false);
        } catch (IOException | URISyntaxException | InterruptedException e) {
            LOGGER.warning("Download template failed " + e.getMessage());
            return template;
        }
        File file = PathUtil.getStaticFile(template);
        if (!file.exists()) {
            LOGGER.warning("Template not exists");
        }
        return template;
    }
}
