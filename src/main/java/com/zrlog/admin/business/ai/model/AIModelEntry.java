package com.zrlog.admin.business.ai.model;

import java.util.Arrays;
import java.util.List;

public class AIModelEntry {

    private String name;
    private List<AIModelCapability> capabilities;

    public AIModelEntry() {
    }

    public AIModelEntry(String name, AIModelCapability... capabilities) {
        this.name = name;
        this.capabilities = Arrays.asList(capabilities);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<AIModelCapability> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<AIModelCapability> capabilities) {
        this.capabilities = capabilities;
    }

    public boolean supports(AIModelCapability capability) {
        return capabilities != null && capabilities.contains(capability);
    }
}
