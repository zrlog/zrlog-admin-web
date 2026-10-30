package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.Test;

import static org.junit.Assert.*;

public class AutomationSettingsTest {
    @Test
    public void requestBudgetsArePersistedAndValidatedWithFaasManualRunsUsingTheSameLimit() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            var authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
            AccountAccess actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            var service = new AutomationSettingsService(store, authority, () -> 1000, () -> true);
            assertEquals(60000, service.externalBudgetMillis());
            assertEquals(60000, service.manualBudgetMillis());
            assertThrows(IllegalArgumentException.class, () -> service.save(actor, 0, false, false, 0));
            assertThrows(IllegalArgumentException.class, () -> service.save(actor, 0, false, false, 661));
            assertEquals(180, service.save(actor, 0, false, false, 180).externalBudgetSeconds);
            var restarted = new AutomationSettingsService(store, authority, () -> 1000, () -> true);
            assertEquals(180000, restarted.externalBudgetMillis());
            assertEquals(180000, restarted.manualBudgetMillis());
            assertEquals(660000, new AutomationSettingsService(store, authority, () -> 1000, () -> false).manualBudgetMillis());
        }
    }
    @Test
    public void triggerTokenIsHashedRotatableRevocableAndAbsentFromOrdinaryViews() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            AutomationStore store = new AutomationStore(() -> new WebSite(db.dataSource()));
            TaskAuthority authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
            AccountAccess actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            AutomationSettingsService service = new AutomationSettingsService(store, authority, () -> 1000, () -> true);
            assertFalse(service.internalEnabled());
            assertThrows(PermissionErrorException.class, () -> service.authenticate("Bearer missing"));
            AutomationSettingsService.IssuedToken issued = service.rotate(actor, 0);
            assertNotNull(service.authenticate("Bearer " + issued.token));
            String persisted = db.scalar("SELECT value FROM website WHERE name=?", AutomationSettingsService.KEY).toString();
            assertFalse(persisted.contains(issued.token));
            String view = AutomationStore.JSON.toJson(service.read(actor));
            assertFalse(view.contains(issued.token));
            assertFalse(view.contains("tokenHash"));
            assertTrue(service.read(actor).faas);
            assertThrows(PermissionErrorException.class, () -> service.authenticate("Basic " + issued.token));
            assertThrows(PermissionErrorException.class, () -> service.authenticate("Bearer " + issued.token + " extra"));
            assertThrows(IllegalStateException.class, () -> service.rotate(actor, 0));
            AutomationSettingsService.IssuedToken replacement = service.rotate(actor, issued.settings.revision);
            assertThrows(PermissionErrorException.class, () -> service.authenticate("Bearer " + issued.token));
            assertNotNull(service.authenticate("bearer " + replacement.token));
            service.revoke(actor, replacement.settings.revision);
            assertThrows(PermissionErrorException.class, () -> service.authenticate("Bearer " + replacement.token));
            assertFalse(service.read(actor).tokenConfigured);
            assertFalse(service.read(actor).externalEnabled);
        }
    }

    @Test
    public void revokedAccountCannotRotateTokenDuringTheConditionalDatabaseWrite() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            AutomationStore store = new AutomationStore(() -> new WebSite(db.dataSource()));
            TaskAuthority authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
            AccountAccess actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            AutomationSettingsService service = new AutomationSettingsService(store, authority, () -> 1000, () -> false);
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("INSERT INTO website(name,value) SELECT")) {
                    db.execute("UPDATE user SET enabled=? WHERE userId=?", false, 1);
                }
            });
            assertThrows(IllegalStateException.class, () -> service.rotate(actor, 0));
            assertNull(store.read(AutomationSettingsService.KEY, AutomationSettingsService.Settings.class));
        }
    }
}
