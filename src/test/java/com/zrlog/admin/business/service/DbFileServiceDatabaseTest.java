package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.type.FileEntryAccess;
import com.zrlog.admin.business.type.FileEntryAction;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.exception.NotImplementException;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DbFileServiceDatabaseTest {

    @Test
    public void shouldStoreLoadListAndDeleteDbFilesThroughWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            DbFileService service = new DbFileService();

            UploadFileResponse uploaded = service.toDbFile("/assets/readme.txt",
                    "hello".getBytes(StandardCharsets.UTF_8));
            service.toDbFile("/assets/images/logo.png", new byte[]{1, 2, 3});
            List<FileEntryVO> entries = service.getDbFiles("/assets");

            FileEntryVO textFile = entries.stream()
                    .filter(entry -> "readme.txt".equals(entry.getName()))
                    .findFirst()
                    .orElseThrow();
            FileEntryVO imageDir = entries.stream()
                    .filter(entry -> "images".equals(entry.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("/assets/readme.txt", uploaded.getUrl());
            assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8),
                    service.loadDbFile("/assets/readme.txt"));
            assertEquals("file", textFile.getType());
            assertEquals("/assets/readme.txt", textFile.getPath());
            assertTrue(textFile.getSize() > 0);
            assertEquals(FileEntryAccess.ADMIN_ONLY, textFile.getAccess());
            assertTrue(textFile.getActions().contains(FileEntryAction.DOWNLOAD));
            assertEquals("directory", imageDir.getType());
            assertEquals("/assets/images", imageDir.getPath());
            assertTrue(imageDir.getActions().contains(FileEntryAction.OPEN));

            assertTrue(service.deleteByKey("/assets/readme.txt"));
            assertThrows(NotImplementException.class, () -> service.loadDbFile("/assets/readme.txt"));
            assertTrue(service.deleteByPrefix("/assets"));
            assertEquals(null, db.queryOne("select name from website where name=?", "db_file/assets/images/logo.png"));
        }
    }

    @Test
    public void shouldRejectMissingDbFile() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            assertThrows(NotImplementException.class, () -> new DbFileService().loadDbFile("/missing.txt"));
        }
    }
}
