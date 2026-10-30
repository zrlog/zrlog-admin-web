package com.zrlog.business.service;

import com.zrlog.admin.business.automation.TaskExecutionContext;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.HostAutomaticUpgradeOperations;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.Updater;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.updater.aws.AwsCredentials;
import com.zrlog.common.updater.aws.AwsLambdaUpdateConfig;
import com.zrlog.common.vo.Version;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import com.zrlog.updater.faas.FaasProvider;
import com.zrlog.updater.spi.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.SSLSession;
import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Real host adapter, package provider and Lambda handler, with AWS transport replaced locally. */
public class AssistantFaasUpgradeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private InMemoryZrLogDatabase db;
    private WebsiteKvService settings;
    private TaskExecutionContext execution;

    @Before public void open() throws Exception {
        db = InMemoryZrLogDatabase.open();
        db.execute("UPDATE user SET role=? WHERE userId=?", "owner", 1);
        settings = new WebsiteKvService(() -> new WebSite(db.dataSource()));
        var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
        execution = new TaskExecutionContext("local-faas-upgrade", actor,
                System.currentTimeMillis() + 600000, System::currentTimeMillis, () -> { });
    }

    @After public void close() throws Exception { if (db != null) db.close(); }

    @Test public void availabilityFollowsLambdaConfigurationInsteadOfDisablingAllFaas() throws Exception {
        assertTrue(operations(true, true, true, noNetwork()).available());
        assertFalse(operations(false, true, true, noNetwork()).available());
        assertFalse(operations(true, false, true, noNetwork()).available());
        assertFalse(operations(true, true, false, noNetwork()).available());
    }

    @Test public void incompleteConfigurationCannotReportManualGuidanceAsSubmitted() throws Exception {
        var operations = operations(true, false, true, noNetwork());
        assertFalse(operations.submit(target(), execution));
        assertTrue(settings.getString("admin_audit_log").isEmpty());
    }

    @Test public void waitsForDownloadUploadAndLambdaSubmissionBeforeReturning() throws Exception {
        var downloadFinished = new AtomicBoolean();
        var calls = new AtomicInteger();
        var submitted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var transport = new UpdateHttp() {
            public String read(URI uri) { throw new AssertionError("No catalog read during submission"); }
            public int download(URI uri, File file) throws Exception {
                Files.write(file.toPath(), new byte[] {1, 2, 3});
                downloadFinished.set(true);
                return 200;
            }
            public HttpResponse<String> send(HttpRequest request) throws Exception {
                assertTrue(downloadFinished.get());
                int phase = calls.incrementAndGet();
                if (phase == 1) {
                    assertEquals("fixture-bucket.s3.us-east-1.amazonaws.com", request.uri().getHost());
                    return response(request, 200, "");
                }
                assertEquals(2, phase);
                assertEquals("lambda.us-east-1.amazonaws.com", request.uri().getHost());
                submitted.countDown();
                assertTrue("Test must release the blocked response", release.await(5, TimeUnit.SECONDS));
                return response(request, 200, "{\"LastUpdateStatus\":\"InProgress\"}");
            }
        };
        var operations = operations(true, true, true, transport);
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> result = executor.submit(() -> operations.submit(target(), execution));
            assertTrue("Lambda submission should start", submitted.await(5, TimeUnit.SECONDS));
            assertFalse("An in-flight Lambda response must keep the task running", result.isDone());
            release.countDown();
            assertTrue(result.get(5, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
            assertTrue(settings.getString("admin_audit_log").contains("local-faas-upgrade"));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test public void uploadFailureDoesNotSubmitLambdaOrReportSuccess() throws Exception {
        var calls = new AtomicInteger();
        var transport = new UpdateHttp() {
            public String read(URI uri) { throw new AssertionError(); }
            public int download(URI uri, File file) throws Exception {
                Files.write(file.toPath(), new byte[] {1, 2, 3}); return 200;
            }
            public HttpResponse<String> send(HttpRequest request) {
                assertEquals("fixture-bucket.s3.us-east-1.amazonaws.com", request.uri().getHost());
                calls.incrementAndGet(); return response(request, 500, "");
            }
        };
        assertFalse(operations(true, true, true, transport).submit(target(), execution));
        assertEquals(1, calls.get());
    }

    private HostAutomaticUpgradeOperations operations(boolean lambda, boolean configured,
                                                       boolean hasCatalog, UpdateHttp http) throws Exception {
        var builder = new UpdateContext.Builder();
        builder.lambda = lambda;
        builder.updateTemp = temporary.newFolder();
        builder.http = http;
        builder.aws = configured
                ? new AwsLambdaUpdateConfig(new AwsCredentials("fixture-key", "fixture-secret", null),
                    "us-east-1", "fixture-function", "fixture-bucket")
                : new AwsLambdaUpdateConfig(new AwsCredentials(null, null, null), null, null, null);
        var context = builder.build();
        Updater updater = UpdaterHosts.adapt(new UpdateSession(context, new FaasProvider().create(context)));
        var notices = new UpgradeNoticeService(() -> true, settings);
        var upgrades = new UpgradeService(() -> updater, notices, new BackupProtectionService(settings), settings) {
            @Override boolean isFaaSMode() { return true; }
            @Override boolean isDockerMode() { return false; }
            @Override boolean isSystemServiceMode() { return false; }
        };
        var catalog = new UpdateVersionInfoPlugin(notices, () -> "en_US", updater, settings);
        return new HostAutomaticUpgradeOperations(upgrades, settings, () -> hasCatalog ? catalog : null,
                () -> updater, () -> true, new AdminAuditService(settings));
    }

    private static Version target() {
        var version = new Version();
        version.setVersion("99.0.0-SNAPSHOT");
        version.setArtifactVersion("99.0.0-SNAPSHOT");
        version.setBuildId("future-faas-build");
        version.setBuildDate(new Date(System.currentTimeMillis() + 86400000));
        version.setZipDownloadUrl("https://packages.example.invalid/fixture.zip");
        version.setZipFileSize(3);
        version.setZipSha256("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        return version;
    }

    private static UpdateHttp noNetwork() {
        return new UpdateHttp() {
            public String read(URI uri) { throw new AssertionError("Unexpected network access"); }
            public int download(URI uri, File file) { throw new AssertionError("Unexpected download"); }
            public HttpResponse<String> send(HttpRequest request) { throw new AssertionError("Unexpected AWS call"); }
        };
    }

    private static HttpResponse<String> response(HttpRequest request, int status, String body) {
        return new HttpResponse<>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return request; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
            public String body() { return body; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return request.uri(); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
