package com.zrlog.admin.business.security;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import java.sql.*;
import java.util.*;

/** Parameterized persistence. Web API sessions require atomic conditional writes, not JDBC locks. */
public final class SecurityStore {
    public static final class Session {
        private final QueryRunner runner;
        private final Connection connection;
        private Session(QueryRunner runner, Connection connection) {
            this.runner = runner;
            this.connection = connection;
        }
        public boolean isWebApi() { return connection == null; }
    }

    public interface Work<T> { T run(Session session) throws SQLException; }

    /** JDBC rolls back on failure. Web API commits each statement; callers must fail closed. */
    public <T> T withSession(Work<T> work) throws SQLException {
        DataSourceWrapper source = DAO.getDefaultDataSource();
        if (source.isWebApi()) return work.run(new Session(source.getQueryRunner(), null));
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(new Session(new QueryRunner(), c));
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    public Map<String,Object> one(Session c, String sql, Object... args) throws SQLException {
        return c.isWebApi() ? c.runner.query(sql, new MapHandler(), webArgs(args)) : c.runner.query(c.connection, sql, new MapHandler(), args);
    }
    public List<Map<String,Object>> list(Session c, String sql, Object... args) throws SQLException {
        return c.isWebApi() ? c.runner.query(sql, new MapListHandler(), webArgs(args)) : c.runner.query(c.connection, sql, new MapListHandler(), args);
    }
    public int update(Session c, String sql, Object... args) throws SQLException {
        return c.isWebApi() ? c.runner.update(sql, webArgs(args)) : c.runner.update(c.connection, sql, args);
    }
    public void lock(Session c, String sql, Object... args) throws SQLException {
        if (!c.isWebApi()) update(c, sql, args);
    }
    private static Object[] webArgs(Object[] args) {
        // Use SQLite 0/1 values consistently across Web API gateway versions.
        return Arrays.stream(args).map(value -> value instanceof Boolean ? ((Boolean) value ? 1 : 0) : value).toArray();
    }
}
