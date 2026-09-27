package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PwaInterceptorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final String previousStaticPath = System.getProperty("sws.static.path");

    @After
    public void tearDown() {
        if (previousStaticPath == null) {
            System.clearProperty("sws.static.path");
        } else {
            System.setProperty("sws.static.path", previousStaticPath);
        }
    }

    @Test
    public void shouldPreferConfiguredStaticFaviconFile() throws Exception {
        File staticDir = temporaryFolder.newFolder("static");
        System.setProperty("sws.static.path", staticDir.getAbsolutePath());
        File favicon = new File(staticDir, "favicon.ico");
        Files.write(favicon.toPath(), new byte[]{1, 2, 3});
        ResponseRecorder response = new ResponseRecorder();

        new PwaInterceptor().doInterceptor(request(AdminConstants.FAVICON_ICO_URI_PATH), response.response());

        assertArrayEquals(new byte[]{1, 2, 3}, response.written);
        assertEquals(Integer.valueOf(200), response.status);
    }

    @Test
    public void shouldRenderPackagedPwaIconResource() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        new PwaInterceptor().doInterceptor(request(AdminConstants.FAVICON_PNG_PWA_192_URI_PATH), response.response());

        assertTrue(response.headers.get("Content-Type").contains("png"));
        assertTrue(response.written.length > 0);
        assertEquals(Integer.valueOf(200), response.status);
    }

    @Test
    public void shouldFallbackToDefaultPwaIconWhenRequestedResourceIsMissing() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        new PwaInterceptor().doInterceptor(request("/missing-pwa-icon.png"), response.response());

        assertTrue(response.headers.get("Content-Type").contains("png"));
        assertTrue(response.written.length > 0);
        assertEquals(Integer.valueOf(200), response.status);
    }

    private static HttpRequest request(String uri) {
        return (HttpRequest) Proxy.newProxyInstance(
                PwaInterceptorTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static class ResponseRecorder {
        private final Map<String, String> headers = new HashMap<>();
        private byte[] written;
        private Integer status;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    PwaInterceptorTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "addHeader":
                                headers.put(args[0].toString(), args[1].toString());
                                return null;
                            case "write":
                                if (args[0] instanceof InputStream) {
                                    written = ((InputStream) args[0]).readAllBytes();
                                }
                                if (args.length > 1) {
                                    status = (Integer) args[1];
                                }
                                return null;
                            default:
                                return null;
                        }
                    });
        }
    }
}
