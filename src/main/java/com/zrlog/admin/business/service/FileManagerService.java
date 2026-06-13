package com.zrlog.admin.business.service;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.FileUtils;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.request.ReplaceArticleResourceUrlRequest;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.rest.response.FileReferenceVO;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.type.FileDirectoryAction;
import com.zrlog.admin.business.type.FileEntryAccess;
import com.zrlog.admin.business.type.FileEntryAction;
import com.zrlog.admin.business.util.FileEntryUtils;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.util.I18nUtil;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static com.zrlog.admin.business.AdminConstants.ADMIN_DB_ATTACHED_TMP;

public class FileManagerService {

    public static final String LIBRARY_ROOT = "";
    public static final String ATTACHED_ROOT = "/attached";
    public static final String EXTERNAL_ROOT = "/external";
    private final FileManagerReferenceService referenceService = new FileManagerReferenceService();

    public List<FileEntryVO> getShortcuts() {
        List<FileEntryVO> shortcuts = new ArrayList<>();
        FileEntryVO library = new FileEntryVO(I18nUtil.getAdminBackendStringFromRes("admin.fileManager.title"), LIBRARY_ROOT, "directory", 0, "", 0);
        shortcuts.add(decorateEntry(library));
        shortcuts.add(decorateEntry(new FileEntryVO(I18nUtil.getAdminBackendStringFromRes("admin.fileManager.attachedDir"), ATTACHED_ROOT, "directory", 0, "", 0)));
        shortcuts.add(decorateEntry(new FileEntryVO(I18nUtil.getAdminBackendStringFromRes("admin.fileManager.externalLinks"), EXTERNAL_ROOT, "directory", 0, "", 0)));

        if (EnvKit.isDevMode()) {
            shortcuts.add(decorateEntry(new FileEntryVO(I18nUtil.getAdminBackendStringFromRes("admin.fileManager.projectRoot"), AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH, "directory", 0, "", 0)));
        }
        shortcuts.add(decorateEntry(new FileEntryVO(I18nUtil.getAdminBackendStringFromRes("admin.fileManager.dbTempDir"), ADMIN_DB_ATTACHED_TMP, "directory", 0, "", 0)));
        if (EnvKit.isDevMode()) {
            shortcuts.add(decorateEntry(new FileEntryVO(I18nUtil.getAdminBackendStringFromRes("admin.fileManager.systemTempDir"), AdminConstants.ADMIN_DEV_FILE_SYSTEM_TEMP_URI_BASE_PATH, "directory", 0, "", 0)));
        }
        return shortcuts;
    }


    public List<FileEntryVO> list(String path) throws SQLException {
        boolean referenceEnabled = new WebSiteService().isFeatureResourceReferenceEnabled();
        if (Objects.equals(path, LIBRARY_ROOT)) {
            return getShortcuts().stream().filter(e -> !Objects.equals(e.getPath(), path)).collect(Collectors.toList());
        }
        if (path.startsWith(EXTERNAL_ROOT)) {
            return referenceService.getExternalResources(path, referenceEnabled, this::decorateEntry);
        }
        if (path.startsWith(ADMIN_DB_ATTACHED_TMP)) {
            return new DbFileService().getDbFiles(path);
        }
        Map<String, List<FileReferenceVO>> localReferenceMap = referenceEnabled
                ? referenceService.buildLocalReferenceMap() : Collections.emptyMap();
        File target = resolveAndValidate(path);
        List<FileEntryVO> entries = new ArrayList<>();
        if (target.exists() && target.isDirectory()) {
            File[] files = target.listFiles();
            if (files != null) {
                for (File child : files) {
                    String childRelPath = path.endsWith("/") ? path + child.getName() : path + "/" + child.getName();
                    FileEntryVO vo = new FileEntryVO(
                            child.getName(),
                            childRelPath,
                            child.isDirectory() ? "directory" : "file",
                            child.isDirectory() ? 0 : child.length(),
                            child.isDirectory() ? "" : FileEntryUtils.toMimeType(child.getName()),
                            child.lastModified()
                    );
                    entries.add(decorateEntry(vo));
                }
            }
        }
        return referenceService.applyReferenceInfo(entries, localReferenceMap);
    }

    public List<FileEntryVO> search(String key) throws SQLException {
        if (StringUtils.isEmpty(key)) {
            return Collections.emptyList();
        }
        List<FileEntryVO> results = new ArrayList<>();
        collectSearchResults(LIBRARY_ROOT, key.toLowerCase(Locale.ROOT), results, new HashSet<>());
        return results;
    }

    private void collectSearchResults(String path, String key, List<FileEntryVO> results, Set<String> visited) throws SQLException {
        if (results.size() >= 100 || !visited.add(path)) {
            return;
        }
        for (FileEntryVO entry : list(path)) {
            if (matches(entry, key)) {
                results.add(entry);
                if (results.size() >= 100) {
                    return;
                }
            }
            if ("directory".equals(entry.getType())) {
                collectSearchResults(entry.getPath(), key, results, visited);
                if (results.size() >= 100) {
                    return;
                }
            }
        }
    }

