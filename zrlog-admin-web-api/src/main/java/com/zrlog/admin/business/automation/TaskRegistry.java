package com.zrlog.admin.business.automation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Providers are explicitly assembled by the host; task payloads never select a Java class or URL.
 */
public final class TaskRegistry {
    private final Supplier<List<ScheduledTaskProvider>> providers;

    public TaskRegistry(Supplier<List<ScheduledTaskProvider>> providers) {
        this.providers = Objects.requireNonNull(providers);
    }

    public Collection<Entry> entries() {
        Lookup lookup = lookup();
        return lookup.providers.keySet().stream()
                .map(lookup::require)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<AutomationModels.Definition> definitions() {
        List<AutomationModels.Definition> definitions = new ArrayList<>();
        entries().forEach(entry -> definitions.add(entry.definition));
        return definitions;
    }

    public List<AutomationModels.Definition> systemDefinitions() {
        var supplied = providers.get();
        var lookup = new Lookup(supplied);
        List<AutomationModels.Definition> result = new ArrayList<>();
        for (var provider : supplied)
            for (var definition : provider.systemDefinitions()) {
                if (!definition.systemTask || lookup.provider(definition.type) != provider)
                    throw new IllegalStateException("automation.invalidDefinition");
                result.add(definition);
            }
        return result;
    }

    public Entry require(String type) {
        return lookup().require(type);
    }

    public ScheduledTaskProvider provider(String type) {
        return lookup().provider(type);
    }

    /**
     * Explicit operation lifetime: runtime configuration is never cached across ticks or requests.
     */
    public Lookup lookup() {
        return new Lookup(providers.get());
    }

    public static final class Lookup {
        private final Map<String, ScheduledTaskProvider> providers = new LinkedHashMap<>();
        private final Map<String, Entry> loaded = new LinkedHashMap<>();

        private Lookup(List<ScheduledTaskProvider> supplied) {
            for (var provider : supplied)
                for (String type : provider.types()) {
                    if (type == null
                            || !type.matches("[a-z][a-z0-9._-]{1,79}")
                            || providers.putIfAbsent(type, provider) != null)
                        throw new IllegalStateException("automation.invalidDefinition");
                }
        }

        public ScheduledTaskProvider provider(String type) {
            var provider = providers.get(type);
            if (provider == null) throw new IllegalArgumentException("automation.unknownTask");
            return provider;
        }

        public Entry require(String type) {
            if (loaded.containsKey(type)) return loaded.get(type);
            var provider = provider(type);
            Map<String, Entry> definitions = new LinkedHashMap<>();
            for (var definition : provider.definitions()) {
                if (providers.get(definition.type) != provider
                        || definition.timeoutSeconds < 1
                        || definition.timeoutSeconds > 650
                        || definitions.putIfAbsent(definition.type, new Entry(provider, definition))
                                != null)
                    throw new IllegalStateException("automation.invalidDefinition");
            }
            for (var registration : providers.entrySet()) {
                if (registration.getValue() == provider
                        && !definitions.containsKey(registration.getKey()))
                    throw new IllegalStateException("automation.invalidDefinition");
            }
            loaded.putAll(definitions);
            return loaded.get(type);
        }
    }

    public static final class Entry {
        public final ScheduledTaskProvider provider;
        public final AutomationModels.Definition definition;

        Entry(ScheduledTaskProvider provider, AutomationModels.Definition definition) {
            this.provider = provider;
            this.definition = definition;
        }
    }
}
