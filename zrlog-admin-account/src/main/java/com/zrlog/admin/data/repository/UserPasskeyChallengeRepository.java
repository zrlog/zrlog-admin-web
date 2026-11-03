package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.entity.OptimisticLockException;
import com.zrlog.data.repository.*;
import com.zrlog.data.repository.BaseRepository;
import com.zrlog.model.UserPasskeyChallenge;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.Map;

public class UserPasskeyChallengeRepository extends BaseRepository<UserPasskeyChallenge, Long> {

    private final DataSourceWrapper database;

    public static final String TABLE_NAME = "user_passkey_challenge";

    @Inject
    public UserPasskeyChallengeRepository(DataSourceWrapper database) {
        super(UserPasskeyChallenge.class, database);
        this.database = database;
    }

    public boolean save(
            String requestId,
            String ceremony,
            Integer userId,
            String requestJson,
            long expiresAt,
            long createdAt)
            throws SQLException {
        deleteExpired(createdAt);
        return insert(requestId, ceremony, userId, requestJson, expiresAt, createdAt);
    }

    public boolean deleteExpired(long now) throws SQLException {
        return execute("delete from " + tableName + " where expiresAt<?", now);
    }

    public long countActive(String ceremony, long now) throws SQLException {
        return ((Number)
                        queryFirstObj(
                                "select count(1) from "
                                        + tableName
                                        + " where ceremony=? and expiresAt>=?",
                                ceremony,
                                now))
                .longValue();
    }

    public boolean insert(
            String requestId,
            String ceremony,
            Integer userId,
            String requestJson,
            long expiresAt,
            long createdAt)
            throws SQLException {
        UserPasskeyChallenge challenge = new UserPasskeyChallenge();
        challenge.setRequestId(requestId);
        challenge.setCeremony(ceremony);
        challenge.setUserId(userId);
        challenge.setRequestJson(requestJson);
        challenge.setExpiresAt(expiresAt);
        new com.hibegin.common.dao.entity.EntityRepository<>(
                        UserPasskeyChallenge.class,
                        database,
                        java.time.Clock.fixed(
                                java.time.Instant.ofEpochMilli(createdAt),
                                java.time.ZoneOffset.UTC))
                .insert(challenge);
        return true;
    }

    public UserPasskeyChallenge consume(String requestId, String ceremony, long now)
            throws SQLException {
        UserPasskeyChallenge challenge =
                findMatching(Map.of("requestId", requestId, "ceremony", ceremony), 1).stream()
                        .findFirst()
                        .orElse(null);
        if (challenge == null || challenge.getExpiresAt() < now) return null;
        try {
            // The loaded expiry was checked above; version CAS consumes precisely that challenge
            // once.
            delete(
                    challenge,
                    Map.of(
                            "requestId",
                            requestId,
                            "ceremony",
                            ceremony,
                            "expiresAt",
                            challenge.getExpiresAt()));
            return challenge;
        } catch (OptimisticLockException consumedOrChanged) {
            return null;
        }
    }
}
