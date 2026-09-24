package com.zrlog.admin.business.security;

import com.hibegin.common.dao.DAO;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import java.sql.*;
import java.util.*;

/** Parameterized persistence. Transactions make authorization-code and refresh-token consumption atomic. */
public final class SecurityStore {
    private final QueryRunner runner = new QueryRunner();
    public interface Work<T> { T run(Connection connection) throws SQLException; }
    public <T> T transaction(Work<T> work) throws SQLException {
        if (DAO.getDefaultDataSource().isWebApi()) {
            throw new OAuthException("temporarily_unavailable", 503, com.zrlog.admin.business.exception.AdminErrorCode.ACCOUNT_STORAGE_UNSUPPORTED);
        }
        try (Connection c = DAO.getDefaultDataSource().getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    public Map<String,Object> one(Connection c, String sql, Object... args) throws SQLException { return runner.query(c, sql, new MapHandler(), args); }
    public List<Map<String,Object>> list(Connection c, String sql, Object... args) throws SQLException { return runner.query(c, sql, new MapListHandler(), args); }
    public int update(Connection c, String sql, Object... args) throws SQLException { return runner.update(c, sql, args); }
}
