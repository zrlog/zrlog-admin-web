package com.zrlog.admin.support;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.util.DataSourceUtil;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class InMemoryZrLogDatabase implements AutoCloseable {

    private final DataSourceWrapper dataSource;
    private final DataSourceWrapper previousDataSource;
    private final ZrLogConfig previousConfig;
    private final AdminResource previousAdminResource;
    private final TestCacheService cacheService = new TestCacheService();

    private InMemoryZrLogDatabase() throws Exception {
        this.previousDataSource = currentDefaultDataSource();
        this.previousConfig = Constants.zrLogConfig;
        this.previousAdminResource = AdminConstants.adminResource;
        this.dataSource = newDataSource();
        DAO.setDs(dataSource);
        Constants.zrLogConfig = new TestZrLogConfig(dataSource, cacheService);
        AdminConstants.adminResource = new TestAdminResource();
        loadSchema();
        seedBaseData();
    }

    public static InMemoryZrLogDatabase open() throws Exception {
        return new InMemoryZrLogDatabase();
    }

    public DataSourceWrapper dataSource() {
        return dataSource;
    }

    public TestCacheService cacheService() {
        return cacheService;
    }

    public boolean execute(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().update(sql, params) > 0;
    }

    public Object scalar(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().query(sql, new ScalarHandler<>(1), params);
    }

    public Map<String, Object> queryOne(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().query(sql, new MapHandler(), params);
    }

    public List<Map<String, Object>> queryList(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().query(sql, new MapListHandler(), params);
    }

    public void putWebsite(String name, Object value) throws SQLException {
        execute("merge into website(name, value) key(name) values(?, ?)", name, value == null ? null : value.toString());
    }

    private static DataSourceWrapper newDataSource() {
        Properties properties = new Properties();
        properties.setProperty("driverClass", "org.h2.Driver");
        properties.setProperty("jdbcUrl", "jdbc:h2:mem:zrlog_admin_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
                + ";NON_KEYWORDS=USER,VALUE,COMMENT,TYPE;DB_CLOSE_DELAY=-1");
        properties.setProperty("user", "sa");
        properties.setProperty("password", "");
        return DataSourceUtil.buildDataSource(properties);
    }

    private void loadSchema() throws Exception {
        try (InputStream input = InMemoryZrLogDatabase.class.getResourceAsStream("/init-table-structure.sql")) {
            if (input == null) {
                throw new IllegalStateException("Missing init-table-structure.sql from zrlog-install-web test dependency");
            }
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : normalizeInstallSqlForH2(sql).split(";")) {
                String trimmed = normalizeStatement(statement);
                if (!trimmed.isEmpty()) {
                    dataSource.getQueryRunner().update(trimmed);
                }
            }
        }
    }

    private static String normalizeInstallSqlForH2(String sql) {
        StringBuilder builder = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("/*!")) {
                continue;
            }
            String normalizedLine = line
                    .replaceAll("(?i)UNIQUE\\s+KEY\\s+`[^`]+`\\s*\\(", "UNIQUE (")
                    .replaceAll("(?i)KEY\\s+`[^`]+`\\s*\\(", "INDEX (")
                    .replaceAll("(?i)\\s+COMMENT\\s+'[^']*'", "");
            builder.append(normalizedLine)
                    .append('\n');
        }
        return builder.toString()
                .replace("bit(1)", "boolean")
                .replace("DEFAULT b'0'", "DEFAULT false")
                .replace("DEFAULT b'1'", "DEFAULT true")
                .replaceAll("(?i)\\)\\s*ENGINE\\s*=\\s*InnoDB\\s+DEFAULT\\s+CHARSET\\s*=\\s*[^\\s;]+(?:\\s+COLLATE\\s+[^\\s;]+)?", ")");
    }

    private static String normalizeStatement(String statement) {
        String trimmed = statement.trim();
        if (trimmed.toLowerCase().startsWith("drop table if exists") && trimmed.contains(",")) {
            return "";
        }
        return trimmed;
    }

    private void seedBaseData() throws SQLException {
        execute("insert into user(userId, email, password, userName, header) values(?, ?, ?, ?, ?)",
                1, "admin@example.com", "password", "admin", "/avatar.png");
        execute("insert into type(typeId, alias, typeName, remark) values(?, ?, ?, ?)",
                1, "default", "Default", "Default type");
        putWebsite("title", "ZrLog Test");
        putWebsite("host", "localhost:18080");
        putWebsite("language", Constants.DEFAULT_LANGUAGE);
        putWebsite("article_auto_digest_length", 80);

        TypeDTO type = new TypeDTO();
        type.setId(1L);
        type.setAlias("default");
        type.setTypeName("Default");
        type.setRemark("Default type");
        cacheService.articleTypes.add(type);
    }

    private static DataSourceWrapper currentDefaultDataSource() throws Exception {
        Field field = DAO.class.getDeclaredField("defaultDataSource");
        field.setAccessible(true);
        return (DataSourceWrapper) field.get(null);
    }

    @Override
    public void close() throws Exception {
        try {
            dataSource.close();
        } finally {
            DAO.setDs(previousDataSource);
            Constants.zrLogConfig = previousConfig;
            AdminConstants.adminResource = previousAdminResource;
        }
    }

    public static class TestCacheService implements CacheService {

        private final PublicWebSiteInfo publicInfo = new PublicWebSiteInfo();
        private final List<TypeDTO> articleTypes = new ArrayList<>();
        private final List<TagDTO> tags = new ArrayList<>();

        private TestCacheService() {
            publicInfo.setTitle("ZrLog Test");
            publicInfo.setHost("localhost:18080");
            publicInfo.setLanguage(Constants.DEFAULT_LANGUAGE);
            publicInfo.setGenerator_html_status(false);
            publicInfo.setDisable_comment_status(false);
            publicInfo.setArticle_thumbnail_status(true);
            publicInfo.setArticle_auto_digest_length(80L);
        }

        @Override
        public long getCurrentSqlVersion() {
            return 0;
        }

        @Override
        public long getWebSiteVersion() {
            return 0;
        }

        @Override
        public BaseDataInitVO getInitData() {
            return new BaseDataInitVO();
        }

        @Override
        public BaseDataInitVO refreshInitData() {
            return getInitData();
        }

        @Override
        public PublicWebSiteInfo getPublicWebSiteInfo() {
            return publicInfo;
        }

        @Override
        public List<TypeDTO> getArticleTypes() {
            return articleTypes;
        }

        @Override
        public List<TagDTO> getTags() {
            return tags;
        }

        @Override
        public UserBasicDTO getUserInfoById(Long userId) {
            UserBasicDTO user = new UserBasicDTO();
            user.setUserId(userId);
            user.setUserName("admin");
            user.setHeader("/avatar.png");
            return user;
        }

        @Override
        public Map<String, Object> getTemplateConfigMapWithCache(String template) {
            return Map.of();
        }
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private final DataSourceWrapper testDataSource;

        private TestZrLogConfig(DataSourceWrapper dataSource, CacheService cacheService) {
            super(18080, null, "");
            this.testDataSource = dataSource;
            this.dataSource = dataSource;
            this.cacheService = cacheService;
        }

        @Override
        public boolean isInstalled() {
            return false;
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return testDataSource;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public ServerConfig getServerConfig() {
            return serverConfig;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }

    private static class TestAdminResource implements AdminResource {

        @Override
        public java.util.Set<String> getAdminStaticResourceUris() {
            return java.util.Set.of();
        }

        @Override
        public java.util.Set<String> getAdminPageUris() {
            return java.util.Set.of();
        }

        @Override
        public java.util.Set<String> getAdminStaticCacheUris() {
            return java.util.Set.of();
        }

        @Override
        public java.util.Set<String> getAdminCacheableApiUris() {
            return java.util.Set.of("/api/admin/website", "/api/admin/user");
        }

        @Override
        public InputStream renderServiceWorker(HttpRequest request) {
            return InputStream.nullInputStream();
        }

        @Override
        public String getStaticResourceBuildId() {
            return "test";
        }

        @Override
        public AdminResourceInfoResponse adminResourceInfo(HttpRequest request) {
            return new AdminResourceInfoResponse();
        }
    }
}
