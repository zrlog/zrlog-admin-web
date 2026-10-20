package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.exception.AdminOriginException;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.controller.api.MemberController;
import com.zrlog.common.ZrLogConfig;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemberHttpOriginTest {

    private ZrLogConfig applicationConfig;

    @Test
    public void httpDeploymentsCanCreateEditAndTransferMembers() throws Exception {
        for (String host : List.of("192.168.1.12:8080", "blog.internal", "blog.example:8080")) {
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                applicationConfig = db.config();
                db.cacheService().getPublicWebSiteInfo().setHost(host);
                db.config().getServerConfig().setContextPath("/sub");
                AccountAuthorizationTest.login(db, 1, "owner");
                db.execute("update user set password=? where userId=1", PasswordHashUtils.hash(SecurityUtils.md5("owner-password")));
                String origin = "http://" + host;
                var created = controller(origin, "{\"userName\":\"writer\",\"password\":\"member-password\",\"role\":\"author\"}", db.dataSource()).create();
                int id = created.getData().userId;
                controller(origin, "{\"userId\":" + id + ",\"role\":\"admin\",\"enabled\":true}", db.dataSource()).update();
                assertEquals("admin", db.scalar("select role from user where userId=?", id));
                controller(origin, "{\"userId\":" + id + ",\"password\":\"owner-password\"}", db.dataSource()).transfer();
                assertEquals("owner", db.scalar("select role from user where userId=?", id));
                assertEquals("admin", db.scalar("select role from user where userId=1"));
            }
        }
    }

    @Test
    public void allMemberMutationsStillRejectForeignOrigins() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.cacheService().getPublicWebSiteInfo().setHost("blog.internal");
            AccountAuthorizationTest.login(db, 1, "owner");
            assertThrows(AdminOriginException.class, () -> controller("http://evil.example", "{}", db.dataSource()).create());
            assertThrows(AdminOriginException.class, () -> controller("http://evil.example", "{}", db.dataSource()).update());
            assertThrows(AdminOriginException.class, () -> controller("http://evil.example", "{}", db.dataSource()).transfer());
            assertEquals(1, ((Number) db.scalar("select count(*) from user")).intValue());
        }
    }

    private MemberController controller(String origin, String body, DataSourceWrapper testDataSource) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Map<String, Object> attrs = new HashMap<>();
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(MemberHttpOriginTest.class.getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getHeader": return "Origin".equals(args[0]) ? origin : null;
                        case "getScheme": return URI.create(origin).getScheme();
                        case "getInputStream": return new ByteArrayInputStream(bytes);
                        case "getRequestBodyByteBuffer": return ByteBuffer.wrap(bytes);
                        case "getAttr": return attrs;
                        case "getUri": return "/api/admin/member";
                        case "getHeaderMap": return Map.of("X-Real-IP", "127.0.0.1");
                        case "getRemoteHost": return "127.0.0.1";
                        case "getContextPath": return "/sub";
                        default: return null;
                    }
                });
        MemberController controller =
                new MemberController(
                        ApplicationServicesFixture.backend(applicationConfig),
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(testDataSource),
                                AccountServicesFixture.component(testDataSource).permissions()),
                        AccountServicesFixture.component(testDataSource).members(),
                        ApplicationServicesFixture.audit(testDataSource));
        var field = Controller.class.getDeclaredField("request");
        field.setAccessible(true);
        field.set(controller, request);
        return controller;
    }
}
