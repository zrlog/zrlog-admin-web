package com.zrlog.admin.business.service;

import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.rest.response.TemplateEntryResponse;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.business.type.TemplateType;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.BaseTemplateVO;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.theme.spi.BundledThemes;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** Discovers installed themes without downloading or extracting packages. */
public final class TemplateCatalog {
    @Inject
    public TemplateCatalog() {
    }

    List<File> files() {
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

    public List<TemplateEntryResponse> list(String currentTemplate, String previewTemplate) throws IOException {
        List<BaseTemplateVO> templates = new ArrayList<>(TemplateInfoHelper.getClassPathTemplates());
        for (File file : files()) {
            if (file.isDirectory() && !file.isHidden()) {
                TemplateVO templateVO = TemplateInfoHelper.getTemplateVO(file);
                if (Objects.isNull(templateVO)) {
                    continue;
                }
                templateVO.setDeleteAble(true);
                templateVO.setConfigAble(!templateVO.getConfig().isEmpty());
                templates.add(BeanUtil.convert(templateVO, BaseTemplateVO.class));
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
        return templates.stream().map(template -> {
            TemplateEntryResponse entry = BeanUtil.convert(template, TemplateEntryResponse.class);
            // The SPI registry defines built-in themes independently of their loading mechanism.
            entry.setBuiltIn(BundledThemes.getInstance().contains(template.getTemplate()));
            return entry;
        }).collect(Collectors.toList());
    }

}
