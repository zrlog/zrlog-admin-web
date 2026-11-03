package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.repository.UserRepository;
import com.zrlog.data.repository.WebSiteRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AutomationTickServiceTest {
    private InMemoryZrLogDatabase db;
    private AutomationStore store;
    private AutomationEngine engine;
    private final AtomicLong now = new AtomicLong(1_790_000_000_000L);

    @Before public void open() throws Exception {
        db = InMemoryZrLogDatabase.openWebApi();
        store = new AutomationStore(() -> new WebSiteRepository(db.dataSource()));
        var authority = new TaskAuthority(new AccountIdentityService(() -> new UserRepository(db.dataSource())), () -> new UserRepository(db.dataSource()));
        engine = new AutomationEngine(store, authority, new TaskRegistry(List::of), now::get);
    }

    @After public void close() throws Exception { if (db != null) db.close(); }

    @Test public void olderCompletionCannotOverwriteANewerTicksHealth() throws Exception {
        var newer = new AutomationTickService(engine, store, now::get);
        var newestId = new AtomicReference<String>();
        var older = new AutomationTickService(engine, store, now::get, null, (budget, receipt) -> {
            now.incrementAndGet();
            newer.tick("external", "new-credential", budget);
            newestId.set(newer.health("external").id);
        });
        older.tick("external", "old-credential", 60000);
        var health = older.health("external");
        assertEquals(newestId.get(), health.id);
        assertEquals("new-credential", health.credentialId);
        assertEquals("success", health.status);
    }

    @Test public void failedInitialHealthClaimCannotFinalizeAnotherTicksRecord() throws Exception {
        var ticks = new AutomationTickService(engine, store, now::get);
        ticks.tick("internal", null, 60000);
        var raced = new AtomicBoolean();
        var newestId = new AtomicReference<String>();
        var initialId = ticks.health("internal").id;
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.equals("UPDATE website SET updatedAt=?,version=version+1,value=? WHERE name=? AND value=?")
                    && "assistant.v1.tick:internal".equals(args[2]) && raced.compareAndSet(false, true)) {
                now.incrementAndGet();
                ticks.tick("internal", null, 60000);
                newestId.set(ticks.health("internal").id);
            }
        });
        ticks.tick("internal", null, 60000);
        assertTrue(raced.get());
        var health = ticks.health("internal");
        assertNotEquals(initialId, health.id);
        assertEquals(newestId.get(), health.id);
        assertEquals("success", health.status);
    }
}
