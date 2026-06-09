package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.type.FileDirectoryAction;

import java.util.Collections;
import java.util.List;

public class FileManagerResponse {

    private List<FileEntryVO> shortcuts = Collections.emptyList();
    private List<FileEntryVO> entries = Collections.emptyList();
    private List<FileDirectoryAction> directoryActions = Collections.emptyList();

    public List<FileEntryVO> getShortcuts() {
        return shortcuts;
    }

    public void setShortcuts(List<FileEntryVO> shortcuts) {
        this.shortcuts = shortcuts == null ? Collections.emptyList() : shortcuts;
    }

    public List<FileEntryVO> getEntries() {
        return entries;
    }

    public void setEntries(List<FileEntryVO> entries) {
        this.entries = entries == null ? Collections.emptyList() : entries;
    }

    public List<FileDirectoryAction> getDirectoryActions() {
        return directoryActions;
    }

    public void setDirectoryActions(List<FileDirectoryAction> directoryActions) {
        this.directoryActions = directoryActions == null ? Collections.emptyList() : directoryActions;
    }
}
