package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.model.WebSite;
import jakarta.inject.Provider;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Single-statement conditional writes work on JDBC and D1 without a cross-request local lock. */
final class DbFileStore {
    static final String FILE_PREFIX = "db_file";
    static final String CHUNK_PREFIX = "db_file_chunk:";
    static final long UPLOAD_TIMEOUT = Duration.ofMinutes(30).toMillis();
    private static final Gson JSON =
            new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final String COLUMNS =
            "name,remark,length(value) AS encodedSize,substr(value,-2) AS tail";
    private final Provider<WebSiteRepository> websites;
    private final LongSupplier clock;

    DbFileStore(Provider<WebSiteRepository> websites, LongSupplier clock) {
        this.websites = websites;
        this.clock = clock;
    }

    Row find(String key) throws SQLException {
        FileRow row =
                websites.get()
                        .queryOne(
                                "SELECT " + COLUMNS + " FROM website WHERE name=?",
                                FileRow.class,
                                FILE_PREFIX + key);
        return row == null ? null : new Row(row);
    }

    List<FileRow> list(String path) throws SQLException {
        String prefix = FILE_PREFIX + (path.endsWith("/") ? path : path + "/");
        // Literal prefix, including long paths and '%'/'_'; D1 limits LIKE patterns to 50 bytes.
        return websites.get()
                .queryList(
                        "SELECT "
                                + COLUMNS
                                + " FROM website WHERE name>=? AND name<? AND substr(name,1,length(?))=? ORDER BY name",
                        FileRow.class,
                        prefix,
                        prefix.substring(0, prefix.length() - 1) + "0",
                        prefix,
                        prefix);
    }

    List<FileRow> cleanupPage(String path, String after, int limit) throws SQLException {
        String prefix = FILE_PREFIX + path + "/";
        return websites.get()
                .queryList(
                        "SELECT "
                                + COLUMNS
                                + " FROM website WHERE name>=? AND name<? AND name>? ORDER BY name LIMIT ?",
                        FileRow.class,
                        prefix,
                        prefix.substring(0, prefix.length() - 1) + "0",
                        after,
                        limit);
    }

    boolean deleteExpired(Row row, com.zrlog.data.security.AccountAccess actor)
            throws SQLException {
        String path = row.name.substring(FILE_PREFIX.length());
        String escaped = path.replace("/", "\\/");
        DbFileMetadata meta = copy(row.meta);
        meta.deleting = true;
        String remark = encode(meta);
        // References and deletion intent share one statement. The aggregate derived table also
        // supports
        // MySQL's restriction on selecting an UPDATE target directly in a subquery.
        boolean claimed =
                websites.get()
                                .updateRemarkWhere(
                                        remark,
                                        "name=? AND remark=?"
                                                + " AND EXISTS (SELECT 1 FROM user WHERE userId=? AND role=? AND enabled=? AND authVersion=?)"
                                                + " AND NOT EXISTS (SELECT 1 FROM log WHERE instr(content,?)>0 OR instr(markdown,?)>0"
                                                + " OR instr(thumbnail,?)>0 OR instr(digest,?)>0)"
                                                + " AND NOT EXISTS (SELECT 1 FROM log_version WHERE instr(patch_json,?)>0 OR instr(patch_json,?)>0)"
                                                + " AND (SELECT reference_count FROM (SELECT count(*) AS reference_count FROM website WHERE name<>?"
                                                + " AND NOT (name>=? AND name<?) AND name NOT LIKE 'assistant.cleanup.cursor:%'"
                                                + " AND (instr(value,?)>0 OR instr(value,?)>0)) AS saved_references)=0",
                                        row.name,
                                        row.remark,
                                        actor.getUserId(),
                                        actor.getRole(),
                                        true,
                                        actor.getAuthVersion(),
                                        path,
                                        path,
                                        path,
                                        path,
                                        path,
                                        escaped,
                                        row.name,
                                        FILE_PREFIX,
                                        "db_film",
                                        path,
                                        escaped)
                        == 1;
        if (!claimed) return false;
        recover(new Row(row.name, remark, meta));
        return true;
    }

