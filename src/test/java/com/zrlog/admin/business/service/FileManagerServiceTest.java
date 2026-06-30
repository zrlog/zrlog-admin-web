package com.zrlog.admin.business.service;

import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.type.FileDirectoryAction;
import com.zrlog.admin.business.type.FileEntryAccess;
import com.zrlog.admin.business.type.FileEntryAction;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FileManagerServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldResolveAllowedFileManagerPaths() throws Exception {
        withRootPath(() -> {
            FileManagerService service = new FileManagerService();

            assertEquals(PathUtil.getStaticFile("/attached").getCanonicalFile(),
                    service.resolveAndValidate(null).getCanonicalFile());
            assertEquals(PathUtil.getStaticFile("/attached").getCanonicalFile(),
                    service.resolveAndValidate("").getCanonicalFile());
            assertEquals(PathUtil.getStaticFile("/attached/a.png").getCanonicalFile(),
                    service.resolveAndValidate("/attached/a.png").getCanonicalFile());
            assertEquals(new File(PathUtil.getRootPath() + "/README.md").getCanonicalFile(),
                    service.resolveAndValidate(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH + "/README.md")
                            .getCanonicalFile());
            assertEquals(new File("/tmp/zrlog-test.txt").getCanonicalFile(),
                    service.resolveAndValidate(AdminConstants.ADMIN_DEV_FILE_SYSTEM_TEMP_URI_BASE_PATH
                            + "/zrlog-test.txt").getCanonicalFile());

            assertThrows(IllegalArgumentException.class, () -> service.resolveAndValidate("../etc/passwd"));
            assertThrows(IllegalArgumentException.class, () -> service.resolveAndValidate("/templates/default"));
        });
    }

    @Test
    public void shouldExposeDirectoryActionsForWritableRoots() {
        FileManagerService service = new FileManagerService();

        assertEquals(List.of(FileDirectoryAction.UPLOAD, FileDirectoryAction.MKDIR),
                service.getDirectoryActions("/attached"));
        assertEquals(List.of(FileDirectoryAction.UPLOAD, FileDirectoryAction.MKDIR),
                service.getDirectoryActions("/attached/images"));
        assertEquals(List.of(FileDirectoryAction.UPLOAD),
                service.getDirectoryActions(AdminConstants.ADMIN_DB_ATTACHED_TMP));
        assertEquals(List.of(FileDirectoryAction.UPLOAD),
                service.getDirectoryActions(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/images"));
        assertTrue(service.getDirectoryActions("/admin/dev/file").isEmpty());
        assertTrue(service.getDirectoryActions(null).isEmpty());
    }

    @Test
    public void shouldDecorateFileEntriesWithAccessAndActions() throws Exception {
        FileManagerService service = new FileManagerService();

        assertTrue(service.search("").isEmpty());

        FileEntryVO library = service.decorateEntry(new FileEntryVO("library", "", "directory", 0, "", 0));
        assertEquals(FileEntryAccess.VIRTUAL, library.getAccess());
        assertTrue(library.getActions().contains(FileEntryAction.OPEN));

        FileEntryVO virtualDirectory = new FileEntryVO("virtual", "/virtual", "directory", 0, "", 0);
        virtualDirectory.setVirtual(true);
        service.decorateEntry(virtualDirectory);
        assertEquals(FileEntryAccess.VIRTUAL, virtualDirectory.getAccess());

        FileEntryVO attachedImage = service.decorateEntry(
                new FileEntryVO("a.png", "/attached/a.png", "file", 10, "image/png", 0));
        assertEquals(FileEntryAccess.PUBLIC_URL, attachedImage.getAccess());
        assertTrue(attachedImage.getActions().contains(FileEntryAction.PREVIEW));
        assertTrue(attachedImage.getActions().contains(FileEntryAction.DOWNLOAD));
        assertTrue(attachedImage.getActions().contains(FileEntryAction.COPY_URL));
        assertTrue(attachedImage.getActions().contains(FileEntryAction.SELECT));
        assertTrue(attachedImage.getActions().contains(FileEntryAction.RENAME));
        assertTrue(attachedImage.getActions().contains(FileEntryAction.DELETE));
        assertTrue(attachedImage.getActions().contains(FileEntryAction.UPDATE_REFERENCES));

        FileEntryVO missingLocal = new FileEntryVO("missing.png", "/attached/missing.png", "file", 0,
                "image/png", 0);
        missingLocal.setMissing(true);
        service.decorateEntry(missingLocal);
        assertEquals(FileEntryAccess.VIRTUAL, missingLocal.getAccess());
        assertEquals(List.of(FileEntryAction.REUPLOAD, FileEntryAction.UPDATE_REFERENCES),
                missingLocal.getActions());

        FileEntryVO missingExternal = new FileEntryVO("missing", "/external/missing.png", "file", 0,
                "image/png", 0);
        missingExternal.setMissing(true);
        service.decorateEntry(missingExternal);
        assertEquals(FileEntryAccess.VIRTUAL, missingExternal.getAccess());
        assertTrue(missingExternal.getActions().isEmpty());

        FileEntryVO external = service.decorateEntry(new FileEntryVO("cdn", "/external/example.com",
                "directory", 0, "", 0));
        assertEquals(FileEntryAccess.VIRTUAL, external.getAccess());
        assertEquals(List.of(FileEntryAction.OPEN, FileEntryAction.UPDATE_REFERENCES), external.getActions());

        FileEntryVO externalUrl = service.decorateEntry(new FileEntryVO("remote.png",
                "https://cdn.example.com/remote.png", "file", 10, "image/png", 0));
        assertEquals(FileEntryAccess.PUBLIC_URL, externalUrl.getAccess());
        assertTrue(externalUrl.getActions().contains(FileEntryAction.COPY_URL));
        assertTrue(externalUrl.getActions().contains(FileEntryAction.SELECT));

        FileEntryVO dbTemp = service.decorateEntry(new FileEntryVO("tmp.txt",
                AdminConstants.ADMIN_DB_ATTACHED_TMP + "/tmp.txt", "file", 10, "text/plain", 0));
        assertEquals(FileEntryAccess.ADMIN_ONLY, dbTemp.getAccess());
        assertTrue(dbTemp.getActions().contains(FileEntryAction.DELETE));
    }

    @Test
    public void shouldCreateRenameDeleteAndReadAttachedFiles() throws Exception {
        withRootPath(() -> {
            FileManagerService service = new FileManagerService();

            assertTrue(service.mkdir("/attached/docs"));
            assertTrue(service.mkdir("/attached/docs"));
            assertFalse(service.mkdir("/admin/dev/file/docs"));

            File file = PathUtil.getStaticFile("/attached/docs/a.txt");
            Files.writeString(file.toPath(), "hello", StandardCharsets.UTF_8);

            assertEquals("hello", service.readContent("/attached/docs/a.txt"));
            assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), service.read("/attached/docs/a.txt"));
            assertTrue(service.rename("/attached/docs/a.txt", "b.txt"));
            assertFalse(file.exists());
            assertTrue(PathUtil.getStaticFile("/attached/docs/b.txt").exists());
            assertFalse(service.rename("/external/example.com/a.txt", "b.txt"));
            assertTrue(service.delete("/attached/docs"));
            assertFalse(PathUtil.getStaticFile("/attached/docs").exists());
            assertFalse(service.delete("/admin/dev/file/missing.txt"));
        });
    }

    @Test
    public void shouldRejectInvalidOrExistingReuploadTargets() throws Exception {
        withRootPath(() -> {
            FileManagerService service = new FileManagerService();
            File upload = temporaryFolder.newFile("upload.png");
            File existing = PathUtil.getStaticFile("/attached/existing.png");
            assertTrue(existing.getParentFile().mkdirs());
            Files.writeString(existing.toPath(), "existing", StandardCharsets.UTF_8);

            assertThrows(IllegalArgumentException.class,
                    () -> service.reuploadMissingLocalResource("/external/existing.png", upload, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> service.reuploadMissingLocalResource("/attached/existing.png", upload, null, null));
        });
    }

    @Test
    public void shouldReadAndDeleteDbTempFilesThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            FileManagerService service = new FileManagerService();
            DbFileService dbFileService = new DbFileService();
            String filePath = AdminConstants.ADMIN_DB_ATTACHED_TMP + "/docs/a.txt";
            String dirPath = AdminConstants.ADMIN_DB_ATTACHED_TMP + "/docs";
            dbFileService.toDbFile(filePath, "db-temp".getBytes(StandardCharsets.UTF_8));

            assertEquals("db-temp", service.readContent(filePath));
            assertTrue(service.delete(filePath));
            assertEquals(null, db.queryOne("select name from website where name=?", "db_file" + filePath));

            dbFileService.toDbFile(dirPath + "/b.txt", "nested".getBytes(StandardCharsets.UTF_8));
            assertTrue(service.delete(dirPath));
            assertEquals(null, db.queryOne("select name from website where name=?", "db_file" + dirPath + "/b.txt"));
        }
    }

    @Test
    public void shouldListSearchAndRefreshReferenceAwareEntriesThroughRealDatabase() throws Exception {
        withRootPath(() -> {
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.putWebsite(WebSiteService.FEATURE_RESOURCE_REFERENCE_ENABLED_KEY, true);
                insertArticle(db, 21,
                        "<p><img src=\"/attached/images/a.png\">"
                                + "<img src=\"/attached/images/missing.png\">"
                                + "<img src=\"https://cdn.example.com/assets/a.png\"></p>",
                        "![local](/attached/images/markdown.png) "
                                + "![external](https://cdn.example.com/assets/b.png)");
                File imageDir = PathUtil.getStaticFile("/attached/images");
                assertTrue(imageDir.mkdirs());
                Files.writeString(new File(imageDir, "a.png").toPath(), "image", StandardCharsets.UTF_8);
                Files.writeString(new File(imageDir, "notes.txt").toPath(), "notes", StandardCharsets.UTF_8);

                FileManagerService service = new FileManagerService();
                List<FileEntryVO> attachedEntries = service.list("/attached/images");
                FileEntryVO referencedImage = findByName(attachedEntries, "a.png");
                List<FileEntryVO> externalDomains = service.list(FileManagerService.EXTERNAL_ROOT);
                List<FileEntryVO> externalFiles = service.list(FileManagerService.EXTERNAL_ROOT + "/cdn.example.com");
                List<FileEntryVO> missingResources = service.listBrokenLocalResourceReferences("");
                List<FileEntryVO> filteredMissingResources = service.listBrokenLocalResourceReferences("missing");
                List<FileEntryVO> searchResults = service.search("notes");

                assertNotNull(referencedImage);
                assertTrue(referencedImage.isReferenced());
                assertEquals(1, referencedImage.getReferenceCount());
                assertTrue(referencedImage.getActions().contains(FileEntryAction.UPDATE_REFERENCES));
                assertEquals(1, externalDomains.size());
                assertEquals("cdn.example.com", externalDomains.get(0).getName());
                assertEquals(1, externalDomains.get(0).getReferenceCount());
                assertEquals(2, externalFiles.size());
                assertTrue(externalFiles.get(0).getActions().contains(FileEntryAction.COPY_URL));
                assertTrue(externalFiles.get(0).getActions().contains(FileEntryAction.SELECT));
                assertEquals(2, missingResources.size());
                assertEquals(1, filteredMissingResources.size());
                assertEquals("/attached/images/missing.png", filteredMissingResources.get(0).getPath());
                assertEquals(1, searchResults.size());
                assertEquals("notes.txt", searchResults.get(0).getName());
                assertTrue(service.refreshReferenceIndex());
            }
        });
    }

    @Test
    public void shouldSkipReferenceAwareEntriesWhenFeatureFlagIsDisabled() throws Exception {
        withRootPath(() -> {
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                insertArticle(db, 22,
                        "<p><img src=\"/attached/images/missing.png\">"
                                + "<img src=\"https://cdn.example.com/assets/a.png\"></p>",
                        "");

                FileManagerService service = new FileManagerService();

                assertTrue(service.list(FileManagerService.EXTERNAL_ROOT).isEmpty());
                assertTrue(service.listBrokenLocalResourceReferences("").isEmpty());
                assertFalse(service.refreshReferenceIndex());
            }
        });
    }

    @Test
    public void shouldProtectLargeTextPreview() throws Exception {
        withRootPath(() -> {
            FileManagerService service = new FileManagerService();
            File file = PathUtil.getStaticFile("/attached/large.txt");
            assertTrue(file.getParentFile().mkdirs());
            Files.write(file.toPath(), new byte[1024 * 1024 + 1]);

            assertEquals("File too large (max 1MB)", service.readContent("/attached/large.txt"));
        });
    }

    private void withRootPath(ThrowingRunnable runnable) throws Exception {
        String previousRootPath = System.getProperty("sws.root.path");
        try {
            System.setProperty("sws.root.path", temporaryFolder.newFolder("zrlog-file-manager").getAbsolutePath());
            runnable.run();
        } finally {
            if (previousRootPath == null) {
                System.clearProperty("sws.root.path");
            } else {
                System.setProperty("sws.root.path", previousRootPath);
            }
        }
    }

    private static void insertArticle(InMemoryZrLogDatabase db, int id, String content, String markdown) throws Exception {
        db.execute("insert into log(logId, alias, canComment, click, version, content, plain_content, markdown, digest,"
                        + " keywords, thumbnail, recommended, releaseTime, last_update_date, title, typeId, userId,"
                        + " hot, rubbish, privacy, editor_type) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(),"
                        + " now(), ?, ?, ?, ?, ?, ?, ?)",
                id, "file-manager-" + id, true, 0, 0, content, "plain " + id, markdown, "Digest " + id,
                "zrlog,file", "", false, "File Manager " + id, 1, 1, false, false, false, "markdown");
    }

    private static FileEntryVO findByName(List<FileEntryVO> entries, String name) {
        return entries.stream()
                .filter(entry -> name.equals(entry.getName()))
                .findFirst()
                .orElse(null);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
