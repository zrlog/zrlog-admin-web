package com.zrlog.admin.business.ai.model;

import java.util.Arrays;
import java.util.List;

public class AIModelEntry {

    private String name;
    private List<AIModelCapability> capabilities;
    private Boolean retired;
    private String retirementSource;

    public AIModelEntry() {
    }

    public AIModelEntry(String name, AIModelCapability... capabilities) {
        this.name = name;
        this.capabilities = Arrays.asList(capabilities);
    }

    public AIModelEntry(AIModelEntry model) {
        this(model.name, model.capabilities.toArray(new AIModelCapability[0]));
        this.retired = model.retired;
        this.retirementSource = model.retirementSource;
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

    public boolean isRetired() {
        return Boolean.TRUE.equals(retired);
    }

    public void setRetired(Boolean retired) {
        this.retired = retired;
    }

    public String getRetirementSource() {
        return retirementSource;
    }

    public void setRetirementSource(String retirementSource) {
        this.retirementSource = retirementSource;
    }

    public boolean supports(AIModelCapability capability) {
        return capabilities != null && capabilities.contains(capability);
    }
}