    Row claim(String key) throws SQLException {
        Row old = recover(find(key));
        if (old != null && old.meta.pendingBatch != null) throw changed();
        DbFileMetadata meta = old == null ? new DbFileMetadata() : copy(old.meta);
        meta.formatVersion = 1;
        meta.pendingBatch = UUID.randomUUID().toString();
        meta.pendingUntil = clock.getAsLong() + UPLOAD_TIMEOUT;
        String remark = encode(meta);
        if (old == null) {
            websites.get().insert(WebSiteRepository.entry(FILE_PREFIX + key, "", remark));
        } else if (!updateRemark(old, remark)) {
            throw changed();
        }
        return new Row(FILE_PREFIX + key, remark, meta);
    }

    void chunk(Row claim, int index, byte[] bytes) throws SQLException {
        if (clock.getAsLong() >= claim.meta.pendingUntil) throw changed();
        if (!websites.get()
                .insertWhen(
                        WebSiteRepository.entry(
                                chunkKey(claim.meta.pendingBatch, index),
                                java.util.Base64.getEncoder().encodeToString(bytes),
                                null),
                        "EXISTS (SELECT 1 FROM website WHERE name=? AND remark=?)",
                        claim.name,
                        claim.remark)) throw changed();
    }

    void publish(Row claim, DbFileMetadata meta, String inline) throws SQLException {
        if (clock.getAsLong() >= claim.meta.pendingUntil) throw changed();
        meta.retiredBatch = claim.meta.batch;
        String remark = encode(meta);
        if (!update(
                claim, WebSiteRepository.entry(null, inline, remark), List.of("value", "remark")))
            throw changed();
        // A failed cleanup leaves its batch in remark for a later read/list/delete to retry.
        recover(new Row(claim.name, remark, meta));
    }

    void abort(String key, String batch) throws SQLException {
        Row row = find(key);
        if (row == null || !batch.equals(row.meta.pendingBatch) || row.meta.deleting) return;
        DbFileMetadata meta = copy(row.meta);
        meta.pendingBatch = null;
        meta.pendingUntil = 0;
        meta.retiredBatch = batch;
        String remark = encode(meta);
        if (updateRemark(row, remark)) recover(new Row(row.name, remark, meta));
    }

    Row recover(Row row) throws SQLException {
        if (row == null) return null;
        DbFileMetadata meta = copy(row.meta);
        if (meta.deleting) {
            removeBatch(meta.batch);
            removeBatch(meta.pendingBatch);
            removeBatch(meta.retiredBatch);
            if (!deleteRow(row)) throw changed();
            return null;
        }
        if (meta.pendingBatch != null && clock.getAsLong() >= meta.pendingUntil) {
            meta.retiredBatch = meta.pendingBatch;
            meta.pendingBatch = null;
            meta.pendingUntil = 0;
            String remark = encode(meta);
            if (!updateRemark(row, remark)) throw changed();
            row = new Row(row.name, remark, meta);
        }
        if (meta.retiredBatch != null) {
            removeBatch(meta.retiredBatch);
            meta = copy(meta);
            meta.retiredBatch = null;
            String remark = encode(meta);
            if (!updateRemark(row, remark)) throw changed();
            row = new Row(row.name, remark, meta);
        }
        if (!meta.ready && meta.pendingBatch == null) {
            if (!deleteRow(row)) throw changed();
            return null;
        }
        return row;
    }

    boolean delete(Row row) throws SQLException {
        if (row == null) return false;
        if (!row.meta.deleting) {
            DbFileMetadata meta = copy(row.meta);
            meta.deleting = true;
            String remark = encode(meta);
            if (!updateRemark(row, remark)) throw changed();
            row = new Row(row.name, remark, meta);
        }
        recover(row);
        return true;
    }

    String value(String name) throws SQLException {
        Object value = websites.get().queryFirstObj("SELECT value FROM website WHERE name=?", name);
        if (!(value instanceof String)) throw new SQLException("Missing database file payload");
        return (String) value;
    }

