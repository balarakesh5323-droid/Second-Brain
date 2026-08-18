package com.secondbrain.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkHistoryResponse {

    private String repository;
    private String project;
    private String taskQuery;
    private int totalActivities;

    @Builder.Default
    private List<AgentWorkItem> chronologicalWork = new ArrayList<>();

    @Builder.Default
    private List<DecisionItem> activeDecisions = new ArrayList<>();

    @Builder.Default
    private List<MemoryItem> consolidatedKnowledge = new ArrayList<>();

    private String formattedNarrative;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentWorkItem {
        private String agentName;
        private String sessionId;
        private LocalDateTime timestamp;
        private String activityType; // "FAILED_ATTEMPT", "DECISION_MADE", "EVENT", "SESSION_COMPLETED"
        private String task;
        private String approach;
        private String outcome; // "SUCCESS", "FAILURE", "IN_PROGRESS"
        private String failureReason;
        private String lessonLearned;
        private List<String> filesChanged;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionItem {
        private String title;
        private String rationale;
        private String agentName;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryItem {
        private String memoryKey;
        private String content;
        private String status;
        private Double confidence;
        private String provenanceSource;
    }
}
