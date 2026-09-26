package com.zrlog.admin.business.service;

import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.security.MemberModels.*;
import com.zrlog.admin.business.security.SecurityStore;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.util.ZrLogUtil;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class MemberService {
    private final SecurityStore store = new SecurityStore();
    public Page list() throws SQLException {
        AccountPermissionService.administrator();
        Page page = new Page();
        page.currentRole = AccountPermissionService.current().getRole();
        page.members = store.withSession(c -> store.list(c, "select userId,userName,email,role,enabled from user order by userId"))
                .stream().map(MemberService::member).collect(Collectors.toList());
        return page;
    }
    public Member create(Create body) throws SQLException {
        AccountPermissionService.administrator();
        checkMutation();
        if (body == null || body.userName == null || !body.userName.matches("[A-Za-z0-9_.-]{2,16}")) throw new ArgsException("userName");
        validateRole(body.role);
        if (body.email != null && body.email.length() > 64) throw new ArgsException("email");
        validatePassword(body.password);
        String name = body.userName.toLowerCase(Locale.ROOT);
        String hash = PasswordHashUtils.hash(SecurityUtils.md5(body.password));
        AccountAccess actor = AccountPermissionService.current();
        return store.withSession(c -> {
            lockMembership(c);
            if (store.one(c, "select userId from user where lower(userName)=?", name) != null) throw new ArgsException("userName");
            // Persist an unset avatar as "" for older NOT NULL schemas; UserService supplies the default image on read.
            if (c.isWebApi()) {
                if (store.update(c, "insert into user (userName,email,header,password,secretKey,role,enabled,authVersion) select ?,?,?,?,?,?,?,? "
                                + "where exists (select 1 from user where userId=? and role=? and enabled=? and authVersion=?) "
                                + "and not exists (select 1 from user where lower(userName)=?)",
                        name, Objects.toString(body.email, ""), "", hash, UUID.randomUUID().toString(), body.role, true, 0,
                        actor.getUserId(), actor.getRole(), true, actor.getAuthVersion(), name) != 1) throw new PermissionErrorException();
            } else {
                store.update(c, "insert into user (userName,email,header,password,secretKey,role,enabled,authVersion) values (?,?,?,?,?,?,?,?)",
                        name, Objects.toString(body.email, ""), "", hash, UUID.randomUUID().toString(), body.role, true, 0);
            }
            return member(store.one(c, "select userId,userName,email,role,enabled from user where userName=?", name));
        });
    }
    public Member update(Update body) throws SQLException {
        AccountPermissionService.administrator();
        checkMutation();
        AccountAccess actor = AccountPermissionService.current();
        if (body == null || body.userId == null || body.enabled == null) throw new ArgsException("userId/enabled");
        validateRole(body.role);
        if (body.password != null && !body.password.isEmpty()) validatePassword(body.password);
        return store.withSession(c -> {
            lockMembership(c);
            Map<String,Object> row = store.one(c, "select * from user where userId=?", body.userId);
            if (row == null || "owner".equals(row.get("role")) || body.userId == actor.getUserId()) throw new PermissionErrorException();
            // Administrators cannot edit other administrators or grant their own rank.
            if (!com.zrlog.data.security.AccountAction.ADMIN_APPOINT.allowed(actor) && ("admin".equals(row.get("role")) || "admin".equals(body.role))) throw new PermissionErrorException();
            if (c.isWebApi()) {
                String password = body.password == null || body.password.isEmpty() ? (String) row.get("password")
                        : PasswordHashUtils.hash(SecurityUtils.md5(body.password));
                if (store.update(c, "update user set role=?,enabled=?,password=?,authVersion=authVersion+1 "
                                + "where userId=? and role=? and authVersion=? "
                                + "and exists (select 1 from user actor where actor.userId=? and actor.role=? and actor.enabled=? and actor.authVersion=?)",
                        body.role, body.enabled, password, body.userId, row.get("role"), row.get("authVersion"),
                        actor.getUserId(), actor.getRole(), true, actor.getAuthVersion()) != 1) throw new PermissionErrorException();
                // authVersion invalidates both OAuth grants and personal tokens in the same statement.
                return member(store.one(c, "select userId,userName,email,role,enabled from user where userId=?", body.userId));
            }
            store.update(c, "update user set role=?,enabled=?,authVersion=authVersion+1 where userId=? and role<>?",
                    body.role, body.enabled, body.userId, "owner");
            if (body.password != null && !body.password.isEmpty()) store.update(c, "update user set password=? where userId=?",
                    PasswordHashUtils.hash(SecurityUtils.md5(body.password)), body.userId);
            store.update(c, "update oauth_grant set revoked=? where userId=?", true, body.userId);
            return member(store.one(c, "select userId,userName,email,role,enabled from user where userId=?", body.userId));
        });
    }
    public void transfer(Transfer body) throws SQLException {
        AccountAccess actor = AccountPermissionService.current();
        checkMutation();
        if (body == null || body.userId == null || body.password == null || !actor.isOwner() || body.userId == actor.getUserId()) throw new PermissionErrorException();
        new UserService().verifyCurrentCredentials(actor.getUserId(), SecurityUtils.md5(body.password), body.mfaCode);
        store.withSession(c -> {
            if (c.isWebApi()) {
                // Materialize eligibility before either row changes. One D1 statement transfers both roles and versions.
                if (store.update(c, "with participants as materialized (select a.userId as oldOwner,b.userId as newOwner "
                                + "from user a inner join user b on b.userId=? where a.userId=? and a.role=? and a.enabled=? "
                                + "and a.authVersion=? and b.enabled=? and b.role<>?) "
                                + "update user set role=case when userId=? then ? else ? end,authVersion=authVersion+1 "
                                + "where userId in (select oldOwner from participants union all select newOwner from participants)",
                        body.userId, actor.getUserId(), "owner", true, actor.getAuthVersion(), true, "owner",
                        actor.getUserId(), "admin", "owner") != 2) throw new PermissionErrorException();
                return null;
            }
            lockMembership(c);
            Map<String,Object> target = store.one(c, "select * from user where userId=?", body.userId);
            if (!AccountAccess.from(target).isEnabled()) throw new PermissionErrorException();
            if (store.update(c, "update user set role=?,authVersion=authVersion+1 where userId=? and role=?", "admin", actor.getUserId(), "owner") != 1) throw new PermissionErrorException();
            store.update(c, "update user set role=?,authVersion=authVersion+1 where userId=?", "owner", body.userId);
            store.update(c, "update oauth_grant set revoked=? where userId=? or userId=?", true, actor.getUserId(), body.userId);
            return null;
        });
    }
    private void lockMembership(SecurityStore.Session c) throws SQLException {
        // A single owner row gives all member mutations a consistent lock order.
        store.lock(c, "update user set authVersion=authVersion where role=?", "owner");
        com.zrlog.common.vo.AdminTokenVO token = com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser();
        AccountAccess current = AccountAccess.from(store.one(c, "select * from user where userId=?", token.getUserId()));
        if (!current.isAdministrator() || current.getAuthVersion() != token.getAuthVersion()) throw new PermissionErrorException();
    }

    private static void checkMutation() { if (ZrLogUtil.isPreviewMode()) throw new PermissionErrorException(); }
    private static void validateRole(String role) {
        if (role == null || !AccountAccess.ROLES.contains(role) || "owner".equals(role)) throw new ArgsException("role");
        if ("admin".equals(role) && !com.zrlog.data.security.AccountAction.ADMIN_APPOINT.allowed(AccountPermissionService.current())) throw new PermissionErrorException();
    }
    private static void validatePassword(String password) { if (password == null || password.length() < 12 || password.length() > 256) throw new ArgsException("password"); }
    private static Member member(Map<String,Object> row) {
        Member member = new Member();
        member.userId = ((Number) row.get("userId")).intValue();
        member.userName = Objects.toString(row.get("userName"), "");
        member.email = Objects.toString(row.get("email"), "");
        member.role = Objects.toString(row.get("role"), "");
        member.enabled = AccountAccess.truth(row.get("enabled"));
        return member;
    }
}
