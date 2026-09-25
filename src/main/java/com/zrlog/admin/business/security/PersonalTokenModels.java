package com.zrlog.admin.business.security;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import java.util.List;
import java.util.Set;

public final class PersonalTokenModels {
    private PersonalTokenModels() { }

    public static class Create implements Validator {
        public String name;
        public List<String> scopes = List.of("articles:read");
        public int expiresInDays = 30;
        @Override public void doValid() {
            if (name == null || name.trim().isEmpty() || name.length() > 128
                    || name.chars().anyMatch(Character::isISOControl)
                    || !Set.of(7, 30, 90).contains(expiresInDays)
                    || scopes == null || scopes.isEmpty() || scopes.size() > 4
                    || scopes.stream().anyMatch(java.util.Objects::isNull)) throw new ArgsException();
        }
    }

    /** Safe metadata only; never add the secret or its hash to this DTO. */
    public static class Info {
        public String id;
        public int userId;
        public String name;
        public List<String> scopes;
        public String resource;
        public long createdAt;
        public long expiresAt;
        public boolean revoked;
        public boolean expired;
        public boolean invalidated;
    }

    /** Returned only from the authenticated creation request. */
    public static class Created {
        public String token;
        public Info info;
    }
}
