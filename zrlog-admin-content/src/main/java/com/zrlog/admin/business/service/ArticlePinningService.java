package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.ArticleNotPinnedException;
import com.zrlog.admin.business.exception.ArticlePinningNotAllowedException;
import com.zrlog.admin.business.rest.response.ArticlePinningEntryResponse;
import com.zrlog.admin.business.rest.response.ArticlePinningResponse;
import com.zrlog.common.exception.ResourceLockedException;
import com.zrlog.common.exception.UnknownException;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.data.repository.LogRepository;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class ArticlePinningService {
    static final String LOCK_KEY = "article-sticky-order";
    private static final long LOCK_WAIT_SECONDS = 20L;
    private final DistributedLock.Factory locks;
    private final jakarta.inject.Provider<LogRepository> logProvider;
    @Inject
    public ArticlePinningService(
            jakarta.inject.Provider<LogRepository> logProvider, DistributedLock.Factory locks) {
        this.locks = locks;

        this.logProvider = logProvider;
    }

    static String buildPersistSql(List<ArticlePinningEntryResponse> items) {
        StringBuilder sql = new StringBuilder("update ").append(LogRepository.TABLE_NAME)
                .append(" set sticky=case when rubbish=? and privacy=? then case logId");
        List<Long> logIds = new ArrayList<>(items.size());
        long priority = items.size();
        for (ArticlePinningEntryResponse item : items) {
            long logId = requirePositiveLogId(item);
            logIds.add(logId);
            sql.append(" when ").append(logId).append(" then ").append(priority--);
        }
        sql.append(" else 0 end else 0 end where sticky>0 or logId in (");
        for (int i = 0; i < logIds.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append(logIds.get(i));
        }
        sql.append(')');
        return sql.toString();
    }

    private static long requirePositiveLogId(ArticlePinningEntryResponse item) {
        Long logId = item.getLogId();
        if (logId == null || logId <= 0) {
            throw new IllegalArgumentException("logId must be a positive number");
        }
        return logId;
    }

    private static ArticlePinningResponse response(List<ArticlePinningEntryResponse> items) {
        ArticlePinningResponse response = new ArticlePinningResponse();
        response.setItems(items);
        return response;
    }

    private static int indexOf(List<ArticlePinningEntryResponse> items, Long logId) {
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(items.get(i).getLogId(), logId)) {
                return i;
            }
        }
        return -1;
    }

    public ArticlePinningResponse list() throws SQLException {
        return response(queryPinnedArticles());
    }

    public ArticlePinningResponse pin(Long logId) throws SQLException {
        return withOrderLock(() -> {
            ArticlePinningEntryResponse target = requirePublicArticle(logId);
            List<ArticlePinningEntryResponse> items = queryPinnedArticles();
            if (indexOf(items, logId) >= 0) {
                return response(items);
            }
            items.add(0, target);
            persist(items);
            return response(queryPinnedArticles());
        });
    }

    public ArticlePinningResponse unpin(Long logId) throws SQLException {
        return withOrderLock(() -> {
            requirePublicArticle(logId);
            List<ArticlePinningEntryResponse> items = queryPinnedArticles();
            int index = indexOf(items, logId);
            if (index < 0) {
                return response(items);
            }
            items.remove(index);
            persist(items);
            return response(queryPinnedArticles());
        });
    }

    public ArticlePinningResponse move(Long logId, String direction) throws SQLException {
        return withOrderLock(() -> {
            requirePublicArticle(logId);
            List<ArticlePinningEntryResponse> items = queryPinnedArticles();
            int index = indexOf(items, logId);
            if (index < 0) {
                throw new ArticleNotPinnedException();
            }
            int targetIndex = "UP".equals(direction.toUpperCase(Locale.ROOT)) ? index - 1 : index + 1;
            if (targetIndex < 0 || targetIndex >= items.size()) {
                return response(items);
            }
            ArticlePinningEntryResponse moved = items.remove(index);
            items.add(targetIndex, moved);
            persist(items);
            return response(queryPinnedArticles());
        });
    }

    <T> T withOrderLock(PinningOperation<T> operation) throws SQLException {
        Lock lock = locks.create(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new ResourceLockedException();
            }
            return operation.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnknownException(e);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    void normalizeOrderLocked() throws SQLException {
        persist(queryPinnedArticles());
    }

    private ArticlePinningEntryResponse requirePublicArticle(Long logId) throws SQLException {
        ArticlePinningEntryResponse row = logProvider.get().queryOne(
                "select logId,title,sticky from log where logId=? and rubbish=? and privacy=?",
                ArticlePinningEntryResponse.class, logId, false, false);
        if (row == null) throw new ArticlePinningNotAllowedException();
        return row;
    }

    private List<ArticlePinningEntryResponse> queryPinnedArticles() throws SQLException {
        return logProvider.get().queryList(
                "select logId,title,sticky from log where rubbish=? and privacy=? and sticky>? order by sticky desc,logId desc",
                ArticlePinningEntryResponse.class, false, false, 0);
    }

    private void persist(List<ArticlePinningEntryResponse> items) throws SQLException {
        if (items.isEmpty()) {
            this.logProvider
                    .get()
                    .execute("update " + LogRepository.TABLE_NAME + " set sticky=0 where sticky>0");
            return;
        }
        this.logProvider.get().execute(buildPersistSql(items), false, false);
    }

    @FunctionalInterface
    interface PinningOperation<T> {

        T run() throws SQLException;
    }
}
