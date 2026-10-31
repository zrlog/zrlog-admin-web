package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.AdminComponent;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.User;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class AdminAuditAccessTest {
    @Test
    public void dashboardWorkerUsesTheViewingUsersLanguage() throws Exception {
        var executor = java.util.concurrent.Executors.newCachedThreadPool();
        try (var db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            var component = db.component(AdminComponent.class);
            var settings = db.component(com.zrlog.business.di.ServiceComponent.class).websiteKv();
            settings.putString("admin_audit_log", "[{\"action\":\"EXECUTE_UPGRADE\",\"source\":\"assistant\",\"assistantRunId\":\"localized\"}]");
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            for (String language : new String[]{"en_US", "zh_CN", "en_US"}) {
                try (var ignored = com.zrlog.admin.util.AdminLanguageContext.open(language)) {
                    var log = component.statistics().statisticsInfo(executor, true, actor).get().getAuditLogs().get(0);
                    assertEquals("zh_CN".equals(language) ? "助手执行" : "Run by assistant", log.getSourceLabel());
                    assertEquals("zh_CN".equals(language) ? "执行系统更新" : "Executed system update", log.getAction());
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void onlyAdministratorsCanReadFullLogsAndDashboardReturnsThreeEntries() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            var component = db.component(AdminComponent.class);
            var settings = db.component(com.zrlog.business.di.ServiceComponent.class).websiteKv();
            settings.putString("admin_audit_log", "[{\"action\":\"REFRESH_CACHE\",\"content\":\"one\"},"
                    + "{\"action\":\"REFRESH_CACHE\",\"content\":\"two\"},"
                    + "{\"action\":\"REFRESH_CACHE\",\"content\":\"three\"},"
                    + "{\"action\":\"REFRESH_CACHE\",\"content\":\"four\"}]");
            var audit = new AdminAuditService(settings);
            for (String role : AccountAccess.ROLES) {
                db.execute("UPDATE user SET role=? WHERE userId=1", role);
                var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
                if (actor.isAdministrator()) {
                    assertEquals(4, audit.getRecentLogs(actor).size());
                    var summary = component.statistics().statisticsInfo(Runnable::run, true, actor).get().getAuditLogs();
                    assertEquals(3, summary.size());
                    assertEquals("one", summary.get(0).getContent());
                    assertEquals("three", summary.get(2).getContent());
                } else {
                    assertThrows(PermissionErrorException.class, () -> audit.getRecentLogs(actor));
                    assertNull(component.statistics().statisticsInfo(Runnable::run, true, actor).get().getAuditLogs());
                }
            }
            db.execute("UPDATE user SET role='owner' WHERE userId=1");
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            var restricted = actor.restrictActions(List.of(AccountAction.DASHBOARD_READ.getId(), AccountAction.SESSION.getId()));
            assertThrows(PermissionErrorException.class, () -> audit.getRecentLogs(restricted));
            assertNull(component.statistics().statisticsInfo(Runnable::run, true, restricted).get().getAuditLogs());
            var token = new AdminTokenVO();
            token.setUserId(1); token.setAuthVersion(actor.getAuthVersion());
            var config = DelegatedAccess.withPermissions(Set.of(AccountAction.DASHBOARD_READ.getId(), AccountAction.SESSION.getId()),
                    false, () -> component.dashboard().getConfig(null, token));
            assertFalse(config.getCards().stream().anyMatch(card -> "auditTrail".equals(card.getId())));
        }
    }
}
