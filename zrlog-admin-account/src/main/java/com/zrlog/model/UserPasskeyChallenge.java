package com.zrlog.model;

import com.hibegin.common.dao.BasePageableDAO;

import java.sql.SQLException;
import java.util.Map;

public class UserPasskeyChallenge extends BasePageableDAO {

    public static final String TABLE_NAME = "user_passkey_challenge";

    public UserPasskeyChallenge() {
        this.tableName = TABLE_NAME;
        this.pk = "id";
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
                        + " (requestId,ceremony,userId,requestJson,expiresAt,createdAt) values (?,?,?,?,?,?)",
                requestId, ceremony, userId, requestJson, expiresAt, createdAt);
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
