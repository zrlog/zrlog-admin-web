package com.zrlog.model;

import static org.junit.Assert.*;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.zrlog.admin.data.repository.LogVersionRepository;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import java.sql.SQLException;
import java.util.Date;
import org.junit.Test;

public class LogVersionTest {
    @Test
    public void storesHistoryTimeAndMetadataWithoutMixingArticleAndRowVersions() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            LogVersionRepository repository = new LogVersionRepository(db.dataSource());
            Date time = new Date(1_000);
            assertTrue(repository.savePatch(7, 3, "{\"diff\":true}", 9, "title", time, 2));
            var history = repository.findVersionList(7).get(0);
            assertEquals(3, ((Number) history.articleVersion).intValue());
            assertEquals(2, ((Number) history.fromVersion).intValue());
            assertEquals(1_000L, ResultValueConvertUtils.parseDate(history.createdAt).longValue());
            var row = repository.findById(1L).orElseThrow();
            assertEquals(Long.valueOf(0), row.getVersion());
            assertEquals(1_000L, row.getCreatedAt().getTime());
            assertEquals(1_000L, row.getUpdatedAt().getTime());
            assertThrows(
                    SQLException.class,
                    () -> repository.savePatch(7, 3, "{}", 9, "duplicate", time, 2));
            assertEquals(
                    "{\"diff\":true}",
                    repository.findReversePatchesGreaterThanVersion(7, 2).get(0).getPatchJson());
            assertTrue(repository.deleteByLogIdAndArticleVersion(7, 3));
            assertTrue(repository.findVersionList(7).isEmpty());
        }
    }
}
