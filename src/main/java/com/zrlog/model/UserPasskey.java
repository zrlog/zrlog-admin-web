package com.zrlog.model;

import com.hibegin.common.dao.BasePageableDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class UserPasskey extends BasePageableDAO {

    public static final String TABLE_NAME = "user_passkey";

    public UserPasskey() {
        this.tableName = TABLE_NAME;
        this.pk = "id";
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

    public boolean hasAny(String origin, String rpId) throws SQLException {
        return queryFirstObj("select 1 from " + tableName + " where origin=? and rpId=? limit 1", origin, rpId)
                != null;
    }

    public boolean save(int userId, String credentialIdHash, String credentialId, String publicKeyCose,
                        long signatureCount, String transports, String name, String aaguid,
                        boolean backupEligible, boolean backupState, String origin, String rpId,
                        long createdAt) throws SQLException {
        return execute("insert into " + tableName
                        + " (userId,credentialIdHash,credentialId,publicKeyCose,signatureCount,transports,name,aaguid,"
                        + "backupEligible,backupState,origin,rpId,createdAt,lastUsedAt)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                userId, credentialIdHash, credentialId, publicKeyCose, signatureCount, transports, name, aaguid,
                backupEligible ? 1 : 0, backupState ? 1 : 0, origin, rpId, createdAt, null);
    }

    public boolean updateAfterAuthentication(long id, long signatureCount, boolean backupState, long lastUsedAt)
            throws SQLException {
        return execute("update " + tableName
                        + " set signatureCount=?,backupState=?,lastUsedAt=? where id=?"
                        + " and ((signatureCount=0 and ?=0) or signatureCount<?)",
                signatureCount, backupState ? 1 : 0, lastUsedAt, id, signatureCount, signatureCount);
    }

    public boolean deleteByIdAndUserId(long id, int userId) throws SQLException {
        return execute("delete from " + tableName + " where id=? and userId=?", id, userId);
    }
}
