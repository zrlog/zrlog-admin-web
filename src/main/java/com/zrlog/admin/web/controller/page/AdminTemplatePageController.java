package com.zrlog.admin.web.controller.page;

import com.hibegin.common.util.FileUtils;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.server.util.MimeTypeUtil;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.service.TemplateService;
import com.zrlog.admin.util.AdminTemplateUtils;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.business.template.util.TemplateDownloadUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.util.ZrLogUtil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

public class AdminTemplatePageController extends BaseController {


    @RequestMethod
    public void download() throws IOException, URISyntaxException, InterruptedException {
        String downloadUrl = request.getParaToStr("downloadUrl", "");
        TemplateDownloadUtils.installByUrl(downloadUrl);
        response.redirect(Constants.ADMIN_URI_BASE_PATH + "/website/template");
    }

    @RequestMethod
    public void previewImage() {
        String templateName = AdminTemplateUtils.loadTemplatePathByRequestInfo(this);
        TemplateVO templateVO = new TemplateService().loadTemplateConfig(templateName);
        if (Objects.isNull(templateVO)) {
            response.renderCode(404);
            return;
        }
        if (!templateVO.getPreviewImage().startsWith(Constants.TEMPLATE_BASE_PATH)) {
            response.renderCode(403);
            return;
        }
        ZrLogUtil.putLongTimeCache(response);
        response.addHeader("Content-Type", MimeTypeUtil.getMimeStrByExt(FileUtils.getFileExt(templateVO.getPreviewImage())));
        if (TemplateInfoHelper.isDefaultTemplate(templateVO.getTemplate())) {
            response.write(AdminTemplatePageController.class.getResourceAsStream(templateVO.getPreviewImage()));
            return;
        }
        response.writeFile(PathUtil.getStaticFile(templateVO.getPreviewImage()));
    }


}
