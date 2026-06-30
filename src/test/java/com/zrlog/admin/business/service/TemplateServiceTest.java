package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.BaseTemplateVO;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.model.WebSite;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TemplateServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldDiscoverLocalTemplateAndLoadConfigOverridesFromRealDatabase() throws Exception {
        withRootPath(() -> {
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.cacheService().getPublicWebSiteInfo().setTemplate(Constants.DEFAULT_TEMPLATE_PATH);
                String templatePath = createLocalTemplate("local-theme");
                db.putWebsite(templatePath + "_setting", "{\"accent\":\"db-blue\"}");
                TemplateService service = new TemplateService();

                List<BaseTemplateVO> templates = service.getAllTemplates(templatePath);
                BaseTemplateVO local = templates.stream()
                        .filter(template -> templatePath.equals(template.getTemplate()))
                        .findFirst()
                        .orElse(null);
                TemplateVO config = service.loadTemplateConfig(templatePath);

                assertNotNull(local);
                assertEquals("Local Theme", local.getName());
                assertTrue(local.isDeleteAble());
                assertTrue(local.isConfigAble());
                assertTrue(local.isPreview());
                assertFalse(local.isUse());
                assertEquals(".ftl", local.getViewType());
                assertEquals("db-blue", config.getConfig().get("accent").getValue());
                assertEquals(templatePath, config.getConfig().get("template").getValue());
                assertEquals("hidden", config.getConfig().get("template").getType());
            }
        });
    }

    @Test
    public void shouldSanitizeAndPersistTemplateConfigThroughRealWebsiteTable() throws Exception {
        withRootPath(() -> {
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.cacheService().getPublicWebSiteInfo().setTemplate(Constants.DEFAULT_TEMPLATE_PATH);
                String templatePath = createLocalTemplate("sanitize-theme");
                TemplateService service = new TemplateService();
                Map<String, Object> settings = new LinkedHashMap<>();
                settings.put("accent", "<b>blue</b><script>bad()</script>");
                settings.put("customCss", "<style>.card{color:red}</style><script>bad()</script>");
                settings.put("layoutYml", "title: <b>keep</b>");
                settings.put("customHtml", "<strong>keep html</strong>");
                settings.put("unknown", "<script>ignored()</script>");

                UpdateRecordResponse response = service.save(templatePath, settings);
                Map<String, Object> stored = new WebSite().getTemplateConfigMap(templatePath);

                assertNotNull(response.getMessage());
                assertEquals("blue", stored.get("accent"));
                assertTrue(String.valueOf(stored.get("customCss")).contains("<style>"));
                assertFalse(String.valueOf(stored.get("customCss")).contains("<script>"));
                assertEquals("title: <b>keep</b>", stored.get("layoutYml"));
                assertEquals("<strong>keep html</strong>", stored.get("customHtml"));
                assertEquals("<script>ignored()</script>", stored.get("unknown"));
                assertNotNull(db.queryOne("select value from website where name=?", templatePath + "_setting"));
            }
        });
    }

    private String createLocalTemplate(String shortName) throws Exception {
        File templateDir = new File(temporaryFolder.getRoot(), "static/include/templates/" + shortName);
        File settingDir = new File(templateDir, "setting");
        assertTrue(settingDir.mkdirs());
        Files.writeString(new File(templateDir, "template.properties").toPath(),
                "name=Local Theme\n"
                        + "author=ZrLog\n"
                        + "digest=Local digest\n"
                        + "version=1.0.0\n"
                        + "url=https://example.com\n"
                        + "previewImages=images/preview.png\n",
                StandardCharsets.UTF_8);
        Files.writeString(new File(templateDir, "index.ftl").toPath(), "<html></html>", StandardCharsets.UTF_8);
        Files.writeString(new File(settingDir, "config-form.json").toPath(),
                "{"
                        + "\"accent\":{\"label\":\"Accent\",\"htmlElementType\":\"input\",\"contentType\":\"text\",\"value\":\"red\"},"
                        + "\"customCss\":{\"label\":\"CSS\",\"htmlElementType\":\"textarea\",\"contentType\":\"css\",\"value\":\"\"},"
                        + "\"layoutYml\":{\"label\":\"YAML\",\"htmlElementType\":\"textarea\",\"contentType\":\"yml\",\"value\":\"\"},"
                        + "\"customHtml\":{\"label\":\"HTML\",\"htmlElementType\":\"textarea\",\"contentType\":\"html\",\"value\":\"\"}"
                        + "}",
                StandardCharsets.UTF_8);
        return Constants.TEMPLATE_BASE_PATH + shortName;
    }

    private void withRootPath(ThrowingRunnable runnable) throws Exception {
        String previousRootPath = System.getProperty("sws.root.path");
        try {
            System.setProperty("sws.root.path", temporaryFolder.getRoot().getAbsolutePath());
            runnable.run();
        } finally {
            if (previousRootPath == null) {
                System.clearProperty("sws.root.path");
            } else {
                System.setProperty("sws.root.path", previousRootPath);
            }
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
