package com.zrlog.admin.business.ai.model;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.util.PathUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Hot-loads a small local catalog. Network synchronization is an external scheduled job. */
public final class AIModelCatalog {
    public static final String RESOURCE = "/ai/models.json";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Logger LOGGER = LoggerUtil.getLogger(AIModelCatalog.class);

    private final Map<AIProviderType, List<AIModelEntry>> bundled;
    private Map<AIProviderType, List<AIModelEntry>> current;
    private Path currentPath;
    private String observedContent;
    private boolean readFailure;

    private static class Holder {
        private static final AIModelCatalog INSTANCE = new AIModelCatalog();
    }

    AIModelCatalog() {
        try (InputStream input = AIModelCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled AI model catalog");
            }
            bundled = parse(read(input));
            current = bundled;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read bundled AI model catalog", e);
        }
    }

    public static List<AIModelEntry> getModels(AIProviderType provider) {
        String override = System.getProperty("zrlog.ai.modelCatalog");
        Path path = override == null || override.isBlank()
                ? PathUtil.getConfFile("ai-models.json").toPath() : Path.of(override);
        return Holder.INSTANCE.getModels(provider, path);
    }

    synchronized List<AIModelEntry> getModels(AIProviderType provider, Path path) {
        path = path.toAbsolutePath().normalize();
        if (!path.equals(currentPath)) {
            currentPath = path;
            current = bundled;
            observedContent = null;
            readFailure = false;
        }
        try (InputStream input = Files.newInputStream(path)) {
            String content = read(input);
            readFailure = false;
            if (!Objects.equals(content, observedContent)) {
                // Remember rejected contents too, to avoid logging the same bad update on every request.
                observedContent = content;
                try {
                    current = parse(content);
                } catch (RuntimeException e) {
                    LOGGER.warning("Invalid AI model catalog; keeping previous models: " + path);
                }
            }
        } catch (NoSuchFileException e) {
            current = bundled;
            observedContent = null;
            readFailure = false;
        } catch (IOException | SecurityException e) {
            if (!readFailure) {
                LOGGER.warning("Unable to read AI model catalog; keeping previous models: " + path);
                readFailure = true;
            }
        }
        // DTOs are mutable; callers must not be able to corrupt the shared snapshot.
        return current.get(provider).stream()
                .map(AIModelEntry::new)
                .collect(Collectors.toList());
    }

    private static String read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("AI model catalog exceeds size limit");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static Map<AIProviderType, List<AIModelEntry>> parse(String content) {
        AIModelCatalogDocument document = new Gson().fromJson(content, AIModelCatalogDocument.class);
        require(document != null && document.schemaVersion == 1 && document.providers != null);
        Map<AIProviderType, List<AIModelEntry>> result = new EnumMap<>(AIProviderType.class);
        for (AIModelCatalogDocument.Provider provider : document.providers) {
            require(provider != null && provider.name != null && !result.containsKey(provider.name));
            require(provider.models != null && !provider.models.isEmpty() && provider.models.size() <= 512);
            Set<String> names = new HashSet<>();
            for (AIModelEntry model : provider.models) {
                require(model != null && model.getName() != null
                        && model.getName().matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
                        && names.add(model.getName()));
                List<AIModelCapability> capabilities = model.getCapabilities();
                require(capabilities != null && !capabilities.isEmpty() && !capabilities.contains(null)
                        && new HashSet<>(capabilities).size() == capabilities.size());
                require(!model.supports(AIModelCapability.IMAGE_GENERATION)
                        || provider.name == AIProviderType.OPEN_AI || provider.name == AIProviderType.GOOGLE_GEMINI);
                require(!model.isRetired() || (model.getRetirementSource() != null
                        && !model.getRetirementSource().isBlank() && model.getRetirementSource().length() <= 2048));
            }
            require(provider.models.stream().anyMatch(model -> model.supports(AIModelCapability.TEXT)));
            result.put(provider.name, List.copyOf(provider.models));
        }
        require(result.size() == AIProviderType.values().length);
        return result;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("Invalid AI model catalog");
        }
    }
}
