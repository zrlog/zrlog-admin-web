package com.zrlog.admin.business.service;

import com.hibegin.common.util.SecurityUtils;
import com.zrlog.admin.business.rest.request.LoginRequest;
import com.zrlog.admin.business.security.MemberModels;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class MemberServiceTest {
    @Parameterized.Parameters(name = "{0}")
    public static List<String> backends() {
        return List.of("h2", "sqlite", "webapi");
    }

    @Parameterized.Parameter
    public String backend;

    @Test
    public void createsMemberWithDefaultAvatarWhenExistingSchemaRequiresHeader() throws Exception {
        try (InMemoryZrLogDatabase db = "webapi".equals(backend) ? InMemoryZrLogDatabase.openWebApi()
                : "sqlite".equals(backend) ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open()) {
            requireHeader(db);
            AccountAuthorizationTest.login(db, 1, "owner");

            MemberModels.Create create = new MemberModels.Create();
            create.userName = "NewWriter";
            create.password = "new-member-password";
            create.role = "author";
            MemberModels.Member member = new MemberService().create(create);

            assertTrue(member.userId > 1);
            assertEquals("newwriter", member.userName);
            assertEquals("", member.email);
            assertEquals("author", member.role);
            assertTrue(member.enabled);
            Map<String, Object> row = db.queryOne("select header,authVersion from user where userId=?", member.userId);
            assertEquals("", row.get("header"));
            assertEquals(0, ((Number) row.get("authVersion")).intValue());

            UserService users = new UserService();
            String avatar = users.getBasicUserInfo(member.userId, "new-member-session").getHeader();
            String dataUriPrefix = "data:image/gif;base64,";
            assertTrue(avatar.startsWith(dataUriPrefix));
            try (InputStream image = UserService.class.getResourceAsStream("/assets/admin/images/default-portrait.gif")) {
                assertNotNull(image);
                assertArrayEquals(image.readAllBytes(), Base64.getDecoder().decode(avatar.substring(dataUriPrefix.length())));
            }
            LoginRequest login = new LoginRequest();
            login.setUserName(create.userName);
            login.setPassword(SecurityUtils.md5(create.password));
            assertEquals(avatar, users.login(login).getUserBasicInfoResponse().getHeader());
            assertEquals("", db.scalar("select header from user where userId=?", member.userId));
        }
    }

    private void requireHeader(InMemoryZrLogDatabase db) throws Exception {
        if ("h2".equals(backend)) {
            db.execute("alter table user alter column header set not null");
            return;
        }
        // Existing installations can have a stricter user table than the current install schema.
        // SQLite requires rebuilding the table to change a column's nullability.
        String schema = (String) db.scalar("select sql from sqlite_master where type='table' and name='user'");
        String strictSchema = schema.replaceFirst("(?i)[`\"]?header[`\"]?\\s+[^,]+", "header varchar(255) not null");
        assertNotEquals(schema, strictSchema);
        db.execute("alter table user rename to user_nullable_header");
        db.execute(strictSchema);
        db.execute("insert into user select * from user_nullable_header");
        Map<String, Object> header = db.queryList("pragma table_info(user)").stream()
                .filter(column -> "header".equals(column.get("name"))).findFirst().orElseThrow();
        assertEquals(1, ((Number) header.get("notnull")).intValue());
        assertNull(header.get("dflt_value"));
    }
}
