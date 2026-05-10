package com.zrlog.admin.business.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.HealthCheckIssueResponse;
import com.zrlog.admin.business.rest.response.HealthCheckResponse;
import com.zrlog.admin.business.rest.response.HealthCheckSuggestionResponse;
import com.zrlog.common.Constants;
import com.zrlog.model.Log;
import com.zrlog.model.WebSite;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.*;

public class SystemHealthCheckService {

    private static final int SAMPLE_LIMIT = 5;
    private static final String WEBSITE_SEO_ROUTE = "/website";
    private static final String ARTICLE_ROUTE = "/article";

    public HealthCheckResponse check() throws SQLException {
        BrokenLinkResult brokenLinkResult = scanBrokenLinks();
        SeoResult seoResult = scanSeo();
        DatabaseFragmentResult databaseFragmentResult = inspectDatabaseFragment();
        DirectoryWritableResult directoryWritableResult = inspectDirectoryWritable();

        List<HealthCheckIssueResponse> issues = new ArrayList<>();
        List<HealthCheckSuggestionResponse> suggestions = new ArrayList<>();

        if (brokenLinkResult.count > 0) {
            issues.add(new HealthCheckIssueResponse(
                    "brokenLinks",
                    "warning",
                    brokenLinkResult.count,
                    brokenLinkResult.samples,
                    ARTICLE_ROUTE
            ));
            suggestions.add(new HealthCheckSuggestionResponse(
                    "repairBrokenLinks",
                    ARTICLE_ROUTE
            ));
        }

        if (seoResult.siteMissingCount > 0 || seoResult.articleMissingCount > 0) {
            issues.add(new HealthCheckIssueResponse(
                    "seoMissing",
                    "warning",
                    seoResult.siteMissingCount + seoResult.articleMissingCount,
                    seoResult.samples,
                    seoResult.siteMissingCount > 0 ? WEBSITE_SEO_ROUTE : ARTICLE_ROUTE
            ));
            if (seoResult.siteMissingCount > 0) {
                suggestions.add(new HealthCheckSuggestionResponse(
                        "completeWebsiteSeo",
                        WEBSITE_SEO_ROUTE
                ));
            }
            if (seoResult.articleMissingCount > 0) {
                suggestions.add(new HealthCheckSuggestionResponse(
                        "completeArticleSeo",
                        ARTICLE_ROUTE
                ));
            }
        }

        if (databaseFragmentResult.fragmentValue > 0) {
            issues.add(new HealthCheckIssueResponse(
                    "databaseFragment",
                    databaseFragmentResult.canOptimize ? "warning" : "info",
                    databaseFragmentResult.fragmentValue,
                    databaseFragmentResult.samples,
                    null
            ));
            suggestions.add(new HealthCheckSuggestionResponse(
                    "databaseOptimize",
                    null
            ));
        }

        if (directoryWritableResult.count > 0) {
            issues.add(new HealthCheckIssueResponse(
                    "directoryWritable",
                    "error",
                    directoryWritableResult.count,
                    directoryWritableResult.samples,
                    null
            ));
            suggestions.add(new HealthCheckSuggestionResponse(
                    "repairDirectoryWritable",
                    null
            ));
        }

        if (suggestions.isEmpty()) {
            suggestions.add(new HealthCheckSuggestionResponse(
                    "healthy",
                    null
            ));
        }

        int score = buildScore(
                brokenLinkResult.count,
                seoResult.siteMissingCount + seoResult.articleMissingCount,
                databaseFragmentResult.fragmentValue > 0,
                directoryWritableResult.count > 0
        );
        return new HealthCheckResponse(
                System.currentTimeMillis(),
                score,
                brokenLinkResult.count,
                seoResult.siteMissingCount + seoResult.articleMissingCount,
                databaseFragmentResult.fragmentValue,
                databaseFragmentResult.fragmentLabel,
                databaseFragmentResult.engineLabel,
                databaseFragmentResult.canOptimize,
                issues,
                suggestions
        );
    }

    public HealthCheckResponse optimize() throws SQLException {
        DatabaseFragmentResult databaseFragmentResult = inspectDatabaseFragment();
        if (databaseFragmentResult.canOptimize) {
            runDatabaseOptimize(databaseFragmentResult.engine);
        }
        return check();
    }