    private boolean matches(FileEntryVO entry, String key) {
        String name = entry.getName() == null ? "" : entry.getName().toLowerCase(Locale.ROOT);
        String path = entry.getPath() == null ? "" : entry.getPath().toLowerCase(Locale.ROOT);
        return name.contains(key) || path.contains(key);
    }

    public List<FileEntryVO> listBrokenLocalResourceReferences(String key) throws SQLException {
        if (!new WebSiteService().isFeatureResourceReferenceEnabled()) {
            return Collections.emptyList();
        }
        List<FileEntryVO> entries = referenceService.getMissingLocalResources(this::decorateEntry);
        if (StringUtils.isEmpty(key)) {
            return entries;
        }
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> matches(entry, normalizedKey)).collect(Collectors.toList());
    }

    public boolean refreshReferenceIndex() throws SQLException {
        if (!new WebSiteService().isFeatureResourceReferenceEnabled()) {
            return false;
        }
        return referenceService.refreshReferenceIndex();
    }

    public File resolveAndValidate(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            relativePath = ATTACHED_ROOT;
        }
        String normalized = relativePath.replace("\\", "/");
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid path");
        }

        if (normalized.startsWith(ATTACHED_ROOT)) {
            return PathUtil.getStaticFile(normalized);
        }
        if (normalized.startsWith(AdminConstants.ADMIN_DEV_FILE_SYSTEM_TEMP_URI_BASE_PATH)) {
            String subPath = normalized.substring(AdminConstants.ADMIN_DEV_FILE_SYSTEM_TEMP_URI_BASE_PATH.length());
            return new File("/tmp/" + subPath);
        }
        if (normalized.startsWith(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH)) {
            String subPath = normalized.substring(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH.length());
            return new File(PathUtil.getRootPath() + subPath);
        }
        throw new IllegalArgumentException("Unauthorized path: " + relativePath);
    }

    public List<FileDirectoryAction> getDirectoryActions(String path) {
        if (path != null && (path.equals(ADMIN_DB_ATTACHED_TMP) || path.startsWith(ADMIN_DB_ATTACHED_TMP + "/"))) {
            return List.of(FileDirectoryAction.UPLOAD);
        }
        if (path != null && (path.equals(ATTACHED_ROOT) || path.startsWith(ATTACHED_ROOT + "/"))) {
            return List.of(FileDirectoryAction.UPLOAD, FileDirectoryAction.MKDIR);
        }
        return Collections.emptyList();
    }

    public FileEntryVO decorateEntry(FileEntryVO entry) {
        entry.setAccess(resolveAccess(entry));
        FileEntryUtils.decorateEntry(entry);
        entry.setActions(resolveActions(entry));
        entry.setDirectoryActions("directory".equals(entry.getType()) ? getDirectoryActions(entry.getPath()) : Collections.emptyList());
        return entry;
    }

    private FileEntryAccess resolveAccess(FileEntryVO entry) {
        String path = Objects.requireNonNullElse(entry.getPath(), "");
        if (entry.isMissing()) {
            return FileEntryAccess.VIRTUAL;
        }
        if (path.isEmpty() || (entry.isVirtual() && "directory".equals(entry.getType()))) {
            return FileEntryAccess.VIRTUAL;
        }
        if (path.startsWith(EXTERNAL_ROOT)) {
            return FileEntryAccess.VIRTUAL;
        }
        if (FileEntryUtils.isExternalUrl(path) || path.startsWith(ATTACHED_ROOT + "/") || path.equals(ATTACHED_ROOT)) {
            return FileEntryAccess.PUBLIC_URL;
        }
        return FileEntryAccess.ADMIN_ONLY;
    }

    private List<FileEntryAction> resolveActions(FileEntryVO entry) {
        String path = Objects.requireNonNullElse(entry.getPath(), "");
        List<FileEntryAction> actions = new ArrayList<>();
        if (entry.isMissing()) {
            if (path.startsWith(ATTACHED_ROOT + "/")) {
                actions.add(FileEntryAction.REUPLOAD);
                actions.add(FileEntryAction.UPDATE_REFERENCES);
            }
            return actions;
        }
        boolean directory = "directory".equals(entry.getType());
        if (directory) {
            actions.add(FileEntryAction.OPEN);
        }
        if ("file".equals(entry.getType()) && (entry.isImage() || entry.isTextPreviewable())) {
            actions.add(FileEntryAction.PREVIEW);
        }
        if ("file".equals(entry.getType())) {
            actions.add(FileEntryAction.DOWNLOAD);
        }
        if ("file".equals(entry.getType()) && (FileEntryUtils.isExternalUrl(path) || path.startsWith(ATTACHED_ROOT + "/"))) {
            actions.add(FileEntryAction.COPY_URL);
            actions.add(FileEntryAction.SELECT);
        }
        if (path.startsWith(ATTACHED_ROOT + "/")) {
            actions.add(FileEntryAction.RENAME);
            actions.add(FileEntryAction.DELETE);
            actions.add(FileEntryAction.UPDATE_REFERENCES);
        }
        if (path.startsWith(ADMIN_DB_ATTACHED_TMP + "/")) {
            actions.add(FileEntryAction.DELETE);
        }
        if (path.startsWith(EXTERNAL_ROOT + "/")) {
            actions.add(FileEntryAction.UPDATE_REFERENCES);
        }
        return actions.stream().distinct().collect(Collectors.toList());
    }

    public ReplaceArticleResourceUrlResponse replaceArticleResourceUrl(AdminTokenVO user,
                                                                       ReplaceArticleResourceUrlRequest request)
            throws SQLException {
        return referenceService.replaceArticleResourceUrl(user, request);
    }

    public UploadFileResponse reuploadMissingLocalResource(String path, File uploadFile, HttpRequest request,
                                                           AdminTokenVO user) throws IOException {
        if (StringUtils.isEmpty(path) || !path.startsWith(ATTACHED_ROOT + "/")) {
            throw new IllegalArgumentException("Invalid path");
        }
        File target = resolveAndValidate(path);
        if (target.exists()) {
            throw new IllegalArgumentException("Target file already exists");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create parent directory");
        }
        FileUtils.moveOrCopyFile(uploadFile.toString(), target.toString(), true);
        return new UploadService().getCloudUrl(path, target.toString(), request, user);
    }

    public boolean delete(String path) throws SQLException {
        if (!hasAction(path, FileEntryAction.DELETE)) {
            return false;
        }
        if (path.startsWith(ADMIN_DB_ATTACHED_TMP + "/")) {
            if ("directory".equals(resolveEntryType(path))) {
                return new DbFileService().deleteByPrefix(path);
            }
            return new DbFileService().deleteByKey(path);
        }
        File target = resolveAndValidate(path);
        return deleteRecursive(target);
    }

    public boolean mkdir(String path) {
        if (StringUtils.isEmpty(path) || !getDirectoryActions(parentPath(path)).contains(FileDirectoryAction.MKDIR)) {
            return false;
        }
        File target = resolveAndValidate(path);
        return target.exists() ? target.isDirectory() : target.mkdirs();
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    public boolean rename(String path, String newName) throws SQLException {
        return rename(path, newName, false, null);
    }

    public boolean rename(String path, String newName, boolean syncArticleReferences, AdminTokenVO user) throws SQLException {
        if (!hasAction(path, FileEntryAction.RENAME)) {
            return false;
        }
        File target = resolveAndValidate(path);
        File dest = new File(target.getParentFile(), newName);
        boolean directory = target.isDirectory();
        boolean renamed = target.renameTo(dest);
        if (renamed && syncArticleReferences && hasAction(path, FileEntryAction.UPDATE_REFERENCES)) {
            referenceService.replaceArticleResourceReferences(user, referenceService.normalizePath(path),
                    referenceService.normalizePath(toRelativePath(path, newName)), directory);
        }
        return renamed;
    }

    private String toRelativePath(String path, String newName) {
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return newName;
        }
        return path.substring(0, index + 1) + newName;
    }

    private boolean hasAction(String path, FileEntryAction action) {
        return resolveActions(new FileEntryVO(new File(path).getName(), path, resolveEntryType(path), 0, "", 0)).contains(action);
    }

    private String resolveEntryType(String path) {
        if (FileEntryUtils.isExternalUrl(path)) {
            return "file";
        }
        if (path.startsWith(ADMIN_DB_ATTACHED_TMP + "/")) {
            try {
                new DbFileService().loadDbFile(path);
                return "file";
            } catch (Exception e) {
                return "directory";
            }
        }
        if (path.startsWith(EXTERNAL_ROOT + "/") || path.equals(ATTACHED_ROOT) || path.equals(EXTERNAL_ROOT)) {
            return "directory";
        }
        try {
            return resolveAndValidate(path).isDirectory() ? "directory" : "file";
        } catch (Exception e) {
            return "file";
        }
    }

    private String parentPath(String path) {
        if (StringUtils.isEmpty(path)) {
            return "";
        }
        int index = path.lastIndexOf('/');
        if (index <= 0) {
            return "";
        }
        return path.substring(0, index);
    }

    public byte[] read(String path) throws IOException {
        if (path.startsWith(ADMIN_DB_ATTACHED_TMP)) {
            return new DbFileService().loadDbFile(path);
        }
        File target = resolveAndValidate(path);
        return IOUtil.getByteByFile(target);
    }

    public String readContent(String path) throws IOException {
        byte[] bytes = read(path);
        if (bytes.length > 1024 * 1024) {
            return "File too large (max 1MB)";
        }
        return new String(bytes);
    }
}
