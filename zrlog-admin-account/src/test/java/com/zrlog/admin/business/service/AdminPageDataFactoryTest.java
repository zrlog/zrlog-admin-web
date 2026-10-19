package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.Constants;
import com.zrlog.util.I18nUtil;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AdminPageDataFactoryTest {
    @Test
    public void responsesFollowTheirOwnSiteAndResourceVersionWithoutGlobalConfig() throws Exception {
        var previousLanguage = I18nUtil.threadLocal.get();
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            I18nUtil.removeI18n();
            var site = db.cacheService().getPublicWebSiteInfo();
            site.setTitle("Site A");
            site.setLanguage("en_US");
            site.setSystem_notification("Maintenance A");
            var version = new AtomicReference<>("build-a");
            var pages = new AdminPageDataFactory(() -> site, version::get);
            var otherSite = new InMemoryZrLogDatabase.TestCacheService().getPublicWebSiteInfo();
            otherSite.setTitle("Site B");
            var other = new AdminPageDataFactory(() -> otherSite, () -> "build-b");
            Constants.zrLogConfig = null;
            AdminConstants.adminResource = null;

            JsonObject first = new Gson().toJsonTree(pages.response("content", "", "/admin/system")).getAsJsonObject();
            assertEquals("content", first.get("data").getAsString());
            assertEquals("build-a", first.get("pageBuildId").getAsString());
            assertEquals("Maintenance A", first.get("systemNotification").getAsString());
            assertTrue(first.get("documentTitle").getAsString().contains("Site A"));
            assertTrue(first.get("documentTitle").getAsString().contains("System"));
            assertFalse(first.has("metadata"));
            assertTrue(other.response(null, "", "/admin/system").getDocumentTitle().contains("Site B"));

            site.setTitle("Renamed site");
            version.set("build-a-2");
            var next = pages.response(null, "", "/admin/unmapped-page");
            assertEquals("build-a-2", next.getPageBuildId());
            assertTrue(next.getDocumentTitle().contains("Renamed site"));
            assertFalse(next.getDocumentTitle().contains("Site B"));
            assertEquals("build-a", first.get("pageBuildId").getAsString());
        } finally {
            if (previousLanguage == null) I18nUtil.removeI18n();
            else I18nUtil.threadLocal.set(previousLanguage);
        }
    }

    @Test
    public void editorPagesDoNotExposeAdministratorMessageStatus() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            var pages = new AdminPageDataFactory(db.cacheService()::getPublicWebSiteInfo, () -> "");
            new MessageCenterStateService().markMayHaveUnread();
            assertTrue(pages.response(null).getMessageCenter().isHasUnread());
            db.execute("update user set role='editor' where userId=1");
            var response = pages.response(null);
            assertFalse(response.getMessageCenter().isHasUnread());
            assertEquals(0, response.getMessageCenter().getRevision());
            assertEquals("", response.getPageBuildId());
        }
    }
}