    private DirectoryWritableResult inspectDirectoryWritable() {
        List<String> samples = new ArrayList<>();
        checkDirectoryWritable("cache", PathUtil.getCachePath(), samples);
        checkDirectoryWritable("static", PathUtil.getStaticPath(), samples);
        return new DirectoryWritableResult(samples.size(), samples);
    }

    private void checkDirectoryWritable(String key, String path, List<String> samples) {
        File directory = new File(path);
        if (!directory.exists()) {
            if (!directory.mkdirs() && !directory.isDirectory()) {
                samples.add(key + ": " + path + " (cannot create directory)");
                return;
            }
        }
        if (!directory.isDirectory()) {
            samples.add(key + ": " + path + " (not a directory)");
            return;
        }
        String failureReason = tryWriteProbe(directory);
        if (StringUtils.isNotEmpty(failureReason)) {
            samples.add(key + ": " + path + " (" + failureReason + ")");
        }
    }

    private String tryWriteProbe(File directory) {
        File probeFile = new File(directory, ".zrlog-health-check-" + UUID.randomUUID() + ".tmp");
        try {
            if (!probeFile.createNewFile()) {
                return "cannot create probe file";
            }
            java.nio.file.Files.write(probeFile.toPath(), Collections.singletonList("health-check"));
            if (!probeFile.delete()) {
                return "probe file cannot be deleted";
            }
            return "";
        } catch (IOException e) {
            if (probeFile.exists() && !probeFile.delete()) {
                probeFile.deleteOnExit();
            }
            return e.getClass().getSimpleName() + ": " + Objects.toString(e.getMessage(), "");
        }
    }

