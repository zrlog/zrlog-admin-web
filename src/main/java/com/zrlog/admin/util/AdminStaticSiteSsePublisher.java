package com.zrlog.admin.util;

import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AdminStaticSiteSsePublisher {

    public static void write(
            HttpResponse response,
            String threadName,
            String errorEvent,
            List<StaticSiteType> siteTypes,
            SseStep beforeCacheTask,
            CacheTask cacheTask,
            SseStep afterCacheTask
    ) throws IOException {
        write(response, threadName, errorEvent, siteTypes, beforeCacheTask, cacheTask, emitter -> {
        }, afterCacheTask);
    }

    public static void write(
            HttpResponse response,
            String threadName,
            String errorEvent,
            List<StaticSiteType> siteTypes,
            SseStep beforeCacheTask,
            CacheTask cacheTask,
            SseStep duringCacheTask,
            SseStep afterCacheTask
    ) throws IOException {
        write(response, threadName, errorEvent, errorEvent, siteTypes, beforeCacheTask, cacheTask, duringCacheTask,
                afterCacheTask);
    }

    public static void write(
            HttpResponse response,
            String threadName,
            String beforeCacheErrorEvent,
            String cacheErrorEvent,
            List<StaticSiteType> siteTypes,
            SseStep beforeCacheTask,
            CacheTask cacheTask,
            SseStep duringCacheTask,
            SseStep afterCacheTask
    ) throws IOException {
        AdminSseEmitter.write(response, threadName, beforeCacheErrorEvent, emitter -> {
            beforeCacheTask.write(emitter);
            try {
                if (StaticSitePlugin.isDisabled()) {
                    cacheTask.run();
                    duringCacheTask.write(emitter);
                    emitter.send("static-sync-skipped", AdminStaticSiteProgress.snapshot(siteTypes));
                } else {
                    emitter.send("static-sync-start", AdminStaticSiteProgress.snapshot(siteTypes));
                    CompletableFuture<Void> cacheFuture = CompletableFuture.runAsync(cacheTask::run);
                    while (!cacheFuture.isDone()) {
                        duringCacheTask.write(emitter);
                        emitter.send("static-progress", AdminStaticSiteProgress.snapshot(siteTypes));
                        Thread.sleep(1000);
                    }
                    cacheFuture.join();
                    duringCacheTask.write(emitter);
                    emitter.send("static-sync-complete", AdminStaticSiteProgress.snapshot(siteTypes));
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                emitter.sendError(cacheErrorEvent, e);
                return;
            }
            afterCacheTask.write(emitter);
        });
    }

    public static void write(
            HttpResponse response,
            String threadName,
            List<StaticSiteType> siteTypes,
            SseStep beforeCacheTask,
            CacheTask cacheTask,
            SseStep afterCacheTask
    ) throws IOException {
        write(response, threadName, "static-error", siteTypes, beforeCacheTask, cacheTask, afterCacheTask);
    }

    @FunctionalInterface
    public interface CacheTask {

        void run();
    }

    @FunctionalInterface
    public interface SseStep {

        void write(AdminSseEmitter emitter) throws Exception;
    }
}
