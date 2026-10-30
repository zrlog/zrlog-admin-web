package com.zrlog.admin.business.automation;

import com.zrlog.common.Validator;

import java.util.ArrayList;
import java.util.List;

public final class AutomationViews {
    private AutomationViews() { }

    public static class Task extends AutomationModels.Plan {
        public boolean systemTask;
        public Integer updatedBy;
        public List<String> requiredActions = new ArrayList<>();
        public long createdAt;
        public long updatedAt;
        public Long nextRunAt;
        public Long lastRunAt;
        public String lastStatus;
        public String activeRunId;
    }

    public static class Overview {
        public List<AutomationModels.Definition> definitions = new ArrayList<>();
        public List<Task> tasks = new ArrayList<>();
        public List<Task> systemTasks = new ArrayList<>();
        public long total;
        public boolean aiAvailable;
        public boolean canManageScheduler;
    }

    public static class Runs {
        public List<Run> items = new ArrayList<>();
        public long total;
        public int page;
        public int pageSize;
    }

    public static class Run extends AutomationModels.Run {
        public String summary;
    }

    public static class Settings {
        public AutomationSettingsService.SettingsView settings;
        public AutomationTickService.Health internal;
        public AutomationTickService.Health external;
    }

    public static class Change implements Validator {
        public String id;
        public Long revision;
        public Boolean enabled;

        @Override
        public void doValid() {
            if (id == null || id.isBlank() || revision == null || revision < 1) {
                throw new IllegalArgumentException("automation.invalidPlan");
            }
        }
    }

    public static class SettingsChange implements Validator {
        public Integer externalBudgetSeconds;
        public Long revision;
        public Boolean internalEnabled;
        public Boolean externalEnabled;

        @Override
        public void doValid() {
            if (revision == null || revision < 0) throw new IllegalArgumentException("automation.invalidPlan");
        }
    }
}
