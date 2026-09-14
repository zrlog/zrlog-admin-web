package com.zrlog.admin.business.ai.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class AIModelCatalogTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String bundled() throws Exception {
        try (var input = getClass().getResourceAsStream(AIModelCatalog.RESOURCE)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void shouldHotLoadAndKeepLastValidCatalogOnInvalidOrOversizedUpdate() throws Exception {
        AIModelCatalog catalog = new AIModelCatalog();
        Path path = temporaryFolder.getRoot().toPath().resolve("models.json");
        String original = catalog.getModels(AIProviderType.OPEN_AI, path).get(0).getName();
        Files.writeString(path, bundled().replace(original, "future-model"));
        assertEquals("future-model", catalog.getModels(AIProviderType.OPEN_AI, path).get(0).getName());
        Files.writeString(path, "{broken");
        assertEquals("future-model", catalog.getModels(AIProviderType.OPEN_AI, path).get(0).getName());
        Files.writeString(path, " ".repeat(1024 * 1024 + 1));
        assertEquals("future-model", catalog.getModels(AIProviderType.OPEN_AI, path).get(0).getName());
        Files.writeString(path, bundled().replace(original, "another-model"));
        assertEquals("another-model", catalog.getModels(AIProviderType.OPEN_AI, path).get(0).getName());
        Files.delete(path);
        assertEquals(original, catalog.getModels(AIProviderType.OPEN_AI, path).get(0).getName());
    }

    @Test
    public void shouldFallBackOnColdStartAndNotShareMutableEntriesOrRuntimeRoots() throws Exception {
        AIModelCatalog catalog = new AIModelCatalog();
        Path path = temporaryFolder.newFile("bad.json").toPath();
        Files.writeString(path, "null");
        List<AIModelEntry> first = catalog.getModels(AIProviderType.OPEN_AI, path);
        String original = first.get(0).getName();
        first.get(0).setName("mutated");
        first.get(0).setCapabilities(List.of(AIModelCapability.IMAGE_GENERATION));
        assertEquals(original, catalog.getModels(AIProviderType.OPEN_AI, path).get(0).getName());
        assertTrue(catalog.getModels(AIProviderType.OPEN_AI, path).get(0).supports(AIModelCapability.TEXT));
        Files.writeString(path, bundled().replace(original, "root-one"));
        assertEquals("root-one", catalog.getModels(AIProviderType.OPEN_AI, path).get(0).getName());
        assertEquals(original, catalog.getModels(AIProviderType.OPEN_AI,
                temporaryFolder.getRoot().toPath().resolve("other-root.json")).get(0).getName());
    }

    @Test
    public void shouldRejectUnknownDuplicateMissingAndInvalidCatalogEntries() throws Exception {
        String source = bundled();
        assertInvalid(source.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"));
        assertInvalid(source.replace("\"DEEP_SEEK\"", "\"UNKNOWN\""));
        assertInvalid(source.replace("\"QWEN\"", "\"DEEP_SEEK\""));
        assertInvalid(source.replace("\"TEXT\"", "\"VIDEO\""));
        assertInvalid(source.replace("deepseek-flash", "bad model"));
        JsonObject document = JsonParser.parseString(source).getAsJsonObject();
        document.getAsJsonArray("providers").remove(0);
        assertInvalid(document.toString());
        document = JsonParser.parseString(source).getAsJsonObject();
        var models = document.getAsJsonArray("providers").get(0).getAsJsonObject().getAsJsonArray("models");
        models.add(models.get(0).deepCopy());
        assertInvalid(document.toString());
        assertInvalid(source.replace("\"TEXT\"", "\"IMAGE_GENERATION\""));
    }

    @Test
    public void shouldUseExternalImageCatalogForExistingSettingsValidation() throws Exception {
        String property = System.getProperty("zrlog.ai.modelCatalog");
        Path path = temporaryFolder.newFile("override.json").toPath();
        Files.writeString(path, bundled().replace("gpt-image-2.5-sunburst", "future-image"));
        try {
            System.setProperty("zrlog.ai.modelCatalog", path.toString());
            assertTrue(AIProviderType.OPEN_AI.getImageModels().contains("future-image"));
            assertFalse(AIProviderType.OPEN_AI.getModels().contains("future-image"));
            AIWebSiteInfo settings = new AIWebSiteInfo();
            settings.setAi_provider(AIProviderType.OPEN_AI);
            settings.setAi_model("custom-text-model");
            settings.setAi_image_provider(AIProviderType.OPEN_AI);
            settings.setAi_image_model("future-image");
            settings.doValid();
            assertEquals("future-image", settings.getAi_image_model());
        } finally {
            if (property == null) {
                System.clearProperty("zrlog.ai.modelCatalog");
            } else {
                System.setProperty("zrlog.ai.modelCatalog", property);
            }
        }
    }

    private void assertInvalid(String content) {
        assertThrows(RuntimeException.class, () -> AIModelCatalog.parse(content));
    }
}
