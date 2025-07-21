package com.zrlog.admin.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.zrlog.admin.business.rest.response.ArticleStatusCountResponse;
import com.zrlog.admin.business.rest.response.StatisticsInfoResponse;
import com.zrlog.admin.util.ServerInfo;
import com.zrlog.admin.util.ServerInfoUtils;
import com.zrlog.model.Comment;
import com.zrlog.model.Log;
import com.zrlog.common.Constants;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class AdminStatisticsService {

    private static final Logger LOGGER = LoggerUtil.getLogger(AdminStatisticsService.class);

    public CompletableFuture<StatisticsInfoResponse> statisticsInfo(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            StatisticsInfoResponse info = new StatisticsInfoResponse();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            futures.add(CompletableFuture.runAsync(() -> {
                info.setTypeData(Constants.zrLogConfig.getCacheService().getArticleTypes());
            }, executor));
            futures.add(CompletableFuture.runAsync(() -> {
                info.setTagData(Constants.zrLogConfig.getCacheService().getTags());
            }, executor));
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    info.setCommCount(new Comment().count());
                } catch (SQLException e) {
                    info.setCommCount(-1L);
                    LOGGER.warning("Query comment count error," + e.getMessage());
                }
            }, executor));
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    info.setToDayCommCount(new Comment().countToDayComment());
                } catch (SQLException e) {
                    info.setToDayCommCount(-1L);
                    LOGGER.warning("Query to day comment count error, " + e.getMessage());
                }
            }, executor));
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    info.setClickCount(new Log().sumClick().longValue());
                } catch (SQLException e) {
                    info.setClickCount(-1L);
                    LOGGER.warning("Query click count error, " + e.getMessage());
                }
            }, executor));
            futures.add(CompletableFuture.runAsync(() -> {
                ArticleStatusCountResponse counts = new AdminArticleService().getStatusCounts();
                info.setArticleCount(counts.getTotal());
                info.setDraftCount(counts.getDraft());
                info.setPrivateCount(counts.getPrivateCount());
                info.setPublishedCount(counts.getPublished());
            }, executor));
            futures.add(CompletableFuture.runAsync(() -> {
                info.setAuditLogs(new AdminAuditService().getRecentLogs());
            }, executor));
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
}
