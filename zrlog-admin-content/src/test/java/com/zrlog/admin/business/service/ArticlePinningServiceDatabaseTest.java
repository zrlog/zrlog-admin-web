package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.ArticleNotPinnedException;
import com.zrlog.admin.business.exception.ArticlePinningNotAllowedException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.response.ArticlePinningEntryResponse;
import com.zrlog.admin.business.rest.response.ArticlePinningResponse;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ArticlePinningServiceDatabaseTest {

    @Test
    public void shouldPinMoveUnpinAndCompressPriorities() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            Long first = createArticle("First", false, false);
            Long second = createArticle("Second", false, false);
            Long third = createArticle("Third", false, false);
            ArticlePinningService service = new ArticlePinningService();

            assertIds(service.pin(first), first);
            assertIds(service.pin(second), second, first);
            assertIds(service.pin(second), second, first);
            assertIds(service.move(first, "up"), first, second);
            assertIds(service.move(first, "up"), first, second);
            assertIds(service.move(first, "down"), second, first);
            assertIds(service.unpin(second), first);
            assertIds(service.unpin(second), first);

            assertEquals(1, ((Number) db.scalar("select sticky from log where logId=?", first)).intValue());
            assertEquals(0, ((Number) db.scalar("select sticky from log where logId=?", second)).intValue());
            assertThrows(ArticleNotPinnedException.class, () -> service.move(third, "up"));
        }
    }

    @Test
    public void shouldRejectDraftAndPrivateArticles() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            Long draft = createArticle("Draft", false, true);
            Long privateArticle = createArticle("Private", true, false);
            ArticlePinningService service = new ArticlePinningService();

            assertThrows(ArticlePinningNotAllowedException.class, () -> service.pin(draft));
            assertThrows(ArticlePinningNotAllowedException.class, () -> service.pin(privateArticle));
            assertTrue(service.list().getItems().isEmpty());
        }
    }

    @Test
    public void shouldSerializeConcurrentPinsWithDatabaseLock() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            Long first = createArticle("Concurrent First", false, false);
            Long second = createArticle("Concurrent Second", false, false);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<ArticlePinningResponse> firstResult = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return new ArticlePinningService().pin(first);
                });
                Future<ArticlePinningResponse> secondResult = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return new ArticlePinningService().pin(second);
                });
                ready.await();
                start.countDown();
                firstResult.get();
                secondResult.get();

                List<ArticlePinningEntryResponse> items = new ArticlePinningService().list().getItems();
                assertEquals(Set.of(first, second), items.stream()
                        .map(ArticlePinningEntryResponse::getLogId)
                        .collect(Collectors.toSet()));
                assertEquals(List.of(2L, 1L), items.stream()
                        .map(ArticlePinningEntryResponse::getSticky)
                        .collect(Collectors.toList()));
                assertEquals(2, ((Number) db.scalar("select count(1) from log where sticky>0")).intValue());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    public void shouldPersistMoreThanD1BindingLimitWithNumericLiterals() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            ArticlePinningService service = new ArticlePinningService();
            for (int i = 1; i <= 40; i++) {
                service.pin(createArticle("Pinned " + i, false, false));
            }

            List<ArticlePinningEntryResponse> items = service.list().getItems();

            assertEquals(40, items.size());
            assertEquals("Pinned 40", items.get(0).getTitle());
            assertEquals("Pinned 1", items.get(39).getTitle());
            assertEquals(40L, items.get(0).getSticky().longValue());
            assertEquals(1L, items.get(39).getSticky().longValue());
            assertEquals(40, ((Number) db.scalar(
                    "select count(distinct sticky) from log where sticky>0")).intValue());
            assertEquals(2L, ArticlePinningService.buildPersistSql(items).chars()
                    .filter(value -> value == '?').count());
        }
    }

    private static void assertIds(ArticlePinningResponse response, Long... ids) {
        assertEquals(List.of(ids), response.getItems().stream()
                .map(ArticlePinningEntryResponse::getLogId)
                .collect(Collectors.toList()));
        long expectedPriority = ids.length;
        for (ArticlePinningEntryResponse item : response.getItems()) {
            assertEquals(expectedPriority--, item.getSticky().longValue());
        }
    }

    private static Long createArticle(String title, boolean privacy, boolean rubbish) throws Exception {
        CreateArticleRequest request = new CreateArticleRequest();
        request.setTitle(title);
        request.setAlias(title.toLowerCase().replace(' ', '-'));
        request.setContent("<p>" + title + "</p>");
        request.setMarkdown(title);
        request.setTypeId(1L);
        request.setCanComment(true);
        request.setPrivacy(privacy);
        request.setRubbish(rubbish);
        request.setEditorType("markdown");
        CreateOrUpdateArticleResponse response = new AdminArticleService().create(token(), request);
        return response.getLogId();
    }

    private static AdminTokenVO token() {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(1);
        token.setSessionId("pinning-test");
        return token;
    }
}
