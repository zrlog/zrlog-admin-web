package com.zrlog.admin.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.zrlog.admin.business.rest.response.StatisticsInfoResponse;
import com.zrlog.admin.util.ServerInfo;
import com.zrlog.admin.util.ServerInfoUtils;
import com.zrlog.common.Constants;
import com.zrlog.model.Comment;
import com.zrlog.model.Log;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class AdminStatisticsService {

    private static final Logger LOGGER = LoggerUtil.getLogger(AdminStatisticsService.class);

    public CompletableFuture<StatisticsInfoResponse> statisticsInfo(Executor executor) {
        return statisticsInfo(executor, true);
    }

    public CompletableFuture<StatisticsInfoResponse> statisticsInfo(Executor executor, boolean includeAuditLogs) {
        return CompletableFuture.supplyAsync(() -> {
            StatisticsInfoResponse info = new StatisticsInfoResponse();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            futures.add(CompletableFuture.runAsync(() -> {
                info.setTypeData(Constants.zrLogConfig.getCacheService().getArticleTypes());
            }, executor));
            futures.add(CompletableFuture.runAsync(() -> {
                info.setTagData(Constants.zrLogConfig.getCacheService().getTags());
            }, executor));
            futures.add(CompletableFuture.runAsync(() -> fillCommentCounts(info), executor));
            futures.add(CompletableFuture.runAsync(() -> fillArticleCounts(info), executor));
            if (includeAuditLogs) {
                futures.add(CompletableFuture.runAsync(() -> {
                    info.setAuditLogs(new AdminAuditService().getRecentLogs());
                }, executor));
            }
            futures.add(CompletableFuture.runAsync(() -> {
                List<ServerInfo> serverInfos = ServerInfoUtils.getServerInfos2();
                for (ServerInfo serverInfo : serverInfos) {
                    if ("usedCacheSpace".equals(serverInfo.getKey())) {
                        info.setUsedCacheSpace(serverInfo.getValue());
                    }
                    if ("usedDiskSpace".equals(serverInfo.getKey())) {
                        info.setUsedDiskSpace(serverInfo.getValue());
                    }
                }
            }, executor));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return info;
        }, executor);
    }

    private void fillCommentCounts(StatisticsInfoResponse info) {
        try {
            Map<String, Object> row = new Comment().queryFirstWithParams(
                    "SELECT count(1) AS totalCount,"
                            + "SUM(CASE WHEN commTime > ? THEN 1 ELSE 0 END) AS todayCount "
                            + "FROM " + Comment.TABLE_NAME,
                    new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            info.setCommCount(toLong(row, "totalCount"));
            info.setToDayCommCount(toLong(row, "todayCount"));
        } catch (SQLException e) {
            info.setCommCount(-1L);
            info.setToDayCommCount(-1L);
            LOGGER.warning("Query comment counts error, " + e.getMessage());
        }
    }

    private void fillArticleCounts(StatisticsInfoResponse info) {
        try {
            Map<String, Object> row = new Log().queryFirstWithParams(
                    "SELECT "
                            + "count(1) AS totalCount,"
                            + "SUM(CASE WHEN l.rubbish = ? AND l.privacy = ? THEN 1 ELSE 0 END) AS publishedCount,"
                            + "SUM(CASE WHEN l.privacy = ? THEN 1 ELSE 0 END) AS privateCount,"
                            + "SUM(CASE WHEN l.rubbish = ? THEN 1 ELSE 0 END) AS draftCount,"
                            + "SUM(l.click) AS clickCount "
                            + "FROM " + Log.TABLE_NAME + " l "
                            + "inner join user u on u.userId = l.userId "
                            + "inner join type t on t.typeId = l.typeId "
                            + "where l.typeId is not null",
                    false, false, true, true);
            info.setArticleCount(toLong(row, "totalCount"));
            info.setDraftCount(toLong(row, "draftCount"));
            info.setPrivateCount(toLong(row, "privateCount"));
            info.setPublishedCount(toLong(row, "publishedCount"));
            info.setClickCount(toLong(row, "clickCount"));
        } catch (SQLException e) {
            info.setArticleCount(0L);
            info.setDraftCount(0L);
            info.setPrivateCount(0L);
            info.setPublishedCount(0L);
            info.setClickCount(-1L);
            LOGGER.warning("Query article counts error, " + e.getMessage());
        }
    }

    private static long toLong(Map<String, Object> row, String key) {
        if (row == null) {
            return 0L;
        }
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toLowerCase(Locale.ROOT));
        }
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}
