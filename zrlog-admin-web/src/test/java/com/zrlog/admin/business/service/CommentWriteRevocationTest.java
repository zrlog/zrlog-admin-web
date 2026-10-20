package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommentWriteRevocationTest {
    @Test
    public void revocationAtTheWebApiStatementPreventsEveryCommentMutation() throws Exception {
        for (String operation : List.of("read", "readAll", "delete")) {
            for (String revocation : List.of("enabled=false", "role='author'", "authVersion=1")) {
                try (var db = InMemoryZrLogDatabase.openWebApi()) {
                    CommentActorBoundaryTest.seed(db);
                    var actor = db.component(AccountComponent.class).identities().account(CommentActorBoundaryTest.token(2));
                    var service = db.component(ContentComponent.class).comments();
                    AdminTokenThreadLocal.remove();
                    AtomicBoolean revoked = new AtomicBoolean();
                    db.beforeWebApiUpdate((sql, params) -> {
                        if ((sql.startsWith("update comment ") || sql.startsWith("delete from comment "))
                                && revoked.compareAndSet(false, true)) {
                            db.execute("update user set " + revocation + " where userId=2");
                        }
                    });
                    assertThrows(operation + ": " + revocation, PermissionErrorException.class, () -> {
                        switch (operation) {
                            case "read": service.read(actor, CommentActorBoundaryTest.read(1)); break;
                            case "readAll": service.readAll(actor); break;
                            case "delete": service.delete(actor, new String[]{"1", "3"}); break;
                            default: throw new AssertionError(operation);
                        }
                    });
                    assertTrue(revoked.get());
                    assertEquals(3, ((Number) db.scalar("select count(*) from comment")).intValue());
                    assertEquals(3, ((Number) db.scalar("select count(*) from comment where have_read=false")).intValue());
                    assertNull(AdminTokenThreadLocal.getUser());
                }
            }
        }
    }
}
