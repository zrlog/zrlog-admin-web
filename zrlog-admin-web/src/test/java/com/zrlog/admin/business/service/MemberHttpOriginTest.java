package com.zrlog.admin.business.service;

import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.exception.AdminOriginException;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.controller.api.MemberController;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MemberHttpOriginTest {
    @Test
    public void httpDeploymentsCanCreateEditAndTransferMembers() throws Exception {
        for (String host : List.of("192.168.1.12:8080", "blog.internal", "blog.example:8080")) {
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.cacheService().getPublicWebSiteInfo().setHost(host);
                com.zrlog.common.Constants.zrLogConfig.getServerConfig().setContextPath("/sub");
                AccountAuthorizationTest.login(db, 1, "owner");
                db.execute("update user set password=? where userId=1", PasswordHashUtils.hash(SecurityUtils.md5("owner-password")));
                String origin = "http://" + host;
                var created = controller(origin, "{\"userName\":\"writer\",\"password\":\"member-password\",\"role\":\"author\"}").create();
                int id = created.getData().userId;
                controller(origin, "{\"userId\":" + id + ",\"role\":\"admin\",\"enabled\":true}").update();
                assertEquals("admin", db.scalar("select role from user where userId=?", id));
                controller(origin, "{\"userId\":" + id + ",\"password\":\"owner-password\"}").transfer();
                assertEquals("owner", db.scalar("select role from user where userId=?", id));
                assertEquals("admin", db.scalar("select role from user where userId=1"));
            }
        }
    }

    @Test
    public void allMemberMutationsStillRejectForeignOrigins() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.cacheService().getPublicWebSiteInfo().setHost("blog.internal");
            AccountAuthorizationTest.login(db, 1, "owner");
            assertThrows(AdminOriginException.class, () -> controller("http://evil.example", "{}").create());
            assertThrows(AdminOriginException.class, () -> controller("http://evil.example", "{}").update());
            assertThrows(AdminOriginException.class, () -> controller("http://evil.example", "{}").transfer());
            assertEquals(1, ((Number) db.scalar("select count(*) from user")).intValue());
        }
    }

    private static MemberController controller(String origin, String body) throws Exception {
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
        MemberController controller = new MemberController();
        var field = Controller.class.getDeclaredField("request");
        field.setAccessible(true);
        field.set(controller, request);
        return controller;
    }
}
