package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.type.FileDirectoryAction;
import com.zrlog.admin.business.type.FileEntryAccess;
import com.zrlog.admin.business.type.FileEntryAction;

import java.util.Collections;
import java.util.List;

public class FileEntryVO {

    private String name;
    private String path;
    private String type; // "file" | "directory"
    private long size;
    private String mimeType;
    private long lastModified;
    private boolean image;
    private boolean textPreviewable;
    private String iconType;
    private boolean virtual;
    private FileEntryAccess access = FileEntryAccess.VIRTUAL;
    private List<FileEntryAction> actions = Collections.emptyList();
    private List<FileDirectoryAction> directoryActions = Collections.emptyList();
    private boolean referenced;
    private int referenceCount;
    private List<FileReferenceVO> references = Collections.emptyList();
    private boolean missing;
    private String missingReason;

    public FileEntryVO() {
    }

    public FileEntryVO(String name, String path, String type, long size, String mimeType, long lastModified) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.size = size;
        this.mimeType = mimeType;
        this.lastModified = lastModified;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    public boolean isImage() {
        return image;
    }

    public void setImage(boolean image) {
        this.image = image;
    }

    public boolean isTextPreviewable() {
        return textPreviewable;
    }

    public void setTextPreviewable(boolean textPreviewable) {
        this.textPreviewable = textPreviewable;
    }

    public String getIconType() {
        return iconType;
    }

    public void setIconType(String iconType) {
        this.iconType = iconType;
    }

    public boolean isVirtual() {
        return virtual;
    }

    public void setVirtual(boolean virtual) {
        this.virtual = virtual;
    }

    public FileEntryAccess getAccess() {
        return access;
    }

    public void setAccess(FileEntryAccess access) {
        this.access = access;
    }

    public List<FileEntryAction> getActions() {
        return actions;
    }

    public void setActions(List<FileEntryAction> actions) {
        this.actions = actions == null ? Collections.emptyList() : actions;
    }

    public List<FileDirectoryAction> getDirectoryActions() {
        return directoryActions;
    }

    public void setDirectoryActions(List<FileDirectoryAction> directoryActions) {
        this.directoryActions = directoryActions == null ? Collections.emptyList() : directoryActions;
    }

    public boolean isReferenced() {
        return referenced;
    }

    public void setReferenced(boolean referenced) {
        this.referenced = referenced;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public void setReferenceCount(int referenceCount) {
        this.referenceCount = referenceCount;
    }

    public List<FileReferenceVO> getReferences() {
        return references;
    }

    public void setReferences(List<FileReferenceVO> references) {
        this.references = references;
    }

    public boolean isMissing() {
        return missing;
    }

    public void setMissing(boolean missing) {
        this.missing = missing;
    }

    public String getMissingReason() {
        return missingReason;
    }

    public void setMissingReason(String missingReason) {
        this.missingReason = missingReason;
    }
}
