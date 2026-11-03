package com.zrlog.admin.business.security;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.EntityRepository;
import com.hibegin.common.dao.entity.EntitySession;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Parameterized persistence. Web API sessions require atomic conditional writes, not JDBC locks.
 */
@Singleton
public final class SecurityStore {
    private final jakarta.inject.Provider<DataSourceWrapper> database;

    @Inject
    public SecurityStore(jakarta.inject.Provider<DataSourceWrapper> database) {
        this.database = database;
    }

    private static Object[] webArgs(Object[] args) {
        // Use SQLite 0/1 values consistently across Web API gateway versions.
        return Arrays.stream(args).map(value -> value instanceof Boolean ? ((Boolean) value ? 1 : 0) : value).toArray();
    }

    /**
     * JDBC rolls back on failure. Web API commits each statement; callers must fail closed.
     */
    public <T> T withSession(Work<T> work) throws SQLException {
        DataSourceWrapper source = database.get();
        if (source.isWebApi()) return work.run(new Session(source, source.getQueryRunner(), null, null));
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try (EntitySession entities = new EntitySession(source, c)) {
                T result = work.run(new Session(source, new QueryRunner(), c, entities));
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public <E extends BaseEntity<ID>, ID> EntityRepository<E, ID> repository(Session session, Class<E> type) {
        return session.entities == null ? new EntityRepository<>(type, session.source) : session.entities.repository(type);
    }

    public <E extends BaseEntity<ID>, ID> E entity(Session session, Class<E> type, ID id) throws SQLException {
        return this.<E, ID>repository(session, type).findById(id).orElse(null);
    }

    public <E extends BaseEntity<ID>, ID> List<E> entities(Session session, Class<E> type, String clauses, Object... args) throws SQLException {
        return this.<E, ID>repository(session, type).select(clauses, args);
    }

    public <E extends BaseEntity<ID>, ID> E firstEntity(Session session, Class<E> type, String clauses, Object... args) throws SQLException {
        return entities(session, type, clauses, args).stream().findFirst().orElse(null);
    }

    public Object scalar(Session session, String sql, Object... args) throws SQLException {
        var handler = new org.apache.commons.dbutils.handlers.ScalarHandler<>();
        return session.isWebApi() ? session.runner.query(sql, handler, webArgs(args))
                : session.runner.query(session.connection, sql, handler, args);
    }

    public <T> List<T> list(Session session, String sql, Class<T> type, Object... args) throws SQLException {
        if (BaseEntity.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Use tracked entity reads or a concrete projection type");
        }
        List<T> values = new java.util.ArrayList<>();
        for (Map<String, Object> row : list(session, sql, args)) values.add(com.hibegin.common.dao.entity.RowProjection.map(row, type));
        return values;
    }

    public Map<String, Object> one(Session c, String sql, Object... args) throws SQLException {
        return c.isWebApi() ? c.runner.query(sql, new MapHandler(), webArgs(args)) : c.runner.query(c.connection, sql, new MapHandler(), args);
    }

    public List<Map<String, Object>> list(Session c, String sql, Object... args) throws SQLException {
        return c.isWebApi() ? c.runner.query(sql, new MapListHandler(), webArgs(args)) : c.runner.query(c.connection, sql, new MapListHandler(), args);
    }

    public int update(Session c, String sql, Object... args) throws SQLException {
        return c.isWebApi() ? c.runner.update(sql, webArgs(args)) : c.runner.update(c.connection, sql, args);
    }

    public void lock(Session c, String sql, Object... args) throws SQLException {
        if (!c.isWebApi()) update(c, sql, args);
    }

    public interface Work<T> {
        T run(Session session) throws SQLException;
    }

    public static final class Session {
        private final DataSourceWrapper source;
        private final QueryRunner runner;
        private final Connection connection;
        private final EntitySession entities;

        private Session(DataSourceWrapper source, QueryRunner runner, Connection connection, EntitySession entities) {
            this.source = source;
            this.runner = runner;
            this.connection = connection;
            this.entities = entities;
        }

        public boolean isWebApi() {
            return connection == null;
        }
    }
}