    String inline(Row row) throws SQLException {
        Object value =
                websites.get()
                        .queryFirstObj(
                                "SELECT value FROM website WHERE name=? AND "
                                        + (row.remark == null ? "remark IS NULL" : "remark=?"),
                                row.remark == null
                                        ? new Object[] {row.name}
                                        : new Object[] {row.name, row.remark});
        if (!(value instanceof String)) throw changed();
        return (String) value;
    }

    private void removeBatch(String batch) throws SQLException {
        if (batch == null) return;
        requireBatch(batch);
        websites.get()
                .deleteWhere(
                        "name>=? AND name<?",
                        CHUNK_PREFIX + batch + ":",
                        CHUNK_PREFIX + batch + ";");
    }

    private boolean updateRemark(Row row, String remark) throws SQLException {
        return update(row, WebSiteRepository.entry(null, null, remark), List.of("remark"));
    }

    private boolean update(Row row, WebSite patch, List<String> fields) throws SQLException {
        return websites.get()
                        .updateWhere(
                                patch,
                                fields,
                                "name=? AND "
                                        + (row.remark == null ? "remark IS NULL" : "remark=?"),
                                row.remark == null
                                        ? new Object[] {row.name}
                                        : new Object[] {row.name, row.remark})
                == 1;
    }

    private boolean deleteRow(Row row) throws SQLException {
        return websites.get().deleteWhere("name=? AND remark=?", row.name, row.remark) == 1;
    }

    static String chunkKey(String batch, int index) {
        requireBatch(batch);
        return CHUNK_PREFIX + batch + ":" + index;
    }

    private static void requireBatch(String batch) {
        if (!batch.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalStateException("Invalid database file batch");
    }

    static String encode(DbFileMetadata meta) {
        String value = JSON.toJson(meta);
        if (value.length() > 2000)
            throw new IllegalArgumentException("Database file metadata exceeds remark capacity");
        return value;
    }

    private static DbFileMetadata copy(DbFileMetadata meta) {
        return JSON.fromJson(JSON.toJson(meta), DbFileMetadata.class);
    }

    static IllegalStateException changed() {
        return new IllegalStateException(
                "Database file changed or its upload expired; retry the operation");
    }

    static final class FileRow {
        String name;
        String remark;
        long encodedSize;
        String tail;
    }

    static final class Row {
        final String name;
        final String remark;
        final DbFileMetadata meta;

        Row(String name, String remark, DbFileMetadata meta) {
            this.name = name;
            this.remark = remark;
            this.meta = meta;
        }

        Row(FileRow row) {
            name = row.name.toString();
            remark = (String) row.remark;
            if (remark == null || remark.isEmpty()) {
                meta = new DbFileMetadata();
                meta.formatVersion = 1;
                meta.ready = true;
                long encoded = ((Number) row.encodedSize).longValue();
                String tail = java.util.Objects.toString(row.tail, "");
                meta.size =
                        encoded / 4 * 3 - (tail.endsWith("==") ? 2 : tail.endsWith("=") ? 1 : 0);
            } else {
                meta = JSON.fromJson(remark, DbFileMetadata.class);
                if (meta == null
                        || meta.formatVersion != 1
                        || meta.size < 0
                        || meta.chunkCount < 0
                        || (meta.batch == null && meta.chunkCount != 0)
                        || (meta.batch != null
                                && (meta.size <= DbFileService.CHUNK_BYTES
                                        || meta.sha256 == null
                                        || !meta.sha256.matches("[0-9a-f]{64}")))
                        || (meta.batch != null
                                && (meta.chunkSize != DbFileService.CHUNK_BYTES
                                        || meta.chunkCount
                                                != (meta.size + meta.chunkSize - 1)
                                                        / meta.chunkSize)))
                    throw new IllegalStateException("Invalid database file metadata");
                if (meta.batch != null) requireBatch(meta.batch);
                if (meta.pendingBatch != null) requireBatch(meta.pendingBatch);
                if (meta.retiredBatch != null) requireBatch(meta.retiredBatch);
            }
        }
    }
}
