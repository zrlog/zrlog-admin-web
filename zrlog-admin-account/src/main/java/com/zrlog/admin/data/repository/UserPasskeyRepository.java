package com.zrlog.admin.data.repository;

import com.zrlog.model.UserPasskey;
import com.zrlog.data.repository.BaseRepository;

import com.zrlog.data.repository.*;


import com.hibegin.common.dao.DataSourceWrapper;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class UserPasskeyRepository extends BaseRepository<UserPasskey, Long> {

    public static final String TABLE_NAME = "user_passkey";

    @Inject
    public UserPasskeyRepository(DataSourceWrapper database) {
        super(UserPasskey.class, database);
    }

    public List<Map<String, Object>> findByUserId(int userId) throws SQLException {
        return queryListWithParams("select * from " + tableName + " where userId=? order by createdAt desc", userId);
    }

    public Map<String, Object> findByCredentialIdHash(String credentialIdHash) throws SQLException {
        return queryFirstWithParams("select * from " + tableName + " where credentialIdHash=?", credentialIdHash);
    }

    public long countByUserId(int userId) throws SQLException {
        return ((Number) queryFirstObj("select count(1) from " + tableName + " where userId=?", userId)).longValue();
    }

    public boolean hasAny() throws SQLException {
        return queryFirstObj("select 1 from " + tableName + " limit 1") != null;
    }

    public boolean save(int userId, String credentialIdHash, String credentialId, String publicKeyCose,
                        long signatureCount, String transports, String name, String aaguid,
                        boolean backupEligible, boolean backupState, String origin, String rpId,
                        long createdAt) throws SQLException {
        return execute("insert into " + tableName
                        + " (userId,credentialIdHash,credentialId,publicKeyCose,signatureCount,transports,name,aaguid,"
                        + "backupEligible,backupState,origin,rpId,createdAt,lastUsedAt,updatedAt,version)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                userId, credentialIdHash, credentialId, publicKeyCose, signatureCount, transports, name, aaguid,
                backupEligible ? 1 : 0, backupState ? 1 : 0, origin, rpId, new java.sql.Timestamp(createdAt).toString(), null, new java.sql.Timestamp(createdAt).toString());
    }

    public boolean updateAfterAuthentication(long id, long signatureCount, boolean backupState, long lastUsedAt)
            throws SQLException {
        return execute("update " + tableName
                        + " set signatureCount=?,backupState=?,lastUsedAt=?,updatedAt=?,version=version+1 where id=?"
                        + " and ((signatureCount=0 and ?=0) or signatureCount<?)",
                signatureCount, backupState ? 1 : 0, lastUsedAt, new java.sql.Timestamp(lastUsedAt).toString(), id, signatureCount, signatureCount);
    }

    public boolean deleteByIdAndUserId(long id, int userId) throws SQLException {
        return execute("delete from " + tableName + " where id=? and userId=?", id, userId);
    }
}
