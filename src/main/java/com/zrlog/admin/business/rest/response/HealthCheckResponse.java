package com.zrlog.admin.business.rest.response;

import java.util.List;

public class HealthCheckResponse {

    private final long checkedAt;
    private final int score;
    private final long brokenLinkCount;
    private final long seoIssueCount;
    private final long databaseFragmentValue;
    private final String databaseFragmentLabel;
    private final String databaseEngine;
    private final boolean canOptimizeDatabase;
    private final List<HealthCheckIssueResponse> issues;
    private final List<HealthCheckSuggestionResponse> suggestions;

    public HealthCheckResponse(long checkedAt, int score, long brokenLinkCount, long seoIssueCount, long databaseFragmentValue,
                               String databaseFragmentLabel, String databaseEngine, boolean canOptimizeDatabase,
                               List<HealthCheckIssueResponse> issues, List<HealthCheckSuggestionResponse> suggestions) {
        this.checkedAt = checkedAt;
        this.score = score;
        this.brokenLinkCount = brokenLinkCount;
        this.seoIssueCount = seoIssueCount;
        this.databaseFragmentValue = databaseFragmentValue;
        this.databaseFragmentLabel = databaseFragmentLabel;
        this.databaseEngine = databaseEngine;
        this.canOptimizeDatabase = canOptimizeDatabase;
        this.issues = issues;
        this.suggestions = suggestions;
    }

    public long getCheckedAt() {
        return checkedAt;
    }

    public int getScore() {
        return score;
    }

    public long getBrokenLinkCount() {
        return brokenLinkCount;
    }

    public long getSeoIssueCount() {
        return seoIssueCount;
    }

    public long getDatabaseFragmentValue() {
        return databaseFragmentValue;
    }

    public String getDatabaseFragmentLabel() {
        return databaseFragmentLabel;
    }

    public String getDatabaseEngine() {
        return databaseEngine;
    }

    public boolean isCanOptimizeDatabase() {
        return canOptimizeDatabase;
    }

    public List<HealthCheckIssueResponse> getIssues() {
        return issues;
    }

    public List<HealthCheckSuggestionResponse> getSuggestions() {
        return suggestions;
    }
}
