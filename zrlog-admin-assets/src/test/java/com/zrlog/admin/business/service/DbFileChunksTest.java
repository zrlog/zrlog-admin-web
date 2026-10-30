package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.exception.NotImplementException;
import com.zrlog.model.WebSite;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class DbFileChunksTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() {
        return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"});
    }

    private final String backend;
    private InMemoryZrLogDatabase db;
    private DbFileService service;
    private final AtomicLong now = new AtomicLong(1_790_000_000_000L);

    public DbFileChunksTest(String backend) { this.backend = backend; }

    @Before
    public void open() throws Exception {
        db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        service = anotherInstance();
    }

    @After
    public void close() throws Exception { if (db != null) db.close(); }

    private DbFileService anotherInstance() { return new DbFileService(() -> new WebSite(db.dataSource()), now::get); }

    @Test
    public void expiryCleanupKeepsReferencedRecentLegacyAndActiveResources() throws Exception {
        String root = "/admin/attached/tmp/";
        service.toDbFile(root + "expired.bin", data(DbFileService.CHUNK_BYTES + 1));
        service.toDbFile(root + "article.bin", data(2));
        service.toDbFile(root + "session.bin", data(3));
        service.toDbFile(root + "history.bin", data(4));
        db.putWebsite("db_file" + root + "legacy.bin", "YQ==");
        service.toDbFile("/admin/attached/tmp-neighbor/keep.bin", data(5));
        now.addAndGet(java.time.Duration.ofDays(8).toMillis());
        service.toDbFile(root + "recent.bin", data(6));
        new DbFileStore(() -> new WebSite(db.dataSource()), now::get).claim(root + "active.bin");
        db.execute("insert into log(logId,userId,typeId,title,content,rubbish) values(?,?,?,?,?,?)",
                71, 1, 1, "References", "<img src=\"" + root + "article.bin\">", true);
        db.execute("insert into log_version(log_id,article_version,from_version,patch_json) values(?,?,?,?)",
                71, 0, 1, "{\"content\":\"" + root + "history.bin\"}");
        db.putWebsite("ai.saved-session", "{\"file\":\"" + root + "session.bin\"}");
        var actor = com.zrlog.data.security.AccountAccess.from(new com.zrlog.model.User(db.dataSource()).loadById(1));
        var context = new com.zrlog.admin.business.automation.TaskExecutionContext("cleanup", actor, now.get() + 60000, now::get, () -> {});
        DbFileService.CleanupBatch batch = service.cleanupTemporary(7, "", context);
        assertEquals(1, batch.removed);
        assertFalse(service.hasRecord(root + "expired.bin"));
        for (String name : List.of("article.bin", "session.bin", "history.bin", "recent.bin", "legacy.bin", "active.bin"))
            assertTrue(name, service.hasRecord(root + name));
        assertTrue(service.hasRecord("/admin/attached/tmp-neighbor/keep.bin"));
        assertEquals(0L, ((Number) db.scalar("SELECT count(*) FROM website WHERE name>=? AND name<?", "db_file_chunk:", "db_file_chunk;")).longValue());
        assertThrows(IllegalArgumentException.class, () -> service.cleanupTemporary(0, "", context));
    }

    @Test
    public void cleanupPreservesWebsiteReferencesSavedImmediatelyBeforeDeletionClaim() throws Exception {
        for (boolean escaped : new boolean[]{false, true}) {
            String path = "/admin/attached/tmp/racing-" + escaped + ".bin";
            byte[] payload = data(DbFileService.CHUNK_BYTES + 17);
            service.toDbFile(path, payload);
            now.addAndGet(java.time.Duration.ofDays(8).toMillis());
            AtomicInteger referenceWrites = new AtomicInteger();
            WebSite documents = new WebSite(db.dataSource()) {
                @Override public boolean execute(String sql, Object... params) throws SQLException {
                    if (sql.startsWith("UPDATE website SET remark=?") && sql.contains("FROM log")
                            && Arrays.asList(params).contains("db_file" + path) && referenceWrites.getAndIncrement() == 0) {
                        db.putWebsite("assistant.saved-session." + escaped,
                                "{\"file\":\"" + (escaped ? path.replace("/", "\\/") : path) + "\"}");
                    }
                    return super.execute(sql, params);
                }
            };
            var cleanup = new DbFileService(() -> documents, now::get);
            var actor = com.zrlog.data.security.AccountAccess.from(new com.zrlog.model.User(db.dataSource()).loadById(1));
            var context = new com.zrlog.admin.business.automation.TaskExecutionContext("cleanup", actor,
                    now.get() + 60000, now::get, () -> {});
            assertEquals(0, cleanup.cleanupTemporary(7, "", context).removed);
            assertTrue(referenceWrites.get() > 0);
            assertFalse(metadata(path).deleting);
            assertArrayEquals(payload, anotherInstance().loadDbFile(path));
        }
    }

    @Test
    public void roundTripsBoundarySizesAcrossInstancesWithBoundedRowsAndMetadata() throws Exception {
        for (int length : new int[]{0, 1, 2, DbFileService.CHUNK_BYTES - 1, DbFileService.CHUNK_BYTES,
                DbFileService.CHUNK_BYTES + 1, DbFileService.CHUNK_BYTES * 2, 2 * 1024 * 1024 + 57}) {
            byte[] bytes = data(length);
            String path = "/files/" + length + ".bin";
            service.toDbFile(path, new ByteArrayInputStream(bytes), "原始文件 " + length + ".bin");
            assertArrayEquals(bytes, anotherInstance().loadDbFile(path));
            DbFileMetadata meta = metadata(path);
            assertEquals(length, meta.size);
            assertEquals(now.get(), meta.uploadedAt);
            assertEquals("原始文件 " + length + ".bin", meta.originalName);
            assertTrue(meta.ready);
            assertEquals(64, meta.sha256.length());
            assertTrue(db.queryOne("SELECT remark FROM website WHERE name=?", "db_file" + path).get("remark").toString().length() <= 2000);
            assertEquals(length > DbFileService.CHUNK_BYTES ? (length + DbFileService.CHUNK_BYTES - 1) / DbFileService.CHUNK_BYTES : 0, meta.chunkCount);
            FileEntryVO entry = anotherInstance().getDbFiles("/files").stream().filter(e -> e.getPath().equals(path)).findFirst().orElseThrow();
            assertEquals(length, entry.getSize());
            assertEquals(meta.originalName, entry.getOriginalName());
            assertEquals(now.get(), entry.getUploadedAt());
            assertEquals(now.get(), entry.getLastModified());
        }
        assertEquals(8, service.getDbFiles("/files").size());
        assertTrue(((Number) db.scalar("SELECT max(length(value)) FROM website WHERE substr(name,1,7)='db_file'")).longValue() <= 349528);
        assertTrue(service.deleteByPrefix("/files"));
        assertEquals(0L, fileRowCount());
    }

    @Test
    public void preservesLegacyBase64AndReportsDecodedSizeWithoutInventingUploadTime() throws Exception {
        for (int length : new int[]{0, 1, 2, 3, 4}) {
            String path = "/legacy/" + length + ".txt";
            db.putWebsite("db_file" + path, Base64.getEncoder().encodeToString(data(length)));
            assertArrayEquals(data(length), service.loadDbFile(path));
        }
        for (FileEntryVO entry : service.getDbFiles("/legacy")) {
            assertEquals(Long.parseLong(entry.getName().replace(".txt", "")), entry.getSize());
            assertEquals(0, entry.getUploadedAt());
        }
        service.toDbFile("/legacy/1.txt", data(DbFileService.CHUNK_BYTES + 1));
        assertArrayEquals(data(DbFileService.CHUNK_BYTES + 1), anotherInstance().loadDbFile("/legacy/1.txt"));
        assertTrue(service.deleteByPrefix("/legacy"));
        assertEquals(0L, fileRowCount());
    }

    @Test
    public void overwriteAndDeleteRemoveAllOldAndCurrentChunksAndKeepOtherFiles() throws Exception {
        byte[] large = data(DbFileService.CHUNK_BYTES * 3 + 5);
        service.toDbFile("/files/a.bin", large);
        String previous = metadata("/files/a.bin").batch;
        service.toDbFile("/files/a.bin", data(13));
        assertEquals(0L, batchCount(previous));
        service.toDbFile("/files/a.bin", large);
        service.toDbFile("/files/sub/b.bin", large);
        service.toDbFile("/files-neighbor/keep.bin", large);
        assertTrue(anotherInstance().deleteByKey("/files/a.bin"));
        assertThrows(NotImplementException.class, () -> service.loadDbFile("/files/a.bin"));
        assertTrue(anotherInstance().deleteByPrefix("/files"));
        assertArrayEquals(large, service.loadDbFile("/files-neighbor/keep.bin"));
        assertEquals(5L, fileRowCount());
    }

    @Test
    public void treatsLongPathsAndWildcardCharactersLiterally() throws Exception {
        String directory = "/a_%/📂" + "long".repeat(20);
        service.toDbFile(directory + "/a.bin", data(DbFileService.CHUNK_BYTES + 9));
        service.toDbFile("/a_X/" + "long".repeat(20) + "/b.bin", data(9));
        assertEquals(1, service.getDbFiles(directory).size());
        assertTrue(service.deleteByPrefix(directory));
        assertArrayEquals(data(9), service.loadDbFile("/a_X/" + "long".repeat(20) + "/b.bin"));
    }

    @Test
    public void rejectsMissingAndCorruptChunksBeforeReturningADownloadStream() throws Exception {
        String path = "/files/a.bin";
        service.toDbFile(path, data(DbFileService.CHUNK_BYTES + 10));
        String key = DbFileStore.chunkKey(metadata(path).batch, 1);
        db.execute("UPDATE website SET value=? WHERE name=?", Base64.getEncoder().encodeToString(new byte[10]), key);
        assertThrows(IOException.class, () -> service.openDbFile(path));
        db.execute("DELETE FROM website WHERE name=?", key);
        assertThrows(IOException.class, () -> service.openDbFile(path));
        assertTrue(service.deleteByKey(path));
        assertEquals(0L, fileRowCount());
    }

    @Test
    public void completedDownloadSnapshotSurvivesOverwriteAndDeletion() throws Exception {
        byte[] bytes = data(DbFileService.CHUNK_BYTES + 19);
        service.toDbFile("/files/a.bin", bytes);
        try (InputStream download = service.openDbFile("/files/a.bin")) {
            anotherInstance().toDbFile("/files/a.bin", data(5));
            anotherInstance().deleteByKey("/files/a.bin");
            assertArrayEquals(bytes, download.readAllBytes());
        }
        assertEquals(0L, fileRowCount());
    }

    @Test
    public void failedUploadPreservesPublishedContentAndCleansPartialBatch() throws Exception {
        Assume.assumeTrue(backend.equals("web-api"));
        service.toDbFile("/files/a.bin", data(7));
        AtomicInteger chunks = new AtomicInteger();
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.startsWith("INSERT INTO website(name,value) SELECT") && chunks.incrementAndGet() == 2)
                throw new SQLException("Interrupted chunk write");
        });
        assertThrows(SQLException.class, () -> service.toDbFile("/files/a.bin", data(DbFileService.CHUNK_BYTES * 3)));
        assertArrayEquals(data(7), anotherInstance().loadDbFile("/files/a.bin"));
        assertEquals(1L, fileRowCount());
        assertNull(metadata("/files/a.bin").pendingBatch);
    }

    @Test
    public void crashedUploadRemainsInvisibleAndCanBeRecoveredByAnotherInstance() throws Exception {
        Assume.assumeTrue(backend.equals("web-api"));
        AtomicInteger chunks = new AtomicInteger();
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.startsWith("INSERT INTO website(name,value) SELECT") && chunks.incrementAndGet() == 2)
                throw new AssertionError("Simulated process termination");
        });
        assertThrows(AssertionError.class, () -> service.toDbFile("/files/a.bin", data(DbFileService.CHUNK_BYTES * 3)));
        assertTrue(anotherInstance().getDbFiles("/files").isEmpty());
        assertEquals(2L, fileRowCount());
        now.addAndGet(DbFileStore.UPLOAD_TIMEOUT + 1);
        assertTrue(anotherInstance().getDbFiles("/files").isEmpty());
        assertEquals(0L, fileRowCount());
    }

    @Test
    public void deletionFailureRetainsCleanupReferencesForRetry() throws Exception {
        Assume.assumeTrue(backend.equals("web-api"));
        service.toDbFile("/files/a.bin", data(DbFileService.CHUNK_BYTES * 3));
        AtomicInteger deletes = new AtomicInteger();
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.startsWith("DELETE FROM website WHERE name>=") && deletes.incrementAndGet() == 1)
                throw new SQLException("Storage unavailable");
        });
        assertThrows(SQLException.class, () -> service.deleteByKey("/files/a.bin"));
        assertTrue(metadata("/files/a.bin").deleting);
        assertTrue(anotherInstance().deleteByKey("/files/a.bin"));
        assertEquals(0L, fileRowCount());
    }

    @Test
    public void expiresAndFencesAStaleWriterAfterAnotherInstancePublishes() throws Exception {
        Assume.assumeTrue(backend.equals("web-api"));
        AtomicInteger chunks = new AtomicInteger();
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.startsWith("INSERT INTO website(name,value) SELECT") && chunks.incrementAndGet() == 2) {
                now.addAndGet(DbFileStore.UPLOAD_TIMEOUT + 1);
                anotherInstance().toDbFile("/files/a.bin", data(7));
            }
        });
        assertThrows(IllegalStateException.class, () -> service.toDbFile("/files/a.bin", data(DbFileService.CHUNK_BYTES * 3)));
        assertArrayEquals(data(7), anotherInstance().loadDbFile("/files/a.bin"));
        assertEquals(1L, fileRowCount());
    }

    @Test
    public void activeOverwriteKeepsOldVersionVisibleAndRejectsACompetingWriter() throws Exception {
        Assume.assumeTrue(backend.equals("web-api"));
        service.toDbFile("/files/a.bin", data(11));
        AtomicInteger chunks = new AtomicInteger();
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.startsWith("INSERT INTO website(name,value) SELECT") && chunks.incrementAndGet() == 2) {
                assertArrayEquals(data(11), anotherInstance().loadDbFile("/files/a.bin"));
                assertEquals(11, anotherInstance().getDbFiles("/files").get(0).getSize());
                assertThrows(IllegalStateException.class, () -> anotherInstance().toDbFile("/files/a.bin", data(3)));
            }
        });
        byte[] replacement = data(DbFileService.CHUNK_BYTES * 3);
        service.toDbFile("/files/a.bin", replacement);
        assertArrayEquals(replacement, anotherInstance().loadDbFile("/files/a.bin"));
    }

    private DbFileMetadata metadata(String path) throws Exception {
        return new Gson().fromJson(db.queryOne("SELECT remark FROM website WHERE name=?", "db_file" + path).get("remark").toString(), DbFileMetadata.class);
    }

    @Test
    public void failedPublicationDoesNotReplaceTheOldFileOrLeakUploadedChunks() throws Exception {
        Assume.assumeTrue(backend.equals("web-api"));
        service.toDbFile("/files/a.bin", data(8));
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.startsWith("UPDATE website SET value=?,remark=?")) throw new SQLException("Publication failed");
        });
        assertThrows(SQLException.class, () -> service.toDbFile("/files/a.bin", data(DbFileService.CHUNK_BYTES * 2)));
        assertArrayEquals(data(8), anotherInstance().loadDbFile("/files/a.bin"));
        assertEquals(1L, fileRowCount());
    }

    @Test
    public void committedOverwriteRetainsRetiredBatchUntilCleanupCanResume() throws Exception {
        Assume.assumeTrue(backend.equals("web-api"));
        service.toDbFile("/files/a.bin", data(DbFileService.CHUNK_BYTES + 9));
        String old = metadata("/files/a.bin").batch;
        AtomicInteger deletes = new AtomicInteger();
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.startsWith("DELETE FROM website WHERE name>=") && deletes.incrementAndGet() == 1)
                throw new SQLException("Cleanup interrupted");
        });
        byte[] replacement = data(DbFileService.CHUNK_BYTES * 2 + 19);
        assertThrows(SQLException.class, () -> service.toDbFile("/files/a.bin", replacement));
        assertEquals(old, metadata("/files/a.bin").retiredBatch);
        assertArrayEquals(replacement, anotherInstance().loadDbFile("/files/a.bin"));
        assertEquals(0L, batchCount(old));
        assertNull(metadata("/files/a.bin").retiredBatch);
    }

    @Test
    public void directoryDeletionPreventsAnInFlightUploadFromRecreatingChunks() throws Exception {
        Assume.assumeTrue(backend.equals("web-api"));
        AtomicInteger chunks = new AtomicInteger();
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.startsWith("INSERT INTO website(name,value) SELECT") && chunks.incrementAndGet() == 2)
                assertTrue(anotherInstance().deleteByPrefix("/files"));
        });
        assertThrows(IllegalStateException.class, () -> service.toDbFile("/files/a.bin", data(DbFileService.CHUNK_BYTES * 3)));
        assertEquals(0L, fileRowCount());
    }

    @Test
    public void unknownMetadataDoesNotGetTreatedAsAnAbandonedUpload() throws Exception {
        service.toDbFile("/files/a.bin", data(17));
        db.execute("UPDATE website SET remark=? WHERE name=?", "{}", "db_file/files/a.bin");
        assertThrows(IllegalStateException.class, () -> service.getDbFiles("/files"));
        assertEquals(Base64.getEncoder().encodeToString(data(17)), db.scalar("SELECT value FROM website WHERE name=?", "db_file/files/a.bin"));
    }

    private long batchCount(String batch) throws Exception {
        return ((Number) db.scalar("SELECT count(*) FROM website WHERE name>=? AND name<?", DbFileStore.CHUNK_PREFIX + batch + ":", DbFileStore.CHUNK_PREFIX + batch + ";")).longValue();
    }

    private long fileRowCount() throws Exception {
        return ((Number) db.scalar("SELECT count(*) FROM website WHERE substr(name,1,7)='db_file'")).longValue();
    }

    private static byte[] data(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }
}
