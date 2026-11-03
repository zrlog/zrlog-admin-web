package com.zrlog.admin.data.repository;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.dao.dto.Direction;
import com.hibegin.common.dao.dto.OrderBy;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.admin.data.dto.ArticleCounts;
import com.zrlog.admin.data.dto.CommentCounts;
import com.zrlog.admin.data.dto.CommentDTO;
import com.zrlog.data.repository.LogRepository;
import com.zrlog.data.repository.TypeRepository;
import com.zrlog.model.Comment;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.test.support.ZrLogTestDatabase.DatabaseType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AdminContentRepositoryDatabaseTest {
    private final DatabaseType databaseType;

    public AdminContentRepositoryDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    private static PageRequestImpl page() {
        return new PageRequestImpl(1L, 10L);
    }

    private static void seedContent(ZrLogTestDatabase db) throws Exception {
        db.update("insert into user(userId, email, password, userName, header) values(?, ?, ?, ?, ?)",
                1, "admin@example.com", "password", "admin", "/avatar.png");
        db.update("insert into type(typeId, alias, typeName, remark) values(?, ?, ?, ?)",
                1, "tech", "Tech", "Tech posts");
        db.update("insert into link(linkId, linkName, url, sort, alt, icon, status) values(?, ?, ?, ?, ?, ?, ?)",
                1, "ZrLog", "https://www.zrlog.com", 1, "ZrLog", "link", true);
        db.update("insert into lognav(navId, navName, url, sort, icon) values(?, ?, ?, ?, ?)",
                1, "Home", "/", 1, "home");
        insertArticle(db, 1, "first", "First", "First content", "java,zrlog",
                "2026-01-15 10:00:00", 5, false, false);
        insertArticle(db, 2, "second", "Second", "Second content", "java",
                "2026-01-20 11:00:00", 7, false, false);
        insertArticle(db, 3, "draft", "Draft", "Draft content", "draft",
                "2026-02-01 09:00:00", 0, true, false);
        db.update("insert into comment(commentId, commTime, have_read, userComment, userMail, userHome, userIp,"
                        + " userName, hide, logId, header) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                1, todayAt("09:00:00"), false, "Nice post", "reader@example.com", "https://reader.example.com",
                "127.0.0.1", "Reader", false, 1, "/reader.png");
        db.update("insert into comment(commentId, commTime, have_read, userComment, userMail, userHome, userIp,"
                        + " userName, hide, logId, header) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                2, "2026-01-15 12:00:00", true, "Old post", null, "",
                "127.0.0.2", "Old", false, 2, "");
    }

    private static String todayAt(String time) {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + " " + time;
    }

    private static void insertArticle(ZrLogTestDatabase db, int id, String alias, String title, String content,
                                      String keywords, String releaseTime, int click, boolean rubbish,
                                      boolean privacy) throws Exception {
        db.update("insert into log(logId, alias, canComment, click, version, content, plain_content, markdown,"
                        + " digest, keywords, recommended, releaseTime, last_update_date, title, typeId, userId,"
                        + " hot, rubbish, privacy, editor_type) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                        + " ?, ?, ?, ?, ?, ?)",
                id, alias, true, click, 1, content, content, content, "Digest", keywords, false,
                releaseTime, releaseTime, title, 1, 1, false, rubbish, privacy, "markdown");
    }

    @Test
    public void shouldQueryAdminArticleViewsThroughRealTables() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            db.update("update log set extensions=? where logId=?",
                    "{\"metadata\":{\"topicIds\":[\"tech\"],\"priority\":3}}", 1);
            AdminLogRepository log = new AdminLogRepository(db.dataSource());

            PageData<ArticleBasicDTO> adminPublished = log.find(page(), "First", "tech", "published");
            PageRequestImpl clickAsc = page();
            clickAsc.setOrders(List.of(new OrderBy("click", Direction.ASC)));
            PageRequestImpl defaultSortAsc = page();
            defaultSortAsc.setOrders(List.of(new OrderBy("unknown", Direction.ASC)));
            PageData<ArticleBasicDTO> adminClickAsc = log.find(clickAsc, null, null);
            PageData<ArticleBasicDTO> adminDefaultSortAsc = log.find(defaultSortAsc, null, null);
            ArticleBasicDTO adminDetail = log.findByIdOrAlias(1);
            ArticleBasicDTO adminDetailByLongId = log.findByIdOrAlias(1L);

            assertEquals(3L, log.findMaxId());
            assertEquals(1L, adminPublished.getTotalElements());
            assertEquals("Draft", adminClickAsc.getRows().get(0).getTitle());
            assertEquals("First", adminDefaultSortAsc.getRows().get(0).getTitle());
            assertEquals("First", adminDetail.getTitle());
            assertEquals("First", adminDetailByLongId.getTitle());
            Map<String, Object> metadataExtensions =
                    (Map<String, Object>) adminDetail.getExtensions().get("metadata");
            assertEquals(Collections.singletonList("tech"), metadataExtensions.get("topicIds"));
            assertEquals(3.0d, metadataExtensions.get("priority"));
            assertEquals("admin", adminDetail.getUserName());
            assertEquals(Long.valueOf(1), log.getArticleData().get("2026-01-15"));
            assertEquals(new BigDecimal(12), log.sumClick());
            assertEquals(3L, log.countByTypeId(1));
            assertEquals(3L, log.getCount());
        }
    }

    @Test
    public void shouldFilterAdminArticlesByDraftPrivateAndPublishedStatus() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            insertArticle(db, 4, "private", "Private", "Private content", "secret",
                    "2026-02-02 10:00:00", 1, false, true);
            AdminLogRepository log = new AdminLogRepository(db.dataSource());

            assertEquals(2L, log.find(page(), null, null, "published").getTotalElements());
            assertEquals("Private", log.find(page(), null, null, "private").getRows().get(0).getTitle());
            assertEquals("Draft", log.find(page(), null, null, "draft").getRows().get(0).getTitle());
            assertEquals(4L, log.find(page(), null, null, "unknown").getTotalElements());
            assertEquals("Private", log.findByIdOrAlias("private").getTitle());
        }
    }

    @Test
    public void shouldRestrictArticleListsByAuthorAndPrivateOwner() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            db.update("insert into user(userId, userName) values(?, ?)", 2, "other");
            insertArticle(db, 4, "own-private", "Own Private", "Private content", "secret",
                    "2026-02-02 10:00:00", 1, false, true);
            insertArticle(db, 5, "other-private", "Other Private", "Private content", "secret",
                    "2026-02-03 10:00:00", 1, false, true);
            db.update("update log set userId=? where logId in (?, ?)", 2, 2, 5);
            AdminLogRepository logs = new AdminLogRepository(db.dataSource());

            assertEquals(List.of("Own Private", "Draft", "First"),
                    logs.find(page(), null, null, "", 1).getRows().stream()
                            .map(ArticleBasicDTO::getTitle).collect(Collectors.toList()));
            assertEquals(4L, logs.find(page(), null, null, "", null, 1).getTotalElements());
            assertEquals("Own Private", logs.find(page(), null, null, "private", null, 1)
                    .getRows().get(0).getTitle());
            assertEquals(0L, logs.find(page(), null, null, "private", 2, 1).getTotalElements());
            ArticleCounts own = logs.getCounts(1, 1);
            assertEquals(3L, own.totalCount);
            assertEquals(1L, own.publishedCount);
            assertEquals(1L, own.privateCount);
            assertEquals(1L, own.draftCount);
            assertEquals(6L, own.clickCount);
            ArticleCounts visible = logs.getCounts(null, 1);
            assertEquals(4L, visible.totalCount);
            assertEquals(2L, visible.publishedCount);
            assertEquals(1L, visible.privateCount);
            assertEquals(1L, visible.draftCount);
            assertEquals(13L, visible.clickCount);
            ArticleCounts otherVisible = logs.getCounts(2, 1);
            assertEquals(1L, otherVisible.totalCount);
            assertEquals(0L, otherVisible.privateCount);
            assertEquals(7L, otherVisible.clickCount);
            assertNull(new LogRepository(db.dataSource()).findByIdOrAlias("own-private", true));
        }
    }

    @Test
    public void shouldResolveNumericAliasesAfterMissingIdsAndHandleEmptyTables() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            AdminLogRepository logs = new AdminLogRepository(db.dataSource());
            assertEquals(0L, logs.findMaxId());
            assertEquals(BigDecimal.ZERO, logs.sumClick());
            assertEquals(0L, logs.getCount());
            assertNull(logs.findByIdOrAlias(null));
            seedContent(db);
            db.update("update log set alias=? where logId=?", "999", 3);

            assertEquals("Draft", logs.findByIdOrAlias("999").getTitle());
            assertEquals("Draft", logs.findByIdOrAlias(999L).getTitle());
            assertNull(logs.findByIdOrAlias("missing"));
            assertEquals(0L, logs.find(page(), null, "missing").getTotalElements());
        }
    }

    @Test
    public void shouldAggregateArticleAndCommentCountsForEmptyAndPopulatedTables() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            AdminLogRepository logs = new AdminLogRepository(db.dataSource());
            AdminCommentRepository comments = new AdminCommentRepository(db.dataSource());
            ArticleCounts emptyArticles = logs.getCounts();
            assertEquals(0L, emptyArticles.totalCount);
            assertEquals(0L, emptyArticles.publishedCount);
            assertEquals(0L, emptyArticles.privateCount);
            assertEquals(0L, emptyArticles.draftCount);
            assertEquals(0L, emptyArticles.clickCount);
            CommentCounts emptyComments = comments.getCounts("2026-01-15 12:00:00");
            assertEquals(0L, emptyComments.totalCount);
            assertEquals(0L, emptyComments.todayCount);

            seedContent(db);
            db.update("update comment set commTime=? where commentId=?", "2026-01-16 09:00:00", 1);
            ArticleCounts articleCounts = logs.getCounts();
            assertEquals(3L, articleCounts.totalCount);
            assertEquals(2L, articleCounts.publishedCount);
            assertEquals(0L, articleCounts.privateCount);
            assertEquals(1L, articleCounts.draftCount);
            assertEquals(12L, articleCounts.clickCount);
            CommentCounts commentCounts = comments.getCounts("2026-01-15 12:00:00");
            assertEquals(2L, commentCounts.totalCount);
            assertEquals(1L, commentCounts.todayCount);
        }
    }

    @Test
    public void shouldQueryModerationViewsAndMarkCommentsRead() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            AdminCommentRepository comments = new AdminCommentRepository(db.dataSource());
            PageData<CommentDTO> result = comments.find(page());
            List<Comment> unread = comments.findHaveReadIsFalse();

            assertEquals(Long.valueOf(2), comments.count());
            assertEquals(Long.valueOf(1), comments.countToDayComment());
            assertEquals(2L, result.getTotalElements());
            assertEquals(todayAt("09:00:00"), result.getRows().get(0).getCommTime());
            JsonObject json = new Gson().toJsonTree(result.getRows().get(0)).getAsJsonObject();
            assertEquals(Set.of("id", "userComment", "header", "commTime", "userMail", "userHome",
                    "userIp", "userName", "hide", "logId"), json.keySet());
            assertEquals(1L, json.get("id").getAsLong());
            assertEquals(1L, json.get("logId").getAsLong());
            assertEquals("Nice post", json.get("userComment").getAsString());
            assertEquals("reader@example.com", json.get("userMail").getAsString());
            assertEquals("127.0.0.1", json.get("userIp").getAsString());
            assertEquals("https://reader.example.com", json.get("userHome").getAsString());
            assertEquals("Reader", json.get("userName").getAsString());
            assertEquals("/reader.png", json.get("header").getAsString());
            assertEquals(todayAt("09:00:00"), json.get("commTime").getAsString());
            assertFalse(json.get("hide").getAsBoolean());
            assertEquals(List.of(1L), unread.stream().map(Comment::getId).collect(Collectors.toList()));
            comments.doRead(1);
            assertTrue(ResultValueConvertUtils.toBoolean(
                    db.queryOne("select have_read from comment where commentId=?", 1).get("have_read")));
            assertTrue(comments.findHaveReadIsFalse().isEmpty());
        }
    }

    @Test
    public void shouldHideCommentsOnOtherAuthorsPrivateArticles() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            db.update("update log set privacy=?,userId=? where logId=?", true, 2, 1);
            AdminCommentRepository comments = new AdminCommentRepository(db.dataSource());

            assertEquals(2L, comments.find(page()).getTotalElements());
            assertEquals(1L, comments.find(page(), 1).getTotalElements());
            assertEquals("Old post", comments.find(page(), 1).getRows().get(0).getUserComment());
            assertEquals(2L, comments.find(page(), 2).getTotalElements());
        }
    }

    @Test
    public void shouldPaginateContentSettingsAndCountAllArticlesInAdminCategories() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            var links = new AdminLinkRepository(db.dataSource()).find(page());
            var navigation = new AdminLogNavRepository(db.dataSource()).find(page());
            var categories = new AdminTypeRepository(db.dataSource()).find(page());

            assertEquals(1L, links.getTotalElements());
            assertEquals("ZrLog", links.getRows().get(0).getLinkName());
            assertEquals(1L, navigation.getTotalElements());
            assertEquals("Home", navigation.getRows().get(0).getNavName());
            assertEquals(1L, categories.getTotalElements());
            assertEquals("Tech", categories.getRows().get(0).getTypeName());
            assertEquals(Long.valueOf(3), categories.getRows().get(0).getTypeamount());
            assertEquals(Long.valueOf(2), new TypeRepository(db.dataSource()).findAll().get(0).getTypeamount());
        }
    }
}
