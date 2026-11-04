package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.data.repository.BaseRepository;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import jakarta.inject.Inject;
import java.sql.SQLException;

/** Admin identity projections exclude credentials and profile/preferences payloads. */
public final class AccountIdentityRepository extends BaseRepository<User, Long> {
    @Inject
    public AccountIdentityRepository(DataSourceWrapper source) {
        super(User.class, source);
    }

    public AccountAccess accessById(int userId) throws SQLException {
        return AccountAccess.fromSnapshot(
                queryOne(
                        "SELECT userId,role,enabled,authVersion FROM user WHERE userId=?",
                        AccountAccess.Snapshot.class,
                        userId));
    }

    public AccountAccess ownerAccess() throws SQLException {
        return AccountAccess.fromSnapshot(
                queryOne(
                        "SELECT userId,role,enabled,authVersion FROM user WHERE role=? AND enabled=? ORDER BY userId LIMIT 1",
                        AccountAccess.Snapshot.class,
                        "owner",
                        true));
    }
}
