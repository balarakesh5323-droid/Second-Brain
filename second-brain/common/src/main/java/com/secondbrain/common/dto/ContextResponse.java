package com.secondbrain.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextResponse {

    private String project;
    private String repository;
    private List<ContextItem> relevantContext;
    private List<ContextItem> architecture;
    private List<AgentSummary> previousAgents;
    private List<ContextItem> recentChanges;
    private List<DecisionSummary> decisions;
    private List<TaskSummary> openTasks;
    private List<ContextItem> knownProblems;
    private List<String> developerPreferences;
    private List<ContextItem> skills;
    private List<String> sources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextItem {
        private String id;
        private String type;
        private String content;
        private Double score;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentSummary {
        private String name;
        private String type;
        private String lastTask;
        private String lastSessionSummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionSummary {
        private String id;
        private String title;
        private String rationale;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskSummary {
        private String id;
        private String title;
        private String description;
        private Integer priority;
        private String status;
    }
}
