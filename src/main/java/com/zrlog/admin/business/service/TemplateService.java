package com.zrlog.admin.business.service;

import com.hibegin.common.util.*;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.rest.response.UploadTemplateResponse;
import com.zrlog.admin.web.controller.api.TemplateController;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.business.template.util.TemplateDownloadUtils;
import com.zrlog.business.type.TemplateType;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.BaseTemplateVO;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.model.WebSite;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

public class TemplateService {

    private static final Logger LOGGER = LoggerUtil.getLogger(TemplateController.class);

    private boolean isNeedClean(String contentType) {
        return !Objects.equals(contentType, "html") && !Objects.equals(contentType, "yml");
    }

    public UpdateRecordResponse save(String template, Map<String, Object> settingMap) throws SQLException, IOException {
        TemplateVO.TemplateConfigMap configMap = TemplateInfoHelper.loadTemplateVO(template).getConfig();
        for (Map.Entry<String, Object> entry : settingMap.entrySet()) {
            if (Objects.isNull(entry.getValue())) {
                continue;
            }
            String key = entry.getKey();
            TemplateVO.TemplateConfigVO templateConfigVO = configMap.get(key);
            if (Objects.isNull(templateConfigVO)) {
                continue;
            }
            if (Objects.equals("css", templateConfigVO.getContentType())) {
                settingMap.put(key, Jsoup.clean((String) entry.getValue(), Safelist.none().addTags("style")));
                continue;
            }
            //校验输入内容
            if (isNeedClean(templateConfigVO.getContentType())) {
                settingMap.put(key, Jsoup.clean((String) entry.getValue(), Safelist.none()));
            }
        }
        new WebSite().updateTemplateConfigMap(template, settingMap);
        UpdateRecordResponse updateRecordResponse = new UpdateRecordResponse();
        updateRecordResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("templateUpdateSuccess"));
        return updateRecordResponse;
    }

    public UploadTemplateResponse upload(String templateName, File file) throws IOException {
        String finalPath = PathUtil.getStaticFile(Constants.TEMPLATE_BASE_PATH).toString();
        String finalFile = finalPath + templateName;
        FileUtils.deleteFile(finalFile);
        //start extract template file
        FileUtils.moveOrCopyFile(file.toString(), finalFile, true);
        UploadTemplateResponse response = new UploadTemplateResponse();
        response.setMessage(I18nUtil.getAdminBackendStringFromRes("templateUploadSuccess"));
        String extractFolder = finalPath + templateName.replace(".zip", "");
        FileUtils.deleteFile(extractFolder);
        ZipUtil.unZip(finalFile, extractFolder);
        return response;
    }

    private static List<File> getAllTemplatesFiles() {
        List<File> list = new ArrayList<>();
        File[] templatesFile = PathUtil.getStaticFile(Constants.TEMPLATE_BASE_PATH).listFiles();
        if (Objects.nonNull(templatesFile)) {
            list.addAll(Arrays.asList(templatesFile));
        }
        if (EnvKit.isFaaSMode()) {
            File file = PathUtil.safeAppendFilePath(ZrLogUtil.getFaaSRoot() + "/static/", Constants.TEMPLATE_BASE_PATH);
            if (file.exists()) {
                File[] files = file.listFiles();
                if (Objects.nonNull(files)) {
                    list.addAll(Arrays.asList(files));
                }
            }
        }
        return list;
    }

    public List<BaseTemplateVO> getAllTemplates(String previewTemplate) throws IOException {
        String currentTemplate = AdminConstants.getPublicWebSiteInfo().getTemplate();
        if (!TemplateInfoHelper.isDefaultTemplate(currentTemplate)) {
            try {
                TemplateDownloadUtils.installByTemplateName(currentTemplate, false);
            } catch (IOException | URISyntaxException | InterruptedException e) {
                LOGGER.warning("Download template failed " + e.getMessage());
            }
        }
        List<BaseTemplateVO> templates = new ArrayList<>(TemplateInfoHelper.getClassPathTemplates());
        for (File file : getAllTemplatesFiles()) {
            if (file.isDirectory() && !file.isHidden()) {
                TemplateVO templateVO = TemplateInfoHelper.getTemplateVO(file);
                if (Objects.isNull(templateVO)) {
                    continue;
                }
                templateVO.setDeleteAble(true);
                templateVO.setConfigAble(!templateVO.getConfig().isEmpty());
                templates.add(BeanUtil.convert(templateVO, BaseTemplateVO.class));
            } else if (file.getName().endsWith(".zip")) {
                String templatePath = Constants.TEMPLATE_BASE_PATH + "/" + file.getName().replace(".zip", "");
                if (TemplateDownloadUtils.exists(templatePath)) {
                    continue;
                }
                TemplateDownloadUtils.installByZipFile(file, templatePath);
            }
        }
        for (BaseTemplateVO templateVO : templates) {
            if (templateVO.getTemplateType() == TemplateType.NODE_JS) {
                templateVO.setTags(Arrays.asList("polyglot", templateVO.getViewType().substring(1)));
            }

            //同时存在以使用为主
            if (templateVO.getTemplate().equals(currentTemplate)) {
                templateVO.setUse(true);
                templateVO.setDeleteAble(false);
                continue;
            }

            if (templateVO.getTemplate().equals(previewTemplate)) {
                templateVO.setPreview(true);
            }
        }
        return templates;
    }

    public TemplateVO loadTemplateConfig(String templateName) {
        TemplateVO templateVO = TemplateInfoHelper.loadTemplateVO(templateName);
        TemplateVO.TemplateConfigMap config = templateVO.getConfig();
        Map<String, Object> dbConfig = new WebSite().getTemplateConfigMap(templateName);
        config.forEach((key, value) -> {
            if (dbConfig.containsKey(key)) {
                value.setValue(dbConfig.get(key));
            }
        });
        templateVO.setConfig(config);
        //添加一个隐藏的表单域
        TemplateVO.TemplateConfigVO templateConfigVO = new TemplateVO.TemplateConfigVO();
        templateConfigVO.setHtmlElementType("input");
        templateConfigVO.setType("hidden");
        templateConfigVO.setValue(templateName);
        config.put("template", templateConfigVO);
        return templateVO;
    }


}
