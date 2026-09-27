package com.zrlog.model;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DatabaseConnectPoolInfo;
import com.hibegin.common.dao.DataSourceWrapper;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogVersionTest {

    @Test
    public void shouldBuildSqlForArticleVersionWritesAndQueries() throws Exception {
        CapturingQueryRunner queryRunner = new CapturingQueryRunner();
        DataSourceWrapper previousDataSource = setDefaultDataSource(dataSource(queryRunner));
        try {
            LogVersion logVersion = new LogVersion();
            Date createdAt = new Date(1_000);

            assertTrue(logVersion.savePatch(7, 3, "{\"diff\":true}", 9, "title", createdAt, 2));
            assertEquals("insert into log_version"
                    + " (log_id, article_version, patch_json, user_id, title, created_at, from_version)"
                    + " values (?, ?, ?, ?, ?, ?, ?)", queryRunner.sql);
            assertArrayEquals(new Object[]{7, 3, "{\"diff\":true}", 9, "title", createdAt, 2},
                    queryRunner.params);

            assertTrue(logVersion.deleteByLogIdAndArticleVersion(7, 3));
            assertEquals("delete from log_version where log_id=? and article_version=?", queryRunner.sql);
            assertArrayEquals(new Object[]{7, 3}, queryRunner.params);

            List<Map<String, Object>> rows = List.of(Map.of("article_version", 3));
            queryRunner.rows = rows;
            assertEquals(rows, logVersion.findVersionList(7));
            assertEquals("select article_version, from_version, created_at, user_id, title from log_version"
                    + " where log_id=? order by article_version desc", queryRunner.sql);
            assertArrayEquals(new Object[]{7}, queryRunner.params);

            assertEquals(rows, logVersion.findReversePatchesGreaterThanVersion(7, 2));
            assertEquals("select article_version, patch_json from log_version"
                    + " where log_id=? and article_version>? order by article_version desc", queryRunner.sql);
            assertArrayEquals(new Object[]{7, 2}, queryRunner.params);
        } finally {
            restoreDefaultDataSource(previousDataSource);
        }
    }

    private static DataSourceWrapper setDefaultDataSource(DataSourceWrapper dataSource) {
        DataSourceWrapper previous = DAO.getDefaultDataSource();
        DAO.setDs(dataSource);
        return previous;
    }

    private static void restoreDefaultDataSource(DataSourceWrapper previousDataSource) {
        DAO.setDs(previousDataSource);
    }

    private static DataSourceWrapper dataSource(CapturingQueryRunner queryRunner) {
        return (DataSourceWrapper) Proxy.newProxyInstance(
                LogVersionTest.class.getClassLoader(),
                new Class[]{DataSourceWrapper.class},
                (proxy, method, args) -> {
                    if ("getQueryRunner".equals(method.getName())) {
                        return queryRunner;
                    }
                    if ("getDataSourceProperties".equals(method.getName())) {
                        return new Properties();
                    }
                    if ("getDatabaseConnectPoolInfo".equals(method.getName())) {
                        return new DatabaseConnectPoolInfo(0, 0);
                    }
                    if ("getDbInfo".equals(method.getName())) {
                        return "test-db";
                    }
                    if ("isWebApi".equals(method.getName()) || "isDev".equals(method.getName())) {
                        return false;
                    }
                    if ("toString".equals(method.getName())) {
                        return "DataSourceWrapperProxy";
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }

    private static class CapturingQueryRunner extends QueryRunner {

        private String sql;
        private Object[] params = new Object[0];
        private List<Map<String, Object>> rows = List.of();

        @Override
        public int update(String sql, Object... params) {
            this.sql = sql;
            this.params = Arrays.copyOf(params, params.length);
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T query(String sql, ResultSetHandler<T> rsh, Object... params) throws SQLException {
            this.sql = sql;
            this.params = Arrays.copyOf(params, params.length);
            return (T) rows;
        }
    }
}
