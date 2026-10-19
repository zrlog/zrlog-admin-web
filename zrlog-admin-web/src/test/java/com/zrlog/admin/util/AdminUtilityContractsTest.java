package com.zrlog.admin.util;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.DatabaseConnectPoolInfo;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminUtilityContractsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldBuildServerInfoFromRequestServerConfig() throws Exception {
        String previousRoot = PathUtil.getRootPath();
        try {
            String rootPath = temporaryFolder.newFolder("zrlog-root").getAbsolutePath();
            PathUtil.setRootPath(rootPath);
            ServerConfig serverConfig = new ServerConfig()
                    .setApplicationName("custom-admin")
                    .setApplicationVersion("1.2.3");

            Map<String, String> infoByKey = new HashMap<>();
            new SystemInfoUtils(() -> false, AdminUtilityContractsTest::dataSource, () -> null, () -> "test uptime").serverInfo(request(serverConfig))
                    .forEach(info -> infoByKey.put(info.getKey(), info.getValue()));

            assertEquals(rootPath, infoByKey.get("runPath"));
            assertEquals("custom-admin/1.2.3", infoByKey.get("webServer"));
            assertTrue(infoByKey.containsKey("system"));
            assertTrue(infoByKey.containsKey("runtime"));
            assertTrue(infoByKey.containsKey("programInfo"));
        } finally {
            PathUtil.setRootPath(previousRoot);
        }
    }

    @Test
    public void shouldReturnSystemIoInfoWithDatabasePoolSummary() throws Exception {
        String previousRoot = PathUtil.getRootPath();
        try {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-root").getAbsolutePath());

            List<ServerInfo> infos = new SystemInfoUtils(() -> false, AdminUtilityContractsTest::dataSource, () -> null, () -> "test uptime").systemIOInfoVO();
            Map<String, String> infoByKey = new HashMap<>();
            infos.forEach(info -> infoByKey.put(info.getKey(), info.getValue()));

            assertFalse(infos.isEmpty());
            assertTrue(infoByKey.containsKey("usedMemorySpace"));
            assertEquals("2 / 5", infoByKey.get("dbConnectSize"));
            assertEquals("test uptime", infoByKey.get("uptime"));
        } finally {
            PathUtil.setRootPath(previousRoot);
        }
    }

    @Test
    public void shouldReturnClasspathTemplatePathWithoutDownload() throws Exception {
        BaseController controller = new BaseController(request(Map.of("shortTemplate", "default")), null);

        assertEquals(Constants.getDefaultTemplatePath(), AdminTemplateUtils.loadTemplatePathByRequestInfo(controller));
    }

    private static HttpRequest request(ServerConfig serverConfig) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminUtilityContractsTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getServerConfig".equals(method.getName())) {
                        return serverConfig;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static HttpRequest request(Map<String, String> params) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminUtilityContractsTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getParaToStr".equals(method.getName())) {
                        return params.get(args[0].toString());
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static DataSourceWrapper dataSource() {
        return (DataSourceWrapper) Proxy.newProxyInstance(
                AdminUtilityContractsTest.class.getClassLoader(),
                new Class[]{DataSourceWrapper.class},
                (proxy, method, args) -> {
                    if ("getDatabaseConnectPoolInfo".equals(method.getName())) {
                        return new DatabaseConnectPoolInfo(2, 5);
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
}
