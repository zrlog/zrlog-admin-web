package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.StatisticsInfoResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.cache.dto.TagDTO;
import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AdminStatisticsServiceDatabaseTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    public void shouldAggregateArticleAndCommentCountsAgainstRealTables() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            insertArticle(db, 1, "Published", false, false, 3);
            insertArticle(db, 2, "Private", false, true, 5);
            insertArticle(db, 3, "Draft", true, false, 7);
            insertComment(db, 1, "CURRENT_TIMESTAMP");
            insertComment(db, 2, "CURRENT_TIMESTAMP");
            insertComment(db, 3, "DATEADD('DAY', -1, CURRENT_TIMESTAMP)");
            db.cacheService().getTags().add(tag("zrlog", 3L));

            StatisticsInfoResponse response = new AdminStatisticsService()
                    .statisticsInfo(DIRECT_EXECUTOR, false)
                    .get();

            assertEquals(Long.valueOf(3L), response.getArticleCount());
            assertEquals(Long.valueOf(1L), response.getPublishedCount());
            assertEquals(Long.valueOf(1L), response.getPrivateCount());
            assertEquals(Long.valueOf(1L), response.getDraftCount());
            assertEquals(Long.valueOf(15L), response.getClickCount());
            assertEquals(Long.valueOf(3L), response.getCommCount());
            assertEquals(Long.valueOf(2L), response.getToDayCommCount());
            assertEquals(1, response.getTypeData().size());
            assertEquals("Default", response.getTypeData().get(0).getTypeName());
            assertEquals(1, response.getTagData().size());
            assertEquals("zrlog", response.getTagData().get(0).getText());
            assertNotNull(response.getUsedCacheSpace());
            assertNotNull(response.getUsedDiskSpace());
        }
    }

    @Test
    public void shouldUseFallbackCountsWhenStatisticsQueriesFail() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("drop table comment");
            db.execute("drop table log");

            StatisticsInfoResponse response = new AdminStatisticsService()
                    .statisticsInfo(DIRECT_EXECUTOR, false)
                    .get();

            assertEquals(Long.valueOf(0L), response.getArticleCount());
            assertEquals(Long.valueOf(0L), response.getPublishedCount());
            assertEquals(Long.valueOf(0L), response.getPrivateCount());
            assertEquals(Long.valueOf(0L), response.getDraftCount());
            assertEquals(Long.valueOf(-1L), response.getClickCount());
            assertEquals(Long.valueOf(-1L), response.getCommCount());
            assertEquals(Long.valueOf(-1L), response.getToDayCommCount());
        }
    }

    private static void insertArticle(InMemoryZrLogDatabase db, int id, String title,
                                      boolean rubbish, boolean privacy, int click) throws Exception {
        db.execute("insert into log(logId,alias,title,content,plain_content,markdown,keywords,typeId,userId,"
                        + "rubbish,privacy,canComment,recommended,click,releaseTime,last_update_date,version) "
                        + "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
                id, "article-" + id, title, title + " content", title + " content", title + " markdown",
                title.toLowerCase(), 1, 1, rubbish, privacy, true, false, click, 0);
    }

    private static void insertComment(InMemoryZrLogDatabase db, int id, String commTimeExpression) throws Exception {
        db.execute("insert into comment(commentId,commTime,hide,have_read,userComment,userMail,userName,logId) "
                + "values(" + id + "," + commTimeExpression + ",false,false,'comment','user@example.com','user',1)");
    }

    private static TagDTO tag(String text, long count) {
        TagDTO tag = new TagDTO();
        tag.setText(text);
        tag.setCount(count);
        return tag;
    }
}
