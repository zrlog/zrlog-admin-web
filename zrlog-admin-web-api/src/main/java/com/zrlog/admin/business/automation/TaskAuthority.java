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
        actor = identities.revalidate(actor);
        require(actor, AccountAction.SESSION.getId());
        return actor;
    }

    public AccountAccess restore(StoredTask task) throws SQLException {
        AccountAccess actor = AccountAccess.from(users.get().loadById(task.ownerId));
        if (!actor.isEnabled() || actor.getAuthVersion() != task.ownerAuthVersion
                || !actor.getRole().equals(task.ownerRole)) throw new PermissionErrorException();
        return actor.restrictActions(task.authorizedActions);
    }

    public List<String> grants(AccountAccess actor, AutomationModels.Definition definition, List<String> requested) {
        Set<String> grants = new LinkedHashSet<>(definition.requiredActions);
        if (requested == null) requested = List.of();
        if (definition.requiresAuthorization && !requested.containsAll(definition.requiredActions)) {
            throw new IllegalArgumentException("automation.authorizationRequired");
        }
        for (String action : requested) {
            if (!definition.requiredActions.contains(action) && !definition.optionalActions.contains(action)) {
                throw new IllegalArgumentException("automation.invalidAuthorization");
            }
            grants.add(action);
        }
        grants.forEach(action -> require(actor, action));
        return new ArrayList<>(grants);
    }

    public static void require(AccountAccess actor, String actionId) {
        for (AccountAction action : AccountAction.values()) {
            if (action.getId().equals(actionId)) {
                if (!action.allowed(actor)) throw new PermissionErrorException();
                return;
            }
        }
        throw new IllegalArgumentException("automation.invalidAuthorization");
    }
}
