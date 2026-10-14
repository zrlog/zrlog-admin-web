package com.zrlog.admin.business.service;

import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.updater.spi.DockerControl;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;

import java.util.Map;
import java.util.Objects;

/** Adapts durable updater jobs to the application's existing upgrade SSE contract. */
public class DockerUpgradeProgressService {
    public ApiStandardResponse<UpgradeProcessResponse> watch(DockerControl client,
            DockerUpdaterProtocol.Job initial, UpgradeProgressListener listener, Map<String, Object> messages) throws Exception {
        if (initial == null) { return new ApiStandardResponse<>(null); }
        DockerUpdaterProtocol.Job job = initial;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
        while (true) {
            if (job == null || !Objects.equals(initial.id, job.id) || !Objects.equals(initial.buildId, job.buildId)
                    || !Objects.equals(initial.version, job.version) || !Objects.equals(initial.channel, job.channel)) {
                throw new IllegalStateException("Updater returned a different upgrade task");
            }
            boolean failed = "failed".equals(job.phase) || "interrupted".equals(job.phase);
            String stage;
            switch (job.phase) {
                case "queued": case "pulling": stage = UpgradeProgressEvent.STAGE_DOWNLOAD; break;
                case "replacing": case "checking": stage = UpgradeProgressEvent.STAGE_RESTART; break;
                case "succeeded": stage = UpgradeProgressEvent.STAGE_COMPLETE; break;
                case "failed": case "interrupted": stage = UpgradeProgressEvent.STAGE_ERROR; break;
                default: throw new IllegalStateException("Unknown updater phase");
            }
            String message = Objects.toString(messages.get("admin.upgrade.docker." + job.phase), "");
            String detail = failed && job.recoveryRequired
                    ? Objects.toString(messages.get("admin.upgrade.docker.recovery"), "") : null;
            listener.onProgress(UpgradeProgressEvent.EVENT, UpgradeProgressEvent.data(stage,
                    failed ? UpgradeProgressEvent.STATUS_ERROR : job.terminal() ? UpgradeProgressEvent.STATUS_COMPLETE
                            : UpgradeProgressEvent.STATUS_RUNNING, message, detail));
            if (job.terminal()) {
                var result = new ApiStandardResponse<>(new UpgradeProcessResponse(!failed, message), message);
                if (failed) { result.setError(1); }
                return result;
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(Objects.toString(messages.get("admin.upgrade.docker.timeout"), ""));
            }
            // The private updater exposes snapshots; the browser receives the standard upgrade SSE stream.
            awaitNextStatus();
            job = client.status(initial.id);
        }
    }

    protected void awaitNextStatus() throws InterruptedException { Thread.sleep(1000); }
}
