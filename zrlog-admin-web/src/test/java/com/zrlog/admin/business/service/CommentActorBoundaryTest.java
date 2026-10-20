package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.hibegin.common.dao.dto.PageRequestImpl;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.ReadCommentRequest;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class CommentActorBoundaryTest {
    @Parameterized.Parameters(name = "{0}")
    public static List<String> databases() { return List.of("h2", "sqlite"); }

    private final String database;

    public CommentActorBoundaryTest(String database) { this.database = database; }

    @Test
    public void explicitEditorFiltersReadsAndWritesWithoutBorrowingTheThreadAdministrator() throws Exception {
        try (var db = open()) {
            seed(db);
            var accounts = db.component(AccountComponent.class);
            var service = db.component(ContentComponent.class).comments();
            var editor = accounts.identities().account(token(2));
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            var page = service.page(editor, new PageRequestImpl(1L, 10L));
            assertEquals(Set.of(1L, 3L), page.getRows().stream()
                    .map(row -> row.getId().longValue()).collect(Collectors.toSet()));
            assertThrows(PermissionErrorException.class, () -> service.read(editor, read(2)));
            assertThrows(PermissionErrorException.class, () -> service.delete(editor, new String[]{"1", "2"}));
            assertEquals(3, count(db, "select count(*) from comment"));
            AdminTokenThreadLocal.remove();
            assertEquals(0, service.read(editor, read(3)).getError());
            service.readAll(editor);
            assertEquals(1, count(db, "select count(*) from comment where have_read=false"));
            assertFalse(AccountAccess.truth(db.scalar("select have_read from comment where commentId=2")));
            assertTrue(service.delete(editor, new String[]{"1", "3"}).getData().getDelete());
            assertEquals(1, count(db, "select count(*) from comment"));
            assertEquals("Private owner's comment", db.scalar("select userComment from comment where commentId=2"));
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void serviceRejectsMissingDelegatedPermissionAndStaleAccountsWithoutChangingComments() throws Exception {
        try (var db = open()) {
            seed(db);
            var accounts = db.component(AccountComponent.class);
            var service = db.component(ContentComponent.class).comments();
            var editor = accounts.identities().account(token(2));
            AdminTokenThreadLocal.remove();
            denyEveryOperation(service, editor.restrictActions(Set.of()));
            for (String change : List.of("enabled=false", "role='author'", "authVersion=1")) {
                db.execute("update user set enabled=true,role='editor',authVersion=0 where userId=2");
                db.execute("update user set " + change + " where userId=2");
                denyEveryOperation(service, editor);
            }
            assertEquals(3, count(db, "select count(*) from comment"));
            assertEquals(3, count(db, "select count(*) from comment where have_read=false"));
        }
    }

    private static void denyEveryOperation(AdminCommentService service, AccountAccess actor) {
        assertThrows(PermissionErrorException.class, () -> service.page(actor, new PageRequestImpl(1L, 10L)));
        assertThrows(PermissionErrorException.class, () -> service.read(actor, read(1)));
        assertThrows(PermissionErrorException.class, () -> service.readAll(actor));
        assertThrows(PermissionErrorException.class, () -> service.delete(actor, new String[]{"1"}));
    }

    static void seed(InMemoryZrLogDatabase db) throws Exception {
        db.execute("insert into user(userId,userName,role) values(2,'editor','editor')");
        db.execute("insert into log(logId,title,userId,privacy,rubbish) values(1,'Public',1,false,false)");
        db.execute("insert into log(logId,title,userId,privacy,rubbish) values(2,'Private owner',1,true,false)");
        db.execute("insert into log(logId,title,userId,privacy,rubbish) values(3,'Private editor',2,true,false)");
        for (int id : List.of(1, 2, 3)) {
            db.execute("insert into comment(commentId,userComment,have_read,logId,commTime) values(?,?,?,?,?)",
                    id, id == 2 ? "Private owner's comment" : "Comment " + id, false, id, "2026-10-20 10:00:00");
        }
    }

    static ReadCommentRequest read(long id) {
        var request = new ReadCommentRequest();
        request.setId(id);
        return request;
    }

    static AdminTokenVO token(int id) {
        var token = new AdminTokenVO();
        token.setUserId(id);
        return token;
    }

    private static int count(InMemoryZrLogDatabase db, String sql) throws Exception {
        return ((Number) db.scalar(sql)).intValue();
    }

    private InMemoryZrLogDatabase open() throws Exception {
        return database.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
    }
}
