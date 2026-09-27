package com.zrlog.admin.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BackendServerUrlTest {
    @Test
    public void configuredBackendIncludesContextExactlyOnce() {
        assertEquals("https://api.example/sub", BackendServerUrl.resolve("https://api.example/", "blog.example", "/sub"));
        assertEquals("https://api.example/proxy/sub", BackendServerUrl.resolve("https://api.example/proxy/sub/", "blog.example", "/sub"));
        assertEquals("https://blog.example/sub", BackendServerUrl.resolve(null, "blog.example", "/sub"));
        assertEquals("http://localhost:18080/sub", BackendServerUrl.resolve("", "localhost:18080", "/sub"));
    }

    @Test
    public void malformedBackendAndContextCannotProduceUnsafeSourceLinks() {
        for (String address : List.of("/sub", "http://api.example", "https://user@api.example", "https://api.example?x=1",
                "https://api.example#fragment", "https://api.example/../sub", "https://api.example/%2e%2e/sub",
                "https://api.example/sub%2fpath", "https://api.example:65536")) {
            assertThrows(address, IllegalArgumentException.class, () -> BackendServerUrl.resolve(address, "blog.example", "/sub"));
        }
        for (String context : List.of("/../sub", "/sub?x=1", "/sub#fragment", "//sub")) {
            assertThrows(context, IllegalArgumentException.class, () -> BackendServerUrl.resolve("https://api.example", "blog.example", context));
        }
    }
}
