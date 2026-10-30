package com.zrlog.admin.business.automation;

import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.common.vo.Version;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class UpgradeExecutionCoordinatorTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() {
        return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"});
    }
    private final String backend;
    private InMemoryZrLogDatabase db;
    private AutomationStore store;
    private AccountAccess actor;
    private UpgradeExecutionCoordinator coordinator;
    private final AtomicLong now = new AtomicLong(System.currentTimeMillis());
    private final AtomicReference<String> installed = new AtomicReference<>("old-build");
    public UpgradeExecutionCoordinatorTest(String backend) { this.backend = backend; }
    @Before public void open() throws Exception {
        db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        store = new AutomationStore(() -> new WebSite(db.dataSource()));
        actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
        var authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
        coordinator = new UpgradeExecutionCoordinator(store, authority, now::get, installed::get);
    }
    @After public void close() throws Exception { if (db != null) db.close(); }

    @Test public void manualAndAutomaticClaimsShareOneDurableReceipt() throws Exception {
        var automaticSnapshot = store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class);
        var manual = coordinator.claimManual(actor, target(), null, "release", null);
        assertEquals("manual", coordinator.view(actor).source);
        assertFalse(store.writeAuthorized(AutomaticUpgradeTaskProvider.KEY, automaticSnapshot, automatic("auto-run"), actor));
        assertThrows(UpgradeExecutionCoordinator.Conflict.class,
                () -> coordinator.claimManual(actor, target(), null, "release", null));
        assertEquals(manual.runId, coordinator.view(actor).id);
    }

    @Test public void anAutomaticSubmissionBlocksManualUpgradeWithoutReview() throws Exception {
        store.writeAuthorized(AutomaticUpgradeTaskProvider.KEY, null, automatic("auto-run"), actor);
        assertThrows(UpgradeExecutionCoordinator.Conflict.class,
                () -> coordinator.claimManual(actor, target(), null, "release", null));
        assertEquals("assistant", coordinator.view(actor).source);
        assertFalse(coordinator.view(actor).canRetry);
    }

    @Test public void unknownManualOutcomeRequiresReviewOfTheExactReceipt() throws Exception {
        var first = coordinator.claimManual(actor, target(), null, "release", null);
        coordinator.finish(first, false);
        assertTrue(coordinator.view(actor).canRetry);
        assertThrows(UpgradeExecutionCoordinator.Conflict.class,
                () -> coordinator.claimManual(actor, target(), null, "release", "different-receipt"));
        var second = coordinator.claimManual(actor, target(), null, "release", first.runId);
        assertNotEquals(first.runId, second.runId);
        assertThrows(UpgradeExecutionCoordinator.Conflict.class,
                () -> coordinator.claimManual(actor, target(), null, "release", first.runId));
        coordinator.finish(first, true);
        assertEquals(second.runId, coordinator.view(actor).id);
        assertEquals("running", store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class).value.phase);
    }

    @Test public void aRunningRequestCannotBeReplacedJustBecauseItIsOld() throws Exception {
        var first = coordinator.claimManual(actor, target(), null, "release", null);
        now.addAndGet(3600000);
        assertFalse(coordinator.view(actor).canRetry);
        assertThrows(UpgradeExecutionCoordinator.Conflict.class,
                () -> coordinator.claimManual(actor, target(), null, "release", first.runId));
        coordinator.finish(first, true);
        assertTrue(coordinator.view(actor).canRetry);
    }

    @Test public void onlyTheMatchingInstalledBuildConfirmsTheReceipt() throws Exception {
        coordinator.claimManual(actor, target(), null, "release", null);
        installed.set("other-build"); assertFalse(coordinator.view(actor).confirmed);
        installed.set("new-build"); assertTrue(coordinator.view(actor).confirmed);
        assertFalse(coordinator.view(actor).canRetry);
    }

    @Test public void dockerRetryKeepsItsIdentityAndRejectsChangedTargetOrChannel() throws Exception {
        var first = coordinator.claimManual(actor, target(), "docker-1234567890", "release", null);
        assertEquals(first.runId, coordinator.claimManual(actor, target(), "docker-1234567890", "release", null).runId);
        assertThrows(UpgradeExecutionCoordinator.Conflict.class,
                () -> coordinator.claimManual(actor, target(), "docker-1234567890", "preview", null));
        Version different = target(); different.setBuildId("another-build");
        assertThrows(UpgradeExecutionCoordinator.Conflict.class,
                () -> coordinator.claimManual(actor, different, "docker-1234567890", "release", null));
    }

    @Test public void revokedAccountCannotAcquireTheUpgradeClaim() throws Exception {
        db.execute("UPDATE user SET enabled=? WHERE userId=?", false, actor.getUserId());
        assertThrows(PermissionErrorException.class,
                () -> coordinator.claimManual(actor, target(), null, "release", null));
        assertNull(store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class));
    }

    @Test public void revokedAccountCannotResumeDockerOrReadTheReceipt() throws Exception {
        coordinator.claimManual(actor, target(), "docker-1234567890", "release", null);
        db.execute("UPDATE user SET enabled=? WHERE userId=?", false, actor.getUserId());
        assertThrows(PermissionErrorException.class,
                () -> coordinator.claimManual(actor, target(), "docker-1234567890", "release", null));
        assertThrows(PermissionErrorException.class, () -> coordinator.view(actor));
    }

    private AutomaticUpgradeTaskProvider.Receipt automatic(String id) {
        var receipt = new AutomaticUpgradeTaskProvider.Receipt();
        receipt.runId = AutomationStore.ownerPrefix(1) + AutomationStore.timeKey(now.get()) + ".00000000-0000-0000-0000-000000000001";
        receipt.ownerId = 1; receipt.buildId = "new-build"; receipt.version = "4.2.0";
        receipt.state = "pending"; receipt.startedAt = now.get(); return receipt;
    }
    private Version target() { var target = new Version(); target.setBuildId("new-build"); target.setVersion("4.2.0"); return target; }
}
