package com.zrlog.admin.web;

import static org.junit.Assert.*;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.automation.PluginScheduledTaskProvider;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.automation.AutomationComponent;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.data.repository.UserRepository;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.test.support.TestLogCapture;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FaasAutomationStartupTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void faasDefersDefaultsUntilAnExternalTick() throws Exception {
        File root = temporaryFolder.newFolder("faas");
        File log = new File(root, "verification.log");
        ProcessBuilder builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp",
                        System.getProperty(
                                "surefire.test.class.path", System.getProperty("java.class.path")),
                        FaasAutomationStartupTest.class.getName(),
                        root.getAbsolutePath());
        // Exercise the production mode detection without changing the parent test process.
        builder.environment().put("AWS_LAMBDA_FUNCTION_NAME", "automation-startup-test");
        builder.environment().put("LAMBDA_TASK_ROOT", root.getAbsolutePath());
        builder.environment().remove("DB_PROPERTIES");
        Process child = builder.redirectErrorStream(true).redirectOutput(log).start();
        try {
            assertTrue("FaaS startup verification timed out", child.waitFor(45, TimeUnit.SECONDS));
            assertEquals(Files.readString(log.toPath()), 0, child.exitValue());
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
    }

    public static void main(String[] args) throws Exception {
        assertTrue(EnvKit.isFaaSMode());
        verify(Path.of(args[0], "external"));
        System.exit(0);
    }

    private static void verify(Path root) throws Exception {
        PathUtil.setRootPath(Files.createDirectory(root).toString());
        try (var db = InMemoryZrLogDatabase.openWebApi();
                var logs = TestLogCapture.forClass(AdminWebSetup.class)) {
            try {
                db.execute("UPDATE user SET role=? WHERE userId=?", "owner", 1);
                AtomicInteger defaultReads = new AtomicInteger();
                db.afterWebApiQuery(
                        (sql, params) -> {
                            if (params != null
                                    && Arrays.stream(params)
                                            .anyMatch(
                                                    value ->
                                                            "assistant.v2.defaults".equals(value)
                                                                    || "assistant.v2.plugin-defaults"
                                                                            .equals(value)))
                                defaultReads.incrementAndGet();
                        });
                db.loadAdminWebModules();
                var component = db.component(AutomationComponent.class);
                List<Boolean> ownership = new ArrayList<>();
                PluginCorePlugin core =
                        (PluginCorePlugin)
                                Proxy.newProxyInstance(
                                        FaasAutomationStartupTest.class.getClassLoader(),
                                        new Class[] {PluginCorePlugin.class},
                                        (proxy, method, values) -> {
                                            if (method.getName().equals("supportsHostScheduler"))
                                                return true;
                                            if (method.getName().equals("setExternallyScheduled"))
                                                ownership.add((Boolean) values[0]);
                                            return method.getReturnType() == boolean.class
                                                    ? false
                                                    : null;
                                        });
                db.config().getAllPlugins().add(core);
                var setup = db.config().getWebSetup(AdminWebSetup.class);
                setup.getPlugins();
                db.config().configDatabase();
                setup.getPlugins();
                assertEquals(List.of(true, true), ownership);
                assertEquals(0, defaultReads.get());
                assertEquals(0, component.store().taskCount(0));
                assertFalse(component.plugin().autoStart());
                component.plugin().start();
                assertFalse(component.plugin().isStarted());
                assertTrue(
                        logs.contains(
                                Level.INFO,
                                "Skip assistant maintenance task initialization check in FaaS mode"));
                assertTrue(
                        logs.contains(
                                Level.INFO,
                                "Skip plugin schedule initialization check in FaaS mode"));

                var actor = AccountAccess.from(new UserRepository(db.dataSource()).loadById(1L));
                var view = component.controller().configuration().getData();
                assertTrue(view.settings.faas);
                assertTrue(view.maintenance.systemTasks.isEmpty());
                assertEquals(0, defaultReads.get());
                assertEquals(0, component.store().taskCount(0));

                var issued = component.settings().rotate(actor, 0);
                var trigger = component.settings().authenticateTrigger("Bearer " + issued.token);
                assertEquals(
                        0,
                        component
                                .ticks()
                                .tick("external", trigger.credentialId(), trigger.budgetMillis())
                                .executed);
                assertEquals("success", component.ticks().health("external").status);
                assertTrue(defaultReads.get() > 0);
                assertEquals(5, component.store().taskCount(0));
                var parent =
                        component.store().tasks(0, 100, 0).stream()
                                .filter(
                                        row ->
                                                PluginScheduledTaskProvider.TYPE.equals(
                                                        row.value.type))
                                .findFirst()
                                .orElseThrow();
                component.tasks().setEnabled(actor, parent.value.id, parent.value.revision, false);

                // Later triggers still consult persistent state and preserve user changes.
                defaultReads.set(0);
                component.ticks().tick("external", "test-trigger", 60000);
                assertEquals(1, defaultReads.get());
                assertFalse(component.store().task(parent.value.id).value.enabled);
                assertEquals(5, component.store().taskCount(0));
            } finally {
                db.config().stop();
            }
        }
    }
}
