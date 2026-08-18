package com.secondbrain.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentStateResponse {

    private String repository;
    private String project;
    private String task;

    // Working Tree & Git State
    private String gitBranch;
    private String gitStatus;
    private Integer modifiedFilesCount;
    @Builder.Default
    private List<String> modifiedFiles = new ArrayList<>();
    @Builder.Default
    private List<String> untrackedFiles = new ArrayList<>();
    @Builder.Default
    private List<String> deletedFiles = new ArrayList<>();
    private String lastCommitSha;
    private String lastCommitMessage;

    // Active Agent & Session
    private String lastActiveAgent;
    private String lastActiveSessionId;
    private String lastActiveTimestamp;

    // Distinct Task vs Attempt Lifecycle
    @Builder.Default
    private List<String> completedTasks = new ArrayList<>();
    @Builder.Default
    private List<String> successfulAttempts = new ArrayList<>();
    @Builder.Default
    private List<String> inProgressTasks = new ArrayList<>();
    @Builder.Default
    private List<String> activeTrials = new ArrayList<>();

    // Distinct Blocker vs Failure Taxonomy
    @Builder.Default
    private List<String> currentBlockers = new ArrayList<>();
    private LastFailureSummary lastFailedAttempt;
    @Builder.Default
    private List<FailureItem> historicalFailures = new ArrayList<>();

    // Task-Relevant Semantic Knowledge
    @Builder.Default
    private List<String> relevantEstablishedKnowledge = new ArrayList<>();

    // Structured Next Action Recommendations
    @Builder.Default
    private List<NextActionRecommendation> nextRecommendedActions = new ArrayList<>();

    private String formattedBriefing;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LastFailureSummary {
        private String agentName;
        private String approach;
        private String failureReason;
        private String lessonLearned;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureItem {
        private String agentName;
        private String task;
        private String approach;
        private String errorMessage;
        private String lessonLearned;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextActionRecommendation {
        private String priority; // CRITICAL, HIGH, MEDIUM, LOW
        private String action;
        private String reason;
        @Builder.Default
        private List<String> evidence = new ArrayList<>();
    }
}
