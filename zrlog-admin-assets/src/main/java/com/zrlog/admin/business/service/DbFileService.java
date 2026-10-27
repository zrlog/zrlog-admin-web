package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.type.FileEntryAccess;
import com.zrlog.admin.business.type.FileEntryAction;
import com.zrlog.admin.business.util.FileEntryUtils;
import com.zrlog.common.exception.NotImplementException;
import com.zrlog.model.WebSite;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.*;
import java.util.function.LongSupplier;

public class DbFileService {
    // Raw bytes. Base64 is at most 349,528 bytes, below D1's 2,000,000-byte row limit.
    public static final int CHUNK_BYTES = 256 * 1024;
    private final DbFileStore store;
    private final LongSupplier clock;

    @Inject
    public DbFileService(Provider<WebSite> websites) {
        this(websites, System::currentTimeMillis);
    }

    DbFileService(Provider<WebSite> websites, LongSupplier clock) {
        this.store = new DbFileStore(websites, clock);
        this.clock = clock;
    }

    public UploadFileResponse toDbFile(String key, byte[] bytes) throws SQLException {
        try {
            return toDbFile(key, new ByteArrayInputStream(bytes), basename(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The caller owns the input stream. No complete-file Base64 string is allocated. */
    public UploadFileResponse toDbFile(String key, InputStream input, String originalName) throws SQLException, IOException {
        requirePath(key);
        String filename = originalName == null ? null : basename(originalName);
        if (filename != null && (filename.isBlank() || filename.length() > 255 || filename.codePoints().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Invalid original filename");
        DbFileStore.Row claim = store.claim(key);
        try {
            MessageDigest digest = digest();
            byte[] first = input.readNBytes(CHUNK_BYTES);
            byte[] next = input.readNBytes(CHUNK_BYTES);
            DbFileMetadata meta = new DbFileMetadata();
            meta.formatVersion = 1;
            meta.ready = true;
            meta.originalName = filename;
            meta.uploadedAt = clock.getAsLong();
            meta.size = first.length;
            digest.update(first);
            String inline = "";
            if (next.length == 0) {
                inline = Base64.getEncoder().encodeToString(first);
            } else {
                meta.batch = claim.meta.pendingBatch;
                meta.chunkSize = CHUNK_BYTES;
                store.chunk(claim, meta.chunkCount++, first);
                do {
                    store.chunk(claim, meta.chunkCount++, next);
                    meta.size += next.length;
                    digest.update(next);
                    next = input.readNBytes(CHUNK_BYTES);
                } while (next.length != 0);
            }
            meta.sha256 = hex(digest.digest());
            store.publish(claim, meta, inline);
            return new UploadFileResponse(key);
        } catch (IOException | SQLException | RuntimeException e) {
            try {
                store.abort(key, claim.meta.pendingBatch);
            } catch (SQLException | RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    public byte[] loadDbFile(String key) {
        try (InputStream input = openDbFile(key)) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Verify a bounded-memory snapshot before HTTP writes headers. Close removes the local scratch file. */
    public InputStream openDbFile(String key) throws IOException {
        requirePath(key);
        Path temp = null;
        try {
            DbFileStore.Row row = store.recover(store.find(key));
            if (row == null || !row.meta.ready) throw new NotImplementException();
            temp = Files.createTempFile("zrlog-db-file-", ".tmp");
            MessageDigest digest = digest();
            long size = 0;
            try (OutputStream output = Files.newOutputStream(temp)) {
                if (row.meta.batch == null) {
                    byte[] bytes = Base64.getDecoder().decode(store.inline(row));
                    output.write(bytes);
                    digest.update(bytes);
                    size = bytes.length;
                } else {
                    for (int index = 0; index < row.meta.chunkCount; index++) {
                        String encoded = store.value(DbFileStore.chunkKey(row.meta.batch, index));
                        if (encoded.length() > ((CHUNK_BYTES + 2) / 3) * 4) throw new IOException("Oversized database file chunk");
                        byte[] bytes = Base64.getDecoder().decode(encoded);
                        int expected = (int) Math.min(CHUNK_BYTES, row.meta.size - size);
                        if (bytes.length != expected) throw new IOException("Invalid database file chunk length");
                        output.write(bytes);
                        digest.update(bytes);
                        size += bytes.length;
                    }
                }
            }
            if (size != row.meta.size || (row.meta.sha256 != null && !row.meta.sha256.equals(hex(digest.digest()))))
                throw new IOException("Database file integrity check failed");
            Path snapshot = temp;
            FileInputStream result = new FileInputStream(temp.toFile()) {
                @Override
                public void close() throws IOException {
                    try { super.close(); } finally { Files.deleteIfExists(snapshot); }
                }
            };
            temp = null;
            return result;
        } catch (SQLException | IllegalArgumentException e) {
            throw new IOException("Cannot read database file", e);
        } finally {
            if (temp != null) Files.deleteIfExists(temp);
        }
    }

    public boolean hasRecord(String key) throws SQLException {
        requirePath(key);
        return store.find(key) != null;
    }

    public List<FileEntryVO> getDbFiles(String path) throws SQLException {
        requirePath(path);
        String prefix = DbFileStore.FILE_PREFIX + (path.endsWith("/") ? path : path + "/");
        Map<String, FileEntryVO> entries = new LinkedHashMap<>();
        for (Map<String, Object> record : store.list(path)) {
            DbFileStore.Row row = store.recover(new DbFileStore.Row(record));
            if (row == null || !row.meta.ready) continue;
            String relative = row.name.substring(prefix.length());
            if (relative.isEmpty()) continue;
            int slash = relative.indexOf('/');
            boolean directory = slash >= 0;
            String name = directory ? relative.substring(0, slash) : relative;
            String filePath = (path.endsWith("/") ? path : path + "/") + name;
            if (entries.containsKey(name)) continue;
            FileEntryVO entry = new FileEntryVO(name, filePath, directory ? "directory" : "file",
                    directory ? 0 : row.meta.size, directory ? "" : FileEntryUtils.toMimeType(name),
                    directory ? 0 : row.meta.uploadedAt);
            if (!directory) {
                entry.setOriginalName(row.meta.originalName);
                entry.setUploadedAt(row.meta.uploadedAt);
            }
            entry.setAccess(FileEntryAccess.ADMIN_ONLY);
            entry.setActions(directory ? List.of(FileEntryAction.OPEN, FileEntryAction.DELETE)
                    : List.of(FileEntryAction.PREVIEW, FileEntryAction.DOWNLOAD, FileEntryAction.DELETE));
            entries.put(name, FileEntryUtils.decorateEntry(entry));
        }
        return new ArrayList<>(entries.values());
    }

    public boolean deleteByKey(String path) throws SQLException {
        requirePath(path);
        return store.delete(store.find(path));
    }

    public boolean deleteByPrefix(String path) throws SQLException {
        requirePath(path);
        boolean deleted = false;
        for (Map<String, Object> record : store.list(path)) deleted |= store.delete(new DbFileStore.Row(record));
        return deleted;
    }

    private static void requirePath(String path) {
        if (path == null || !path.startsWith("/") || DbFileStore.FILE_PREFIX.length() + path.length() > 255
                || path.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid database file path");
    }

    private static String basename(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(Character.forDigit((b & 255) >>> 4, 16)).append(Character.forDigit(b & 15, 16));
        return result.toString();
    }
}
