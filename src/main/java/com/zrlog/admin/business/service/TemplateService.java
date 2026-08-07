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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public class TemplateService {

    private static final Logger LOGGER = LoggerUtil.getLogger(TemplateController.class);
    private static final int MAX_TEMPLATE_FILE_COUNT = 5000;
    private static final long MAX_TEMPLATE_UNCOMPRESSED_SIZE = 100L * 1024 * 1024;
    private static final int COPY_BUFFER_SIZE = 16 * 1024;
    private static final String SHORT_TEMPLATE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";

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
        updateRecordResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.template.update.success"));
        return updateRecordResponse;
    }

    public UploadTemplateResponse upload(String shortTemplate, boolean overwrite, File file) throws IOException {
        if (StringUtils.isEmpty(shortTemplate) || !shortTemplate.matches(SHORT_TEMPLATE_PATTERN)) {
            return uploadError("admin.template.upload.error.invalidName");
        }
        if (Objects.isNull(file) || !file.isFile()) {
            return uploadError("admin.template.upload.error.invalidPackage");
        }

        String templatePath = Constants.TEMPLATE_BASE_PATH + shortTemplate;
        if (TemplateInfoHelper.isDefaultTemplate(templatePath)) {
            return uploadError("admin.template.upload.error.builtIn");
        }

        Path templateRoot = PathUtil.getStaticFile(Constants.TEMPLATE_BASE_PATH).toPath();
        Files.createDirectories(templateRoot);
        Path target = templateRoot.resolve(shortTemplate).normalize();
        if (!target.getParent().equals(templateRoot)) {
            return uploadError("admin.template.upload.error.invalidName");
        }
        if (exists(target) && !overwrite) {
            return uploadError("admin.template.upload.error.alreadyExists");
        }

        Path staging = Files.createTempDirectory(templateRoot, ".upload-");
        Path backup = null;
        boolean installed = false;
        try {
            extractTemplatePackage(file.toPath(), staging);
            Path contentRoot = locateTemplateContentRoot(staging);
            TemplateVO templateVO = TemplateInfoHelper.getTemplateVO(contentRoot.toFile());
            if (Objects.isNull(templateVO) || Objects.equals(templateVO.getViewType(), ".xx")) {
                return uploadError("admin.template.upload.error.invalidPackage");
            }

            if (exists(target) && !overwrite) {
                return uploadError("admin.template.upload.error.alreadyExists");
            }
            boolean overwritten = exists(target);
            if (overwritten) {
                backup = templateRoot.resolve(".backup-" + shortTemplate + "-" + UUID.randomUUID());
                movePath(target, backup);
            }
            try {
                movePath(contentRoot, target);
                installed = true;
            } catch (IOException installError) {
                restoreBackup(target, backup, installError);
                throw installError;
            }

            String displayName = StringUtils.isEmpty(templateVO.getName()) ? shortTemplate : templateVO.getName();
            return UploadTemplateResponse.success(shortTemplate, displayName, templateVO.getVersion(), overwritten,
                    I18nUtil.getAdminBackendStringFromRes("admin.template.upload.success"));
        } catch (InvalidTemplatePackageException e) {
            return uploadError(e.getI18nKey());
        } finally {
            cleanPathQuietly(staging);
            if (installed) {
                cleanPathQuietly(backup);
            }
        }
    }

    private UploadTemplateResponse uploadError(String i18nKey) {
        return UploadTemplateResponse.error(I18nUtil.getAdminBackendStringFromRes(i18nKey));
    }

    private void extractTemplatePackage(Path zipFile, Path staging) throws IOException {
        int entryCount = 0;
        long totalSize = 0;
        Set<Path> extractedPaths = new HashSet<>();
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (InputStream inputStream = Files.newInputStream(zipFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_TEMPLATE_FILE_COUNT) {
                    throw new InvalidTemplatePackageException("admin.template.upload.error.tooManyFiles");
                }
                Path relativePath = safeZipEntryPath(entry.getName());
                Path outputPath = staging.resolve(relativePath).normalize();
                if (!outputPath.startsWith(staging)) {
                    throw new InvalidTemplatePackageException("admin.template.upload.error.invalidPackage");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                    continue;
                }
                if (!extractedPaths.add(relativePath)) {
                    throw new InvalidTemplatePackageException("admin.template.upload.error.invalidPackage");
                }
                Path parent = outputPath.getParent();
                if (Objects.nonNull(parent)) {
                    Files.createDirectories(parent);
                }
                try (OutputStream outputStream = Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW)) {
                    int length;
                    while ((length = zipInputStream.read(buffer)) != -1) {
                        totalSize += length;
                        if (totalSize > MAX_TEMPLATE_UNCOMPRESSED_SIZE) {
                            throw new InvalidTemplatePackageException("admin.template.upload.error.tooLarge");
                        }
                        outputStream.write(buffer, 0, length);
                    }
                }
            }
        } catch (ZipException e) {
            throw new InvalidTemplatePackageException("admin.template.upload.error.invalidPackage", e);
        }
        if (entryCount == 0) {
            throw new InvalidTemplatePackageException("admin.template.upload.error.invalidPackage");
        }
    }

    private Path safeZipEntryPath(String entryName) throws InvalidTemplatePackageException {
        if (StringUtils.isEmpty(entryName) || entryName.contains("\\")) {
            throw new InvalidTemplatePackageException("admin.template.upload.error.invalidPackage");
        }
        Path path;
        try {
            path = Path.of(entryName).normalize();
        } catch (RuntimeException e) {
            throw new InvalidTemplatePackageException("admin.template.upload.error.invalidPackage", e);
        }
        if (path.isAbsolute() || path.toString().isEmpty() || path.getNameCount() == 0 || path.startsWith("..")) {
            throw new InvalidTemplatePackageException("admin.template.upload.error.invalidPackage");
        }
        return path;
    }

    private Path locateTemplateContentRoot(Path staging) throws IOException {
        if (hasTemplateMetadata(staging)) {
            return staging;
        }
        try (Stream<Path> stream = Files.list(staging)) {
            List<Path> candidates = stream
                    .filter(path -> !Objects.equals(path.getFileName().toString(), "__MACOSX"))
                    .filter(path -> !Objects.equals(path.getFileName().toString(), ".DS_Store"))
                    .collect(java.util.stream.Collectors.toList());
            if (candidates.size() == 1 && Files.isDirectory(candidates.get(0))
                    && hasTemplateMetadata(candidates.get(0))) {
                return candidates.get(0);
            }
        }
        throw new InvalidTemplatePackageException("admin.template.upload.error.invalidPackage");
    }

    private boolean hasTemplateMetadata(Path directory) {
        return Files.isRegularFile(directory.resolve("template.properties"))
                || Files.isRegularFile(directory.resolve("package.json"))
                || Files.isRegularFile(directory.resolve("_config.yml"));
    }

    private boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private void movePath(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void restoreBackup(Path target, Path backup, IOException installError) {
        if (Objects.isNull(backup) || !exists(backup)) {
            return;
        }
        try {
            deleteRecursively(target);
            movePath(backup, target);
        } catch (IOException rollbackError) {
            installError.addSuppressed(rollbackError);
        }
    }

    private void cleanPathQuietly(Path path) {
        if (Objects.isNull(path)) {
            return;
        }
        try {
            deleteRecursively(path);
        } catch (IOException e) {
            LOGGER.warning("Clean template upload path failed: " + path + ", " + e.getMessage());
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            try {
                stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                    try {
                        Files.deleteIfExists(item);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
    }

    private static class InvalidTemplatePackageException extends IOException {

        private final String i18nKey;

        private InvalidTemplatePackageException(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        private InvalidTemplatePackageException(String i18nKey, Throwable cause) {
            super(cause);
            this.i18nKey = i18nKey;
        }

        private String getI18nKey() {
            return i18nKey;
        }
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
