package com.zrlog.admin.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.zrlog.admin.business.rest.response.StatisticsInfoResponse;
import com.zrlog.admin.data.dto.ArticleCounts;
import com.zrlog.admin.data.dto.CommentCounts;
import com.zrlog.admin.util.ServerInfo;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.util.I18nUtil;
import com.zrlog.admin.util.SystemInfoUtils;
import com.zrlog.common.CacheService;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.admin.data.repository.AdminCommentRepository;
import com.zrlog.admin.data.repository.AdminLogRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

@Singleton
public class AdminStatisticsService {
    private static final Logger LOGGER = LoggerUtil.getLogger(AdminStatisticsService.class);
    private final AdminAuditService adminAuditService;
    private final jakarta.inject.Provider<AdminLogRepository> logProvider;
    private final jakarta.inject.Provider<AdminCommentRepository> commentProvider;
    private final CacheService cacheService;
    private final SystemInfoUtils systemInfo;

    @Inject
    public AdminStatisticsService(
            CacheService cacheService,
            SystemInfoUtils systemInfo,
            AdminAuditService adminAuditService,
            jakarta.inject.Provider<AdminLogRepository> logProvider,
            jakarta.inject.Provider<AdminCommentRepository> commentProvider) {
        this.adminAuditService = adminAuditService;
        this.logProvider = logProvider;
        this.commentProvider = commentProvider;

        this.cacheService = cacheService;
        this.systemInfo = systemInfo;
    }

    public CompletableFuture<StatisticsInfoResponse> statisticsInfo(Executor executor) {
        return statisticsInfo(executor, false);
    }

    public CompletableFuture<StatisticsInfoResponse> statisticsInfo(Executor executor, boolean includeAuditLogs) {
        return statisticsInfo(executor, includeAuditLogs, null);
    }

    public CompletableFuture<StatisticsInfoResponse> statisticsInfo(Executor executor, boolean includeAuditLogs, AccountAccess actor) {
        String language = I18nUtil.getCurrentLocale();
        return CompletableFuture.supplyAsync(
                () -> {
                    StatisticsInfoResponse info = new StatisticsInfoResponse();
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    futures.add(
                            CompletableFuture.runAsync(
                                    () -> {
                                        info.setTypeData(cacheService.getArticleTypes());
                                    },
                                    executor));
                    futures.add(
                            CompletableFuture.runAsync(
                                    () -> {
                                        info.setTagData(cacheService.getTags());
                                    },
                                    executor));
                    futures.add(
                            CompletableFuture.runAsync(() -> fillCommentCounts(info), executor));
                    futures.add(
                            CompletableFuture.runAsync(() -> fillArticleCounts(info), executor));
                    if (includeAuditLogs && actor != null && AccountAction.SYSTEM_MANAGE.allowed(actor)) {
                        futures.add(
                                CompletableFuture.runAsync(
                                        () -> {
                                            try (var ignored = AdminLanguageContext.open(language)) {
                                                info.setAuditLogs(
                                                        this.adminAuditService.getRecentLogs(actor).stream().limit(3)
                                                                .collect(java.util.stream.Collectors.toList()));
                                            }
                                        },
                                        executor));
                    }
                    futures.add(
                            CompletableFuture.runAsync(
                                    () -> {
                                        List<ServerInfo> serverInfos = systemInfo.systemIOInfoVO();
                                        for (ServerInfo serverInfo : serverInfos) {
                                            if ("usedCacheSpace".equals(serverInfo.getKey())) {
                                                info.setUsedCacheSpace(serverInfo.getValue());
                                            }
                                            if ("usedDiskSpace".equals(serverInfo.getKey())) {
                                                info.setUsedDiskSpace(serverInfo.getValue());
                                            }
                                        }
                                    },
                                    executor));
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    return info;
                },
                executor);
    }

    private void fillCommentCounts(StatisticsInfoResponse info) {
        try {
            CommentCounts row = this.commentProvider.get()
                    .getCounts(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            info.setCommCount(row.totalCount);
            info.setToDayCommCount(row.todayCount);
        } catch (SQLException e) {
            info.setCommCount(-1L);
            info.setToDayCommCount(-1L);
            LOGGER.warning("Query comment counts error, " + e.getMessage());
        }
    }

    private void fillArticleCounts(StatisticsInfoResponse info) {
        try {
            ArticleCounts row = this.logProvider.get().getCounts();
            info.setArticleCount(row.totalCount);
            info.setDraftCount(row.draftCount);
            info.setPrivateCount(row.privateCount);
            info.setPublishedCount(row.publishedCount);
            info.setClickCount(row.clickCount);
        } catch (SQLException e) {
            info.setArticleCount(0L);
            info.setDraftCount(0L);
            info.setPrivateCount(0L);
            info.setPublishedCount(0L);
            info.setClickCount(-1L);
            LOGGER.warning("Query article counts error, " + e.getMessage());
        }
    }
}
