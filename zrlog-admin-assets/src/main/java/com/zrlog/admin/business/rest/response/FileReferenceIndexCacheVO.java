package com.zrlog.admin.business.rest.response;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FileReferenceIndexCacheVO {

    private String signature;
    private Map<String, List<FileReferenceVO>> localReferences = Collections.emptyMap();
    private Map<String, List<FileReferenceVO>> externalReferences = Collections.emptyMap();

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public Map<String, List<FileReferenceVO>> getLocalReferences() {
        return localReferences;
    }

    public void setLocalReferences(Map<String, List<FileReferenceVO>> localReferences) {
        this.localReferences = localReferences;
    }

    public Map<String, List<FileReferenceVO>> getExternalReferences() {
        return externalReferences;
    }

    public void setExternalReferences(Map<String, List<FileReferenceVO>> externalReferences) {
        this.externalReferences = externalReferences;
    }
}
