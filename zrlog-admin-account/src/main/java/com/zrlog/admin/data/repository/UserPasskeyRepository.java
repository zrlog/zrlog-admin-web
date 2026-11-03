package com.zrlog.admin.data.repository;

import com.zrlog.model.UserPasskey;
import com.zrlog.data.repository.BaseRepository;

import com.zrlog.data.repository.*;


import com.hibegin.common.dao.DataSourceWrapper;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import com.hibegin.common.dao.entity.OptimisticLockException;

public class UserPasskeyRepository extends BaseRepository<UserPasskey, Long> {

    private final DataSourceWrapper database;

    public static final String TABLE_NAME = "user_passkey";

    @Inject
    public UserPasskeyRepository(DataSourceWrapper database) {
        super(UserPasskey.class, database);
        this.database = database;
    }

    public List<UserPasskey> findByUserId(int userId) throws SQLException {
        return select("WHERE userId=? ORDER BY createdAt DESC", userId);
    }

    public UserPasskey findByCredentialIdHash(String credentialIdHash) throws SQLException {
        return findMatching(Map.of("credentialIdHash", credentialIdHash), 1).stream().findFirst().orElse(null);
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
        UserPasskey passkey = new UserPasskey();
        passkey.setUserId(userId);
        passkey.setCredentialIdHash(credentialIdHash);
        passkey.setCredentialId(credentialId);
        passkey.setPublicKeyCose(publicKeyCose);
        passkey.setSignatureCount(signatureCount);
        passkey.setTransports(transports);
        passkey.setName(name);
        passkey.setAaguid(aaguid);
        passkey.setBackupEligible(backupEligible);
        passkey.setBackupState(backupState);
        passkey.setOrigin(origin);
        passkey.setRpId(rpId);
        new com.hibegin.common.dao.entity.EntityRepository<>(UserPasskey.class, database,
                java.time.Clock.fixed(java.time.Instant.ofEpochMilli(createdAt), java.time.ZoneOffset.UTC)).insert(passkey);
        return true;
    }

    public boolean updateAfterAuthentication(UserPasskey passkey, long signatureCount, boolean backupState, long lastUsedAt)
            throws SQLException {
        long previous = passkey.getSignatureCount();
        if (!(previous == 0 && signatureCount == 0) && signatureCount <= previous) return false;
        passkey.setSignatureCount(signatureCount);
        passkey.setBackupState(backupState);
        passkey.setLastUsedAt(lastUsedAt);
        try {
            update(passkey);
            return true;
        } catch (OptimisticLockException conflict) {
            return false;
        }
    }

    public boolean deleteByIdAndUserId(long id, int userId) throws SQLException {
        UserPasskey passkey = loadById(id);
        if (passkey == null || !java.util.Objects.equals(passkey.getUserId(), userId)) return false;
        try {
            delete(passkey, Map.of("userId", userId));
            return true;
        } catch (OptimisticLockException conflict) {
            return false;
        }
    }
}
