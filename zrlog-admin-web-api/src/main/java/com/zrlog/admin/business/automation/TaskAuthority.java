package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.User;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Singleton
public final class TaskAuthority {
    private final AccountIdentityService identities;
    private final Provider<User> users;

    @Inject
    public TaskAuthority(AccountIdentityService identities, Provider<User> users) {
        this.identities = identities;
        this.users = users;
    }

    public AccountAccess current(AccountAccess actor) {
        // Entry points declare their access action; business operations check the task's own actions.
        return identities.revalidate(actor);
    }

    public AccountAccess restore(StoredTask task) throws SQLException {
        // Site configuration is independent of its editor. Use the current real owner account,
        // so all existing data-layer permission and conditional-write checks still apply.
        AccountAccess actor = task.ownerId == 0 ? siteActor() : AccountAccess.from(users.get().loadById(task.ownerId));
        if (!actor.isEnabled()) throw new PermissionErrorException();
        return actor.restrictActions(task.executionActions);
    }

    public void revalidateExecution(AccountAccess actor) { identities.revalidate(actor); }

    public AccountAccess siteActor() throws SQLException {
        AccountAccess actor = AccountAccess.from(users.get().queryFirstWithParams(
                "SELECT userId,role,enabled,authVersion FROM user WHERE role=? AND enabled=? ORDER BY userId LIMIT 1", "owner", true));
        if (!actor.isOwner()) throw new PermissionErrorException();
        return actor;
    }

    /** Derived from the authenticated account, never from a second assistant grant form. */
    public List<String> actions(AccountAccess actor, AutomationModels.Definition definition) {
        definition.requiredActions.forEach(action -> require(actor, action));
        Set<String> actions = new LinkedHashSet<>(definition.requiredActions);
        for (String action : definition.optionalActions) {
            for (AccountAction candidate : AccountAction.values()) {
                if (candidate.getId().equals(action) && candidate.allowed(actor)) actions.add(action);
            }
        }
        return new ArrayList<>(actions);
    }

    public void access(AccountAccess actor, StoredTask task, AutomationModels.Definition definition) {
        if (task.ownerId != 0 && task.ownerId != actor.getUserId()) throw new PermissionErrorException();
        definition.requiredActions.forEach(action -> require(actor, action));
    }

    public static void require(AccountAccess actor, String actionId) {
        com.zrlog.admin.business.service.AccountPermissionService.requireAction(actor, actionId);
    }
}
