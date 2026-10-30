package com.zrlog.admin.business.automation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Providers are explicitly assembled by the host; task payloads never select a Java class or URL. */
public final class TaskRegistry {
    private final Supplier<List<ScheduledTaskProvider>> providers;

    public TaskRegistry(Supplier<List<ScheduledTaskProvider>> providers) {
        this.providers = Objects.requireNonNull(providers);
    }

    public Collection<Entry> entries() {
        Map<String, Entry> result = new LinkedHashMap<>();
        for (ScheduledTaskProvider provider : providers.get()) {
            for (AutomationModels.Definition definition : provider.definitions()) {
                if (definition.type == null || !definition.type.matches("[a-z][a-z0-9._-]{1,79}")
                        || definition.timeoutSeconds < 1 || definition.timeoutSeconds > 650
                        || result.putIfAbsent(definition.type, new Entry(provider, definition)) != null) {
                    throw new IllegalStateException("automation.invalidDefinition");
                }
            }
        }
        return result.values();
    }

    public List<AutomationModels.Definition> definitions() {
        List<AutomationModels.Definition> definitions = new ArrayList<>();
        entries().forEach(entry -> definitions.add(entry.definition));
        return definitions;
    }

    public Entry require(String type) {
        return entries().stream().filter(entry -> entry.definition.type.equals(type)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("automation.unknownTask"));
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
