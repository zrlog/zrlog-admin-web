package com.zrlog.admin.business.service;

import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.docker.DockerUpdaterClient;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol.Job;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DockerUpgradeProgressServiceTest {
    private final DockerUpgradeProgressService service = new DockerUpgradeProgressService() {
        @Override protected void awaitNextStatus() { }
    };
    private final Map<String, Object> messages = Map.of("admin.upgrade.docker.succeeded", "Updated",
            "admin.upgrade.docker.failed", "Failed", "admin.upgrade.docker.recovery", "Recovery required");

    @Test public void mapsDurableJobPhasesToExistingUpgradeEventsAndCompletion() throws Exception {
        Client client = new Client(job("pulling"), job("replacing"), job("checking"), job("succeeded"));
        List<UpgradeProgressEvent.Data> events = new ArrayList<>();
        var response = service.watch(client, job("queued"), (name, data) -> {
            assertEquals("upgrade-progress", name);
            events.add((UpgradeProgressEvent.Data) data);
        }, messages);
        assertEquals(List.of("download", "download", "restart", "restart", "complete"),
                events.stream().map(UpgradeProgressEvent.Data::getStage).collect(java.util.stream.Collectors.toList()));
        assertEquals("complete", events.get(events.size() - 1).getStatus());
        assertEquals(0, response.getError());
        assertTrue(response.getData().getFinish());
        assertEquals("Updated", response.getData().getMessage());
        assertEquals(4, client.reads);
    }

    @Test public void terminalFailuresNeverReportSuccessAndRetainRecoveryDetail() throws Exception {
        for (String phase : List.of("failed", "interrupted")) {
            Job job = job(phase); job.recoveryRequired = true;
            var response = service.watch(new Client(), job, (name, data) -> {
                var event = (UpgradeProgressEvent.Data) data;
                assertEquals("error", event.getStage());
                assertEquals("error", event.getStatus());
                assertEquals("Recovery required", event.getDetail());
            }, messages);
            assertNotEquals(0, response.getError());
            assertFalse(response.getData().getFinish());
        }
    }

    @Test public void disconnectedSubscriberStopsReadingWithoutResubmitting() {
        Client client = new Client();
        assertThrows(IOException.class, () -> service.watch(client, job("pulling"),
                (name, data) -> { throw new IOException("Client disconnected"); }, messages));
        assertEquals(0, client.reads);
    }

    @Test public void rejectsAChangedTaskBeforeEmittingItsProgress() {
        for (String field : List.of("id", "version", "buildId", "channel")) {
            Job other = job("succeeded");
            switch (field) {
                case "id": other.id = "other-request-12345678"; break;
                case "version": other.version = "5.0.0"; break;
                case "buildId": other.buildId = "7654321"; break;
                case "channel": other.channel = "release"; break;
            }
            List<Object> events = new ArrayList<>();
            assertThrows(IllegalStateException.class, () -> service.watch(new Client(other), job("queued"),
                    (name, data) -> events.add(data), messages));
            assertEquals(1, events.size());
        }
    }

    @Test public void missingTaskProducesAnEmptyResponseWithoutPolling() throws Exception {
        Client client = new Client();
        assertNull(service.watch(client, null, (name, data) -> fail("No progress for missing job"), messages).getData());
        assertEquals(0, client.reads);
    }

    private static Job job(String phase) {
        Job job = new Job(); job.id = "browser-request-1234"; job.version = "4.0.0-SNAPSHOT";
        job.buildId = "1234567"; job.channel = "preview"; job.phase = phase;
        return job;
    }
    private static class Client extends DockerUpdaterClient {
        private final List<Job> jobs;
        private int reads;
        Client(Job... jobs) {
            super("http://updater:8081", "test_token_123456789012345678901234", "https://dl.example", "docker.json");
            this.jobs = List.of(jobs);
        }
        @Override public Job status(String id) { assertEquals("browser-request-1234", id); return jobs.get(reads++); }
        @Override public Job submit(com.zrlog.common.updater.docker.DockerUpdaterProtocol.UpgradeRequest request) {
            throw new AssertionError("Progress subscription must never submit a job");
        }
    }
}
