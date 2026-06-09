package com.zrlog.admin.business.service;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.type.FileEntryAccess;
import com.zrlog.admin.business.type.FileEntryAction;
import com.zrlog.admin.business.util.FileEntryUtils;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.common.exception.NotImplementException;
import com.zrlog.model.WebSite;

import java.sql.SQLException;
import java.util.*;

public class DbFileService {

    private static final String DB_FILE_PREFIX = "db_file";

    public UploadFileResponse toDbFile(String key, byte[] bytes) throws SQLException {
        // DB 存储
        String dbKey = DB_FILE_PREFIX + key;
        new WebsiteKvService().putString(dbKey, Base64.getEncoder().encodeToString(bytes));
        return new UploadFileResponse(key);
    }

    public byte[] loadDbFile(String key) {
        String dbKey = DB_FILE_PREFIX + key;
        Map<String, Object> webSiteMap = new WebsiteKvService().getByNames(Collections.singletonList(dbKey));
        Object value = webSiteMap.get(dbKey);
        if (value instanceof String) {
            return Base64.getDecoder().decode((String) value);
        }
        throw new NotImplementException();
    }

    public List<FileEntryVO> getDbFiles(String path) throws SQLException {
        String dbPrefix = DB_FILE_PREFIX + path;
        if (!dbPrefix.endsWith("/")) {
            dbPrefix += "/";
        }
        List<Map<String, Object>> records = new WebSite().queryListWithParams("SELECT `name`,length(`value`) AS `size` FROM `website` WHERE `name` LIKE ?", dbPrefix + "%");
        Map<String, FileEntryVO> entriesMap = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            String name = (String) record.get("name");
            String relativePath = name.substring(dbPrefix.length());
            if (StringUtils.isEmpty(relativePath)) {
                continue;
            }
            int firstSlashIndex = relativePath.indexOf("/");
            if (firstSlashIndex != -1) {
                String dirName = relativePath.substring(0, firstSlashIndex);
                String dirPath = path.endsWith("/") ? path + dirName : path + "/" + dirName;
                if (!entriesMap.containsKey(dirName)) {
                    FileEntryVO entry = new FileEntryVO(dirName, dirPath, "directory", 0, "", 0);
                    entry.setAccess(FileEntryAccess.ADMIN_ONLY);
                    entry.setActions(List.of(FileEntryAction.OPEN, FileEntryAction.DELETE));
                    entriesMap.put(dirName, FileEntryUtils.decorateEntry(entry));
                }
            } else {
                String fileName = relativePath;
                String filePath = path.endsWith("/") ? path + fileName : path + "/" + fileName;
                FileEntryVO entry = new FileEntryVO(fileName, filePath, "file", toLong(record.get("size")), FileEntryUtils.toMimeType(fileName), 0);
                entry.setAccess(FileEntryAccess.ADMIN_ONLY);
                entry.setActions(List.of(FileEntryAction.PREVIEW, FileEntryAction.DOWNLOAD, FileEntryAction.DELETE));
                entriesMap.put(fileName, FileEntryUtils.decorateEntry(entry));
            }
        }
        return new ArrayList<>(entriesMap.values());
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.isNotEmpty((String) value)) {
            return Long.parseLong((String) value);
        }
        return 0;
    }

    public boolean deleteByKey(String path) throws SQLException {
        String dbKey = DB_FILE_PREFIX + path;
        return new WebSite().set("name", dbKey).delete();
    }

    public boolean deleteByPrefix(String path) throws SQLException {
        String dbPrefix = DB_FILE_PREFIX + path;
        if (!dbPrefix.endsWith("/")) {
            dbPrefix += "/";
        }
        return new WebSite().execute("DELETE FROM `website` WHERE `name` LIKE ?", dbPrefix + "%");
    }
}
