package com.zrlog.admin;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;

public class MemoryApplicationTest {


    @Test
    public void shouldResolveDefaultAndConfiguredPort() {
        assertEquals(17080, MemoryApplication.resolvePort(null));
        assertEquals(17080, MemoryApplication.resolvePort(new String[]{"--debug"}));
        assertEquals(18080, MemoryApplication.resolvePort(new String[]{"--port=18080"}));
    }


    private static Object scalar(DevZrLogConfig config, String sql, String value) throws Exception {
        try (Connection connection = config.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getObject(1) : null;
            }
        }
    }

    private static void deleteTree(Path rootPath) throws Exception {
        try (var stream = Files.walk(rootPath)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }
}
