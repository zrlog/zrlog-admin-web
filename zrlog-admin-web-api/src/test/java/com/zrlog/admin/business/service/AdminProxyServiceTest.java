package com.zrlog.admin.business.service;

import com.hibegin.http.server.util.PathUtil;
import com.zrlog.util.NetworkProxy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class AdminProxyServiceTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void connectionTestUsesSuppliedProxyAndKeepsDiskAndGlobalPolicyUnchanged() throws Exception {
        String previousRoot = System.getProperty("sws.root.path");
        ProxySelector previousSelector = ProxySelector.getDefault();
        String previousHost = System.getProperty("https.proxyHost");
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            PathUtil.setRootPath(temporary.newFolder().toString());
            CompletableFuture<String> received = new CompletableFuture<>();
            Thread worker = new Thread(() -> {
                try (var connection = socket.accept()) {
                    connection.setSoTimeout(3000);
                    var reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                    String first = reader.readLine();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    }
                    received.complete(first);
                    connection.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                } catch (Exception error) {
                    received.completeExceptionally(error);
                }
            });
            worker.setDaemon(true);
            worker.start();
            // Resolve a local fixture independently of the CI process's HTTP(S)_PROXY and NO_PROXY.
            NetworkProxy.save("http://127.0.0.1:" + socket.getLocalPort(), "");
            NetworkProxy.Settings pending = NetworkProxy.fileSettings();
            byte[] previousFile = Files.readAllBytes(NetworkProxy.configFile());
            assertFalse(new AdminProxyService().testConnection(pending));
            assertEquals("CONNECT www.zrlog.com:443 HTTP/1.1", received.get(3, TimeUnit.SECONDS));
            assertArrayEquals(previousFile, Files.readAllBytes(NetworkProxy.configFile()));
            assertSame(previousSelector, ProxySelector.getDefault());
            assertEquals(previousHost, System.getProperty("https.proxyHost"));
            worker.join(3000);
        } finally {
            if (previousRoot == null) System.clearProperty("sws.root.path");
            else System.setProperty("sws.root.path", previousRoot);
        }
    }
}
