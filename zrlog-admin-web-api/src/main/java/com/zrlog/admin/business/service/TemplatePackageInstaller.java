package com.zrlog.admin.business.service;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.rest.response.UploadTemplateResponse;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.business.template.util.TemplateDownloadUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.StaticFileCacheUtils;
import jakarta.inject.Inject;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** Owns theme package preparation, validated uploads and replacement recovery. */
public class TemplatePackageInstaller {
    private static final Logger LOGGER = Logger.getLogger(TemplatePackageInstaller.class.getName());
    private static final int MAX_TEMPLATE_FILE_COUNT = 5000;
    private static final long MAX_TEMPLATE_UNCOMPRESSED_SIZE = 100L * 1024 * 1024;
    private static final int COPY_BUFFER_SIZE = 16 * 1024;
    private static final String SHORT_TEMPLATE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";
    private final TemplateCatalog catalog;

    @Inject
    public TemplatePackageInstaller(TemplateCatalog catalog) {
        this.catalog = catalog;
    }

    /** Compatibility preparation invoked explicitly before opening theme management. */
    public void prepare(String currentTemplate) throws IOException {
        if (!TemplateInfoHelper.isDefaultTemplate(currentTemplate)) {
            try {
                TemplateDownloadUtils.installByTemplateName(currentTemplate, false);
            } catch (IOException | URISyntaxException | InterruptedException e) {
                LOGGER.warning("Download template failed " + e.getMessage());
            }
        }
        for (File file : catalog.files()) {
            if (!file.isDirectory() && file.getName().endsWith(".zip")) {
                String templatePath = Constants.TEMPLATE_BASE_PATH + "/" + file.getName().replace(".zip", "");
                if (!TemplateDownloadUtils.exists(templatePath)) {
                    TemplateDownloadUtils.installByZipFile(file, templatePath);
                }
            }
        }
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
                StaticFileCacheUtils.getInstance().refreshCacheFileMap();
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
            List<Path> candidates =
                    stream.filter(
                                    path ->
                                            !Objects.equals(
                                                    path.getFileName().toString(), "__MACOSX"))
                            .filter(
                                    path ->
                                            !Objects.equals(
                                                    path.getFileName().toString(), ".DS_Store"))
                            .collect(Collectors.toList());
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

    void movePath(Path source, Path target) throws IOException {
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


}