    private BrokenLinkResult scanBrokenLinks() throws SQLException {
        List<Map<String, Object>> rows = new Log().queryListWithParams(
                "select logId, title, content from " + Log.TABLE_NAME + " where rubbish = ?",
                false
        );
        long count = 0L;
        LinkedHashSet<String> samples = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String content = Objects.toString(row.get("content"), "");
            if (StringUtils.isEmpty(content)) {
                continue;
            }
            Document document = Jsoup.parseBodyFragment(content);
            for (Element element : document.select("[src], a[href]")) {
                String attr = element.hasAttr("src") ? "src" : "href";
                String url = normalizeUrl(element.attr(attr));
                if (!shouldCheckAsLocalAsset(url)) {
                    continue;
                }
                File file = PathUtil.getStaticFile(url);
                if (!file.exists()) {
                    count++;
                    if (samples.size() < SAMPLE_LIMIT) {
                        samples.add(url);
                    }
                }
            }
        }
        return new BrokenLinkResult(count, new ArrayList<>(samples));
    }

    private SeoResult scanSeo() throws SQLException {
        WebSite webSite = new WebSite();
        Map<String, Object> websiteInfo = webSite.getWebSiteByNameIn(Arrays.asList(WebSite.title, WebSite.description, WebSite.keywords));
        long siteMissingCount = 0L;
        List<String> samples = new ArrayList<>();
        if (StringUtils.isEmpty(Objects.toString(websiteInfo.get(WebSite.title), ""))) {
            siteMissingCount++;
            addSample(samples, "网站 SEO: 缺少站点标题");
        }
        if (StringUtils.isEmpty(Objects.toString(websiteInfo.get(WebSite.description), ""))) {
            siteMissingCount++;
            addSample(samples, "网站 SEO: 缺少站点描述");
        }
        if (StringUtils.isEmpty(Objects.toString(websiteInfo.get(WebSite.keywords), ""))) {
            siteMissingCount++;
            addSample(samples, "网站 SEO: 缺少站点关键词");
        }

        List<Map<String, Object>> rows = new Log().queryListWithParams(
                "select logId, title, digest, keywords from " + Log.TABLE_NAME + " where rubbish = ? and privacy = ?",
                false, false
        );
        long articleMissingCount = 0L;
        for (Map<String, Object> row : rows) {
            String articleTitle = Objects.toString(row.get("title"), Objects.toString(row.get("logId"), ""));
            boolean missingDigest = StringUtils.isEmpty(Objects.toString(row.get("digest"), "").trim());
            boolean missingKeywords = StringUtils.isEmpty(Objects.toString(row.get("keywords"), "").trim());
            if (missingDigest) {
                articleMissingCount++;
                addSample(samples, "文章 SEO: " + articleTitle + " 缺少摘要");
            }
            if (missingKeywords) {
                articleMissingCount++;
                addSample(samples, "文章 SEO: " + articleTitle + " 缺少关键词");
            }
        }
        return new SeoResult(siteMissingCount, articleMissingCount, samples);
    }

    private void addSample(List<String> samples, String sample) {
        if (samples.size() < SAMPLE_LIMIT) {
            samples.add(sample);
        }
    }

    private DatabaseFragmentResult inspectDatabaseFragment() throws SQLException {
        DatabaseEngine engine = detectDatabaseEngine();
        if (!(Constants.zrLogConfig.getDataSource() instanceof DataSourceWrapper)) {
            return new DatabaseFragmentResult(engine, "Unknown", 0L, "Unknown", Collections.emptyList(), false);
        }
        DAO dao = new DAO((DataSourceWrapper) Constants.zrLogConfig.getDataSource());
        switch (engine) {
            case SQLITE:
                long freelistCount = toLong(dao.queryFirstObj("PRAGMA freelist_count"));
                long pageSize = toLong(dao.queryFirstObj("PRAGMA page_size"));
                long reclaimableBytes = freelistCount * pageSize;
                return new DatabaseFragmentResult(engine, "SQLite", reclaimableBytes, formatFileSize(reclaimableBytes),
                        Collections.emptyList(), true);
            case MYSQL:
                long dataFree = toLong(dao.queryFirstObj(
                        "SELECT COALESCE(SUM(data_free), 0) FROM information_schema.tables WHERE table_schema = DATABASE()"
                ));
                return new DatabaseFragmentResult(engine, "MySQL/MariaDB", dataFree, formatFileSize(dataFree),
                        Collections.emptyList(), true);
            case POSTGRESQL:
                long deadTuples = toLong(dao.queryFirstObj("SELECT COALESCE(SUM(n_dead_tup), 0) FROM pg_stat_user_tables"));
                return new DatabaseFragmentResult(engine, "PostgreSQL", deadTuples, Long.toString(deadTuples),
                        Collections.emptyList(), true);
            case H2:
                return new DatabaseFragmentResult(engine, "H2", 0L, "N/A", Collections.emptyList(), false);
            default:
                return new DatabaseFragmentResult(engine, "Unknown", 0L, "Unknown", Collections.emptyList(), false);
        }
    }

    private void runDatabaseOptimize(DatabaseEngine engine) throws SQLException {
        if (!(Constants.zrLogConfig.getDataSource() instanceof DataSourceWrapper)) {
            return;
        }
        DAO dao = new DAO((DataSourceWrapper) Constants.zrLogConfig.getDataSource());
        switch (engine) {
            case SQLITE:
                dao.execute("VACUUM");
                return;
            case MYSQL:
                List<Map<String, Object>> tables = dao.queryListWithParams(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'"
                );
                for (Map<String, Object> table : tables) {
                    String tableName = Objects.toString(table.get("table_name"), "");
                    if (StringUtils.isEmpty(tableName)) {
                        continue;
                    }
                    executeStatement("OPTIMIZE TABLE `" + tableName.replace("`", "") + "`");
                }
                return;
            case POSTGRESQL:
                executeStatement("VACUUM ANALYZE");
                return;
            default:
        }
    }

    private void executeStatement(String sql) throws SQLException {
        try (Connection connection = Constants.zrLogConfig.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private DatabaseEngine detectDatabaseEngine() {
        DataSource dataSource = Constants.zrLogConfig.getDataSource();
        if (!(dataSource instanceof DataSourceWrapper)) {
            return DatabaseEngine.UNKNOWN;
        }
        Properties properties = ((DataSourceWrapper) dataSource).getDataSourceProperties();
        String jdbcUrl = Objects.toString(properties.get("jdbcUrl"), "").toLowerCase(Locale.ROOT);
        if (jdbcUrl.contains(":sqlite:")) {
            return DatabaseEngine.SQLITE;
        }
        if (jdbcUrl.contains(":mysql:") || jdbcUrl.contains(":mariadb:")) {
            return DatabaseEngine.MYSQL;
        }
        if (jdbcUrl.contains(":postgresql:")) {
            return DatabaseEngine.POSTGRESQL;
        }
        if (jdbcUrl.contains(":h2:")) {
            return DatabaseEngine.H2;
        }
        String dbInfo = Objects.toString(((DataSourceWrapper) dataSource).getDbInfo(), "").toLowerCase(Locale.ROOT);
        if (dbInfo.contains("sqlite")) {
            return DatabaseEngine.SQLITE;
        }
        if (dbInfo.contains("mysql") || dbInfo.contains("mariadb")) {
            return DatabaseEngine.MYSQL;
        }
        if (dbInfo.contains("postgresql")) {
            return DatabaseEngine.POSTGRESQL;
        }
        if (dbInfo.contains("h2")) {
            return DatabaseEngine.H2;
        }
        return DatabaseEngine.UNKNOWN;
    }

    private int buildScore(long brokenLinks, long seoIssues, boolean hasDatabaseFragment, boolean hasDirectoryWritableIssue) {
        int score = 100;
        score -= Math.min(30, (int) brokenLinks * 5);
        score -= Math.min(25, (int) seoIssues * 3);
        if (hasDatabaseFragment) {
            score -= 10;
        }
        if (hasDirectoryWritableIssue) {
            score -= 20;
        }
        return Math.max(score, 0);
    }

    private boolean shouldCheckAsLocalAsset(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        String lowerCase = url.toLowerCase(Locale.ROOT);
        if (lowerCase.startsWith("http://") || lowerCase.startsWith("https://") || lowerCase.startsWith("//")
                || lowerCase.startsWith("mailto:") || lowerCase.startsWith("tel:") || lowerCase.startsWith("javascript:")
                || lowerCase.startsWith("data:") || lowerCase.startsWith("#")) {
            return false;
        }
        if (lowerCase.startsWith("./") || lowerCase.startsWith("../")) {
            return false;
        }
        if (lowerCase.startsWith(AdminConstants.ATTACHED_FOLDER)) {
            return true;
        }
        return lowerCase.startsWith("/") && lowerCase.matches(".*\\.[a-z0-9]{2,8}$");
    }

    private String normalizeUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return "";
        }
        String normalized = url.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }
        return normalized;
    }

    private long toLong(Object value) {
        if (Objects.isNull(value)) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String formatFileSize(long fileS) {
        DecimalFormat df = new DecimalFormat("#.00");
        if (fileS < 1024) {
            return df.format((double) fileS) + "B";
        } else if (fileS < 1048576) {
            return df.format((double) fileS / 1024) + "K";
        } else if (fileS < 1073741824) {
            return df.format((double) fileS / 1048576) + "M";
        } else if (fileS < 1099511627776L) {
            return df.format((double) fileS / 1073741824) + "G";
        }
        return df.format((double) fileS / 1099511627776L) + "T";
    }

    private enum DatabaseEngine {
        SQLITE, MYSQL, POSTGRESQL, H2, UNKNOWN
    }

    private static class BrokenLinkResult {
        private final long count;
        private final List<String> samples;

        private BrokenLinkResult(long count, List<String> samples) {
            this.count = count;
            this.samples = samples;
        }
    }

    private static class SeoResult {
        private final long siteMissingCount;
        private final long articleMissingCount;
        private final List<String> samples;

        private SeoResult(long siteMissingCount, long articleMissingCount, List<String> samples) {
            this.siteMissingCount = siteMissingCount;
            this.articleMissingCount = articleMissingCount;
            this.samples = samples;
        }
    }

    private static class DatabaseFragmentResult {
        private final DatabaseEngine engine;
        private final String engineLabel;
        private final long fragmentValue;
        private final String fragmentLabel;
        private final List<String> samples;
        private final boolean canOptimize;

        private DatabaseFragmentResult(DatabaseEngine engine, String engineLabel, long fragmentValue, String fragmentLabel,
                                       List<String> samples, boolean canOptimize) {
            this.engine = engine;
            this.engineLabel = engineLabel;
            this.fragmentValue = fragmentValue;
            this.fragmentLabel = fragmentLabel;
            this.samples = samples;
            this.canOptimize = canOptimize;
        }
    }

    private static class DirectoryWritableResult {
        private final long count;
        private final List<String> samples;

        private DirectoryWritableResult(long count, List<String> samples) {
            this.count = count;
            this.samples = samples;
        }
    }
}
