package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.model.WebSite;
import com.zrlog.util.I18nUtil;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class AdminPageDataFactoryTest {
    @Test
    public void responsesFollowTheirOwnSiteAndResourceVersionWithoutGlobalConfig() throws Exception {
        var previousLanguage = I18nUtil.threadLocal.get();
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            I18nUtil.removeI18n();
            var site = db.unitCacheService().getPublicWebSiteInfo();
            site.setTitle("Site A");
            site.setLanguage("en_US");
            site.setSystem_notification("Maintenance A");
            var version = new AtomicReference<>("build-a");
            var pages =
                    new AdminPageDataFactory(
                            () -> site,
                            version::get,
                            ApplicationServicesFixture.messages(db.dataSource()));
            var otherSite = new InMemoryZrLogDatabase.TestCacheService().getPublicWebSiteInfo();
            otherSite.setTitle("Site B");
            var other =
                    new AdminPageDataFactory(
                            () -> otherSite,
                            () -> "build-b",
                            ApplicationServicesFixture.messages(db.dataSource()));

            JsonObject first = new Gson().toJsonTree(pages.response(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), "content", "", "/admin/system")).getAsJsonObject();
            assertEquals("content", first.get("data").getAsString());
            assertEquals("build-a", first.get("pageBuildId").getAsString());
            assertEquals("Maintenance A", first.get("systemNotification").getAsString());
            assertTrue(first.get("documentTitle").getAsString().contains("Site A"));
            assertTrue(first.get("documentTitle").getAsString().contains("System"));
            assertFalse(first.has("metadata"));
            assertTrue(other.response(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), null, "", "/admin/system").getDocumentTitle().contains("Site B"));

            site.setTitle("Renamed site");
            version.set("build-a-2");
            var next = pages.response(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), null, "", "/admin/unmapped-page");
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
            var pages =
                    new AdminPageDataFactory(
                            db.unitCacheService()::getPublicWebSiteInfo,
                            () -> "",
                            ApplicationServicesFixture.messages(db.dataSource()));
            new MessageCenterStateService(ApplicationServicesFixture.websiteCache(db.dataSource()), () -> new WebSite(db.dataSource()))
                    .markMayHaveUnread();
            assertTrue(pages.response(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), null).getMessageCenter().isHasUnread());
            db.execute("update user set role='editor' where userId=1");
            var response = pages.response(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), null);
            assertFalse(response.getMessageCenter().isHasUnread());
            assertEquals(0, response.getMessageCenter().getRevision());
            assertEquals("", response.getPageBuildId());
        }
    }
}
