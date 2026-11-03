package com.zrlog.admin.data.repository;

import com.zrlog.model.UserPasskeyChallenge;
import com.zrlog.data.repository.BaseRepository;

import com.zrlog.data.repository.*;


import com.hibegin.common.dao.DataSourceWrapper;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.Map;

public class UserPasskeyChallengeRepository extends BaseRepository<UserPasskeyChallenge, Long> {

    public static final String TABLE_NAME = "user_passkey_challenge";

    @Inject
    public UserPasskeyChallengeRepository(DataSourceWrapper database) {
        super(UserPasskeyChallenge.class, database);
    }

    public boolean save(String requestId, String ceremony, Integer userId, String requestJson,
                        long expiresAt, long createdAt) throws SQLException {
        deleteExpired(createdAt);
        return insert(requestId, ceremony, userId, requestJson, expiresAt, createdAt);
    }

    public boolean deleteExpired(long now) throws SQLException {
        return execute("delete from " + tableName + " where expiresAt<?", now);
    }

    public long countActive(String ceremony, long now) throws SQLException {
        return ((Number) queryFirstObj("select count(1) from " + tableName
                + " where ceremony=? and expiresAt>=?", ceremony, now)).longValue();
    }

    public boolean insert(String requestId, String ceremony, Integer userId, String requestJson,
                          long expiresAt, long createdAt) throws SQLException {
        return execute("insert into " + tableName
                        + " (requestId,ceremony,userId,requestJson,expiresAt,createdAt,updatedAt,version) values (?,?,?,?,?,?,?,0)",
                requestId, ceremony, userId, requestJson, expiresAt, new java.sql.Timestamp(createdAt).toString(), new java.sql.Timestamp(createdAt).toString());
    }

    public Map<String, Object> consume(String requestId, String ceremony, long now) throws SQLException {
        Map<String, Object> challenge = queryFirstWithParams(
                "select * from " + tableName + " where requestId=? and ceremony=?", requestId, ceremony);
        if (challenge == null) {
            return null;
        }
        boolean consumed = execute("delete from " + tableName
                        + " where requestId=? and ceremony=? and expiresAt>=?",
                requestId, ceremony, now);
        return consumed ? challenge : null;
    }
}
