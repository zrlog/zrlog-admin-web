package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.data.repository.UserRepository;
import com.zrlog.model.User;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.Map;

/** Account login and profile persistence owned by admin. */
public class AdminUserRepository extends UserRepository {

    @Inject
    public AdminUserRepository(DataSourceWrapper dataSource) {
        super(dataSource);
    }

    public User getUserByUserName(String userName) throws SQLException {
        return findMatching(Map.of("userName", userName), 1).stream().findFirst().orElse(null);
    }

    public String getPasswordByUserId(int userId) throws SQLException {
        return (String)
                queryFirstObj("select password from " + tableName + " where userId=?", userId);
    }

    public boolean updatePassword(int userId, String password) throws SQLException {
        User user = loadById((long) userId);
        if (user == null) return false;
        user.setPassword(password);
        return update(user);
    }

    public void updateEmailUserNameHeaderByUserId(
            String email, String userName, String header, int userId) throws SQLException {
        User user = loadById((long) userId);
        if (user == null) return;
        user.setHeader(header);
        user.setEmail(email);
        user.setUserName(userName);
        update(user);
    }
}
