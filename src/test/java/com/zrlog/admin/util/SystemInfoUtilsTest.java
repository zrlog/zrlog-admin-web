package com.zrlog.admin.util;

import com.hibegin.common.dao.DataSourceWrapperImpl;
import com.zrlog.util.DataSourceUtil;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SystemInfoUtilsTest {

    @Test
    public void shouldReadSqliteVersionFromJdbcMetadata() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("driverClass", "org.sqlite.JDBC");
        properties.setProperty("jdbcUrl", "jdbc:sqlite::memory:");
        properties.setProperty("user", "");
        properties.setProperty("password", "");

        try (DataSourceWrapperImpl dataSource = DataSourceUtil.buildDataSource(properties)) {
            String version = SystemInfoUtils.getDatabaseServerVersion(dataSource);

            assertNotEquals("Unknown", version);
            assertTrue(version.matches("\\d+(\\.\\d+)+.*"));
        }
    }
}
