package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class MessageCenterOperationServiceTest {
    private static final String KEY = "admin_cache:message_center_operation_notices_v2";

    private String stored(InMemoryZrLogDatabase db) throws Exception {
        return String.valueOf(db.queryOne("SELECT value FROM website WHERE name=?", KEY).get("value"));
    }

    private MessageCenterNoticeResponse.OperationTaskPayload payload(MessageCenterNoticeResponse notice) {
        return (MessageCenterNoticeResponse.OperationTaskPayload) notice.getPayload();
    }

    @Test
    public void storedNoticesFollowTheReaderLanguageWithoutChangingArticleContentOrReadState() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var service = ApplicationServicesFixture.operations(db.dataSource());
            try (var ignored = AdminLanguageContext.open("zh_CN")) {
                service.recordPublishCheckSuccess(7L, "我的原文 🌿", 90, 2);
                assertTrue(payload(service.listOperationNotices().get(0)).getDescription().contains("90"));
            }
            String raw = stored(db);
            assertTrue(raw.contains("\"titleKey\""));
            assertFalse(raw.contains("\"title\""));
            var state = ApplicationServicesFixture.messages(db.dataSource());
            assertTrue(state.current().isHasUnread());
            long revision = state.current().getRevision();
            String key;
            try (var ignored = AdminLanguageContext.open("en_US")) {
                var notice = ApplicationServicesFixture.operations(db.dataSource()).listOperationNotices().get(0);
                key = notice.getTaskKey();
                assertEquals("/article-edit?id=7", payload(notice).getActionPath());
                assertEquals("success", notice.getStatus());
                assertTrue(payload(notice).getDescription().contains("90"));
                assertFalse(payload(notice).getTitle().contains("发布"));
                var data = new Gson().toJsonTree(payload(notice).getPayload()).getAsJsonObject();
                assertEquals("我的原文 🌿", data.get("articleTitle").getAsString());
            }
            try (var ignored = AdminLanguageContext.open("zh_CN")) {
                assertTrue(payload(service.listOperationNotices().get(0)).getTitle().contains("发布"));
            }
            assertEquals(raw, stored(db));
            assertTrue(service.markOperationNoticeRead(key));
            assertTrue(state.current().getRevision() > revision);
            assertTrue(service.listOperationNotices().isEmpty());
            assertFalse(service.markOperationNoticeRead("missing"));
        }
    }

    @Test
    public void failuresKeepControlledKeysAndNeverPersistRawProviderDiagnostics() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var service = ApplicationServicesFixture.operations(db.dataSource());
            service.recordUpgradeResult(new UpgradeProcessResponse(false, "secret upgrade diagnostic"));
            service.recordUpgradeError();
            service.recordBlogStaticSiteSync(false);
            service.recordPublishCheckError(8L, "Article", "secret publish diagnostic");
            String raw = stored(db);
            assertFalse(raw.contains("secret"));
            assertTrue(raw.contains("admin.messageCenter.operation.publishCheck.errorDescription"));
            assertEquals(4, service.listOperationNotices().size());
            assertEquals(3, service.listOperationNotices().stream().filter(n -> "error".equals(n.getStatus())).count());
            for (var notice : service.listOperationNotices()) {
                assertNotEquals("", payload(notice).getDescription());
                assertFalse(payload(notice).getDescription().startsWith("admin."));
            }
        }
    }

    @Test
    public void operationVariantsRetainTheirParametersAndActions() throws Exception {
        try (var db = InMemoryZrLogDatabase.open(); var ignored = AdminLanguageContext.open("en_US")) {
            var service = ApplicationServicesFixture.operations(db.dataSource());
            service.recordReplaceArticleResourceUrl(5, 2, 3);
            service.recordStaticSiteSync(List.of(StaticSiteType.ADMIN), false);
            service.recordUpgradeRestart("success", "build-1");
            String json = new Gson().toJson(service.listOperationNotices());
            assertTrue(json.contains("build-1"));
            assertTrue(json.contains("/file-manager?resourceType"));
            assertTrue(json.contains("/website/upgrade"));
            assertTrue(json.contains("\"updatedFields\":3"));
            assertFalse(json.contains("{buildId}"));
            assertFalse(json.contains("{articles}"));
        }
    }

    @Test
    public void retentionKeepsFiftyRecentNoticesAcrossServiceRecreation() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var service = ApplicationServicesFixture.operations(db.dataSource());
            for (int i = 0; i < 55; i++) service.recordPublishCheckSuccess((long) i, "Article " + i, 90, 2);
            var notices = ApplicationServicesFixture.operations(db.dataSource()).listOperationNotices();
            assertEquals(50, notices.size());
            assertEquals(50, JsonParser.parseString(stored(db)).getAsJsonArray().size());
            for (var notice : notices) service.markOperationNoticeRead(notice.getTaskKey());
            assertTrue(service.listOperationNotices().isEmpty());
        }
    }
}
