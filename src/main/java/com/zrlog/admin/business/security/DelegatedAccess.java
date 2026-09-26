package com.zrlog.admin.business.security;

import com.zrlog.data.security.AccountAccess;
import java.util.Set;
import java.util.concurrent.Callable;

/** Delegation restrictions apply to the same action checks as a browser session. */
public final class DelegatedAccess {
    private static final ThreadLocal<Set<String>> ACTIONS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> INHERITED = new ThreadLocal<>();
    private DelegatedAccess() { }

    public static boolean active() { return ACTIONS.get() != null; }
    public static boolean restricted() { return active() && !Boolean.TRUE.equals(INHERITED.get()); }
    public static AccountAccess restrict(AccountAccess account) {
        return active() ? account.restrictActions(ACTIONS.get()) : account;
    }
    public static <T> T withIdentity(OAuthModels.Identity identity, Callable<T> task) throws Exception {
        return withPermissions(Set.copyOf(identity.permissions), "inherit".equals(identity.permissionMode), task);
    }
    public static <T> Callable<T> capture(Callable<T> task) {
        Set<String> selected = ACTIONS.get();
        boolean inherited = Boolean.TRUE.equals(INHERITED.get());
        return selected == null ? task : () -> withPermissions(selected, inherited, task);
    }
    private static <T> T withPermissions(Set<String> permissions, boolean inherit, Callable<T> task) throws Exception {
        Set<String> previous = ACTIONS.get();
        Boolean inherited = INHERITED.get();
        // Nested delegation must not widen an existing restriction.
        Set<String> selected = new java.util.HashSet<>(permissions);
        if (previous != null) selected.retainAll(previous);
        ACTIONS.set(Set.copyOf(selected));
        INHERITED.set(inherit && (previous == null || Boolean.TRUE.equals(inherited)));
        try { return task.call(); }
        finally {
            if (previous == null) ACTIONS.remove(); else ACTIONS.set(previous);
            if (inherited == null) INHERITED.remove(); else INHERITED.set(inherited);
        }
    }
}
