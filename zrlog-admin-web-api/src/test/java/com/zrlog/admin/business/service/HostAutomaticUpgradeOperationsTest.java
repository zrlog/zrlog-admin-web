package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import com.zrlog.admin.business.automation.TaskExecutionContext;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.common.vo.Version;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import com.zrlog.updater.docker.DockerImageUpdater;
import com.zrlog.updater.spi.*;
import org.junit.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Exercises the production Docker client against a loopback protocol peer; never touches an engine. */
public class HostAutomaticUpgradeOperationsTest {
    private static final Gson JSON = new Gson();
    private static final String RUN_ID = "0000000000.00000001792910583000.12f6732f-768e-44cb-9c90-0e3bcdcc74cd";

    @Test
    public void previewSubmissionKeepsTheCompleteManifestVersion() throws Exception {
        submit("preview", "4.0.2-SNAPSHOT", false);
    }

    @Test
    public void previewSubmissionUsesArtifactIdentityRegardlessOfDisplayLabel() throws Exception {
        submit("preview", "4.0.2-SNAPSHOT", true);
    }

    @Test
    public void releaseSubmissionKeepsTheManifestVersion() throws Exception {
        submit("release", "4.0.2", false);
    }

    private void submit(String channel, String artifactVersion, boolean changeDisplayLabel) throws Exception {
        var received = new AtomicReference<DockerUpdaterProtocol.UpgradeRequest>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Object body;
            int status;
            if ("/v1/capabilities".equals(exchange.getRequestURI().getPath())) {
                var capabilities = new DockerUpdaterProtocol.Capabilities();
                capabilities.protocolVersion = 1;
                capabilities.ready = true;
                capabilities.metadataBaseUrl = "https://catalog.example.test";
                capabilities.manifestFilename = "docker.version.json";
                body = capabilities;
                status = 200;
            } else {
                var request = JSON.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                        DockerUpdaterProtocol.UpgradeRequest.class);
                received.set(request);
                // The updater requires the exact manifest identity before queuing container replacement.
                if (!artifactVersion.equals(request.version) || !channel.equals(request.channel)
                        || !"target-build".equals(request.buildId)) {
                    var error = new DockerUpdaterProtocol.ErrorResponse();
                    error.code = "version_changed";
                    body = error;
                    status = 409;
                } else {
                    var job = new DockerUpdaterProtocol.Job();
                    job.id = request.requestId;
                    job.version = request.version;
                    job.buildId = request.buildId;
                    job.channel = request.channel;
                    job.phase = "queued";
                    body = job;
                    status = 202;
                }
            }
            byte[] bytes = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try (var db = InMemoryZrLogDatabase.open()) {
            var settings = new WebsiteKvService(() -> new WebSite(db.dataSource()));
            settings.putString("upgradePreview", Boolean.toString("preview".equals(channel)));
            var builder = new UpdateContext.Builder();
            builder.docker = new DockerConnection("http://127.0.0.1:" + server.getAddress().getPort(),
                    "local-test-token-not-a-secret-123456", "https://catalog.example.test", "docker.version.json");
            HttpClient http = HttpClient.newHttpClient();
            builder.http = new UpdateHttp() {
                public String read(URI uri) { throw new AssertionError("Unexpected metadata read"); }
                public int download(URI uri, File target) { throw new AssertionError("Unexpected archive download"); }
                public HttpResponse<String> send(HttpRequest request) throws Exception {
                    return http.send(request, HttpResponse.BodyHandlers.ofString());
                }
            };
            var host = builder.build();
            var docker = new DockerImageUpdater(host);
            var updater = UpdaterHosts.adapt(new UpdateSession(host, docker));
            var operations = new HostAutomaticUpgradeOperations(null, settings, () -> null, () -> updater,
                    () -> false, new AdminAuditService(settings), null);
            var manifest = new Version();
            manifest.setVersion(artifactVersion);
            manifest.setArtifactVersion(artifactVersion);
            manifest.setBuildId("target-build");
            manifest.setChannel(channel);
            Version displayed = UpdateVersionInfoPlugin.normalizeVersionForDisplay(manifest);
            assertEquals(artifactVersion, displayed.getVersion());
            // A presentation change must never alter the identity sent to the updater.
            if (changeDisplayLabel) displayed.setVersion("4.0.2");
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            var context = new TaskExecutionContext(RUN_ID, actor, 600000, () -> 0L, () -> { });

            assertTrue(operations.submit(displayed, context));
            var request = received.get();
            assertNotNull(request);
            assertEquals(artifactVersion, request.version);
            DockerControl.requestId(request.requestId);
            assertTrue(settings.getString("admin_audit_log").contains(RUN_ID));
        } finally {
            server.stop(0);
        }
    }
}
