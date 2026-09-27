package com.zrlog.admin.business.ai.model;

import java.util.List;

/** Versioned data file; never contains credentials or configurable API endpoints. */
public class AIModelCatalogDocument {
    int schemaVersion;
    List<Provider> providers;

    public static class Provider {
        AIProviderType name;
        String source;
        List<AIModelEntry> models;
    }
}
