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

    private String gitBranch;
    private String gitStatus;
    private Integer modifiedFilesCount;
    private String lastCommitSha;
    private String lastCommitMessage;

    private String lastActiveAgent;
    private String lastActiveSessionId;
    private String lastActiveTimestamp;

    @Builder.Default
    private List<String> completedItems = new ArrayList<>();

    @Builder.Default
    private List<String> inProgressItems = new ArrayList<>();

    @Builder.Default
    private List<String> knownIssues = new ArrayList<>();

    private LastFailureSummary lastFailedAttempt;

    @Builder.Default
    private List<String> relevantEstablishedKnowledge = new ArrayList<>();

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
}
