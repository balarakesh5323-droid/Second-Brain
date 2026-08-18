package com.secondbrain.service;

import com.secondbrain.common.dto.CurrentStateResponse;
import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.TaskStatus;
import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurrentStateService {

    private final RepositoryEntityRepository repositoryRepository;
    private final ProjectRepository projectRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentAttemptRepository attemptRepository;
    private final TaskRepository taskRepository;
    private final MemoryRepository memoryRepository;
    private final GitService gitService;
    private final SemanticSearchService semanticSearchService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<MemoryStatus> ACTIVE_STATUSES = List.of(MemoryStatus.ESTABLISHED, MemoryStatus.CONFIRMED);
    private static final List<String> ACTIVE_STATUS_STRS = List.of("ESTABLISHED", "CONFIRMED");

    @Transactional(readOnly = true)
    public CurrentStateResponse getCurrentState(String repoIdOrName, String projectIdOrName, String targetTask) {
        RepositoryEntity repo = resolveRepository(repoIdOrName);
        Project project = resolveProject(projectIdOrName, repo);

        UUID repoId = repo != null ? repo.getId() : null;
        UUID projId = project != null ? project.getId() : null;

        CurrentStateResponse.CurrentStateResponseBuilder builder = CurrentStateResponse.builder()
                .repository(repo != null ? repo.getName() : (repoIdOrName != null ? repoIdOrName : "Global"))
                .project(project != null ? project.getName() : (projectIdOrName != null ? projectIdOrName : "Default"))
                .task(targetTask != null ? targetTask : "Active engineering task");

        // 1. Working Tree & Git State
        List<String> modifiedFiles = new ArrayList<>();
        List<String> untrackedFiles = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();

        if (repo != null && repo.getPath() != null) {
            try {
                var gitStatus = gitService.getWorkingTreeStatus(repo.getPath());
                builder.gitBranch(gitService.getCurrentBranch(repo.getPath()))
                        .gitStatus((String) gitStatus.get("state"))
                        .modifiedFilesCount((Integer) gitStatus.get("modifiedCount"));

                if (gitStatus.get("modifiedFiles") instanceof List<?> list) {
                    for (Object item : list) if (item != null) modifiedFiles.add(item.toString());
                }
                if (gitStatus.get("untrackedFiles") instanceof List<?> list) {
                    for (Object item : list) if (item != null) untrackedFiles.add(item.toString());
                }
                if (gitStatus.get("missingFiles") instanceof List<?> list) {
                    for (Object item : list) if (item != null) deletedFiles.add(item.toString());
                }

                var logEntries = gitService.getRecentCommits(repo.getPath(), 1);
                if (!logEntries.isEmpty()) {
                    var last = logEntries.get(0);
                    builder.lastCommitSha((String) last.get("id"))
                            .lastCommitMessage((String) last.get("message"));
                }
            } catch (Exception e) {
                builder.gitStatus("UNKNOWN");
            }
        }
        builder.modifiedFiles(modifiedFiles)
                .untrackedFiles(untrackedFiles)
                .deletedFiles(deletedFiles);

        // 2. Last Active Agent & Session Lineage
        List<AgentSession> recentSessions;
        if (repoId != null) {
            recentSessions = sessionRepository.findByRepositoryId(repoId);
        } else if (projId != null) {
            recentSessions = sessionRepository.findByProjectId(projId);
        } else {
            recentSessions = sessionRepository.findTop10ByOrderByStartedAtDesc();
        }

        if (!recentSessions.isEmpty()) {
            List<AgentSession> sortedSessions = new ArrayList<>(recentSessions);
            sortedSessions.sort(Comparator.comparing((AgentSession s) -> s.getCreatedAt() != null ? s.getCreatedAt() : LocalDateTime.MIN).reversed());
            AgentSession lastSession = sortedSessions.get(0);
            builder.lastActiveAgent(lastSession.getAgent() != null ? lastSession.getAgent().getName() : "Unknown")
                    .lastActiveSessionId(lastSession.getId() != null ? lastSession.getId().toString() : "session-na")
                    .lastActiveTimestamp(lastSession.getCreatedAt() != null ? lastSession.getCreatedAt().format(DATE_FMT) : "Recent");

            if (lastSession.getParentSessionId() != null) {
                builder.inheritedFromSessionId(lastSession.getParentSessionId().toString())
                        .inheritedFromAgent(lastSession.getInheritedFromAgent());
            } else if (sortedSessions.size() > 1) {
                AgentSession prior = sortedSessions.get(1);
                if (prior.getAgent() != null && lastSession.getAgent() != null && !prior.getAgent().getId().equals(lastSession.getAgent().getId())) {
                    builder.inheritedFromSessionId(prior.getId() != null ? prior.getId().toString() : null)
                            .inheritedFromAgent(prior.getAgent().getName());
                }
            }
        }

        // 3. Distinct Task vs Attempt Lifecycle
        List<String> completedTasks = new ArrayList<>();
        List<String> inProgressTasks = new ArrayList<>();
        List<String> currentBlockers = new ArrayList<>();

        if (projId != null) {
            List<Task> tasks = taskRepository.findByProjectId(projId);
            for (Task t : tasks) {
                if (t.getStatus() == TaskStatus.COMPLETED) {
                    completedTasks.add(t.getTitle());
                } else if (t.getStatus() == TaskStatus.IN_PROGRESS) {
                    inProgressTasks.add(t.getTitle());
                } else if (t.getStatus() == TaskStatus.BLOCKED) {
                    currentBlockers.add(t.getTitle() + (t.getDescription() != null ? ": " + t.getDescription() : ""));
                }
            }
        }

        // 4. Distinct Successful vs Failed Attempts
        List<String> successfulAttempts = new ArrayList<>();
        List<String> activeTrials = new ArrayList<>();
        List<CurrentStateResponse.FailureItem> historicalFailures = new ArrayList<>();
        CurrentStateResponse.LastFailureSummary lastFail = null;

        List<AgentAttempt> attempts;
        if (repoId != null) {
            attempts = attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId, PageRequest.of(0, 15));
        } else if (projId != null) {
            attempts = attemptRepository.findByProjectIdOrderByCreatedAtDesc(projId, PageRequest.of(0, 15));
        } else {
            attempts = attemptRepository.findAllOrderByCreatedAtDesc(PageRequest.of(0, 15));
        }

        for (AgentAttempt a : attempts) {
            if ("SUCCESS".equalsIgnoreCase(a.getStatus())) {
                successfulAttempts.add(a.getTaskDescription() != null ? a.getTaskDescription() + " (" + a.getApproach() + ")" : a.getApproach());
            } else if ("FAILURE".equalsIgnoreCase(a.getStatus()) || "FAILED".equalsIgnoreCase(a.getStatus())) {
                if (lastFail == null) {
                    lastFail = CurrentStateResponse.LastFailureSummary.builder()
                            .agentName(a.getAgentName())
                            .approach(a.getApproach())
                            .failureReason(a.getErrorMessage())
                            .lessonLearned(a.getLessonLearned())
                            .build();
                }
                historicalFailures.add(CurrentStateResponse.FailureItem.builder()
                        .agentName(a.getAgentName())
                        .task(a.getTaskDescription())
                        .approach(a.getApproach())
                        .errorMessage(a.getErrorMessage())
                        .lessonLearned(a.getLessonLearned())
                        .build());
            } else if ("IN_PROGRESS".equalsIgnoreCase(a.getStatus())) {
                activeTrials.add(a.getTaskDescription() != null ? a.getTaskDescription() + " (Approach: " + a.getApproach() + ")" : a.getApproach());
            }
        }

        builder.completedTasks(completedTasks.stream().distinct().limit(8).toList())
                .inProgressTasks(inProgressTasks.stream().distinct().limit(8).toList())
                .successfulAttempts(successfulAttempts.stream().distinct().limit(8).toList())
                .activeTrials(activeTrials.stream().distinct().limit(8).toList())
                .currentBlockers(currentBlockers.stream().distinct().limit(8).toList())
                .historicalFailures(historicalFailures.stream().limit(5).toList())
                .lastFailedAttempt(lastFail);

        // 5. Scalable Task-Aware Semantic Memory Retrieval with Hierarchical Fallback
        List<String> establishedKnowledge = retrieveTaskRelevantKnowledgeHierarchical(
                targetTask,
                projId != null ? projId.toString() : null,
                repoId != null ? repoId.toString() : null,
                projId,
                repoId
        );
        builder.relevantEstablishedKnowledge(establishedKnowledge);

        // 6. Synthesize Structured Next Action Recommendations with Safety Classification
        List<CurrentStateResponse.NextActionRecommendation> nextActions = synthesizeNextRecommendations(
                modifiedFiles,
                currentBlockers,
                lastFail,
                inProgressTasks,
                activeTrials
        );
        builder.nextRecommendedActions(nextActions);

        // 7. Build Formatted Markdown Briefing
        String formattedBriefing = buildBriefing(
                repo != null ? repo.getName() : "Global",
                project != null ? project.getName() : "Default",
                targetTask,
                builder.build()
        );
        builder.formattedBriefing(formattedBriefing);

        return builder.build();
    }

    /**
     * Retrieves task-relevant memories via Qdrant vector search strictly filtered by ACTIVE_STATUSES,
     * followed by hierarchical progressive DB scoping (Primary Repo -> Project/Siblings -> Global).
     */
    private List<String> retrieveTaskRelevantKnowledgeHierarchical(String targetTask, String projIdStr, String repoIdStr, UUID projId, UUID repoId) {
        Set<String> resultKnowledge = new LinkedHashSet<>();

        // Stage 1: Vector search in Qdrant strictly filtered by ESTABLISHED & CONFIRMED
        if (targetTask != null && !targetTask.isBlank() && semanticSearchService != null) {
            try {
                List<SearchResult> semanticHits = semanticSearchService.searchScopedWithStatuses(
                        targetTask, "agent_memory", projIdStr, repoIdStr, ACTIVE_STATUS_STRS, 8
                );
                for (SearchResult hit : semanticHits) {
                    if (hit.getContent() != null && !hit.getContent().isBlank()) {
                        resultKnowledge.add(hit.getContent());
                    }
                }
            } catch (Exception e) {
                log.debug("Semantic memory search unavailable for current state: {}", e.getMessage());
            }
        }

        // Stage 2: Hierarchical Database Scoping Fallback
        if (resultKnowledge.size() < 8 && repoId != null) {
            int needed = 8 - resultKnowledge.size();
            List<Memory> repoMemories = memoryRepository.findByRepositoryIdAndStatusInOrderByConfidenceDesc(
                    repoId, ACTIVE_STATUSES, PageRequest.of(0, needed)
            );
            for (Memory m : repoMemories) {
                if (m.getContent() != null && !m.getContent().isBlank()) {
                    resultKnowledge.add(m.getContent());
                }
            }
        }

        // Stage 3: Sibling/Project Scope Fallback
        if (resultKnowledge.size() < 8 && projId != null) {
            int needed = 8 - resultKnowledge.size();
            List<Memory> projMemories = memoryRepository.findByProjectIdAndStatusInOrderByConfidenceDesc(
                    projId, ACTIVE_STATUSES, PageRequest.of(0, needed)
            );
            for (Memory m : projMemories) {
                if (m.getContent() != null && !m.getContent().isBlank()) {
                    resultKnowledge.add(m.getContent());
                }
            }
        }

        // Stage 4: Global High-Confidence Fallback
        if (resultKnowledge.size() < 8) {
            int needed = 8 - resultKnowledge.size();
            List<Memory> globalMemories = memoryRepository.findByStatusInOrderByConfidenceDesc(
                    ACTIVE_STATUSES, PageRequest.of(0, needed)
            );
            for (Memory m : globalMemories) {
                if (m.getContent() != null && !m.getContent().isBlank()) {
                    resultKnowledge.add(m.getContent());
                }
            }
        }

        return new ArrayList<>(resultKnowledge);
    }

    /**
     * Synthesizes actionable, prioritized, and safety-classified next steps for incoming agents.
     */
    private List<CurrentStateResponse.NextActionRecommendation> synthesizeNextRecommendations(
            List<String> modifiedFiles,
            List<String> currentBlockers,
            CurrentStateResponse.LastFailureSummary lastFail,
            List<String> inProgressTasks,
            List<String> activeTrials
    ) {
        List<CurrentStateResponse.NextActionRecommendation> recommendations = new ArrayList<>();

        // Priority 1: Current Blockers
        if (!currentBlockers.isEmpty()) {
            recommendations.add(CurrentStateResponse.NextActionRecommendation.builder()
                    .priority("CRITICAL")
                    .actionClass("ANALYZE")
                    .action("Address active project blocker: " + currentBlockers.get(0))
                    .reason("Task is explicitly flagged as BLOCKED in the project backlog")
                    .confidence(0.98)
                    .requiresHumanApproval(false)
                    .evidence(List.of("BacklogTaskBlocked"))
                    .build());
        }

        // Priority 2: Last Failed Trial Investigation
        if (lastFail != null) {
            List<String> failWarnings = new ArrayList<>();
            failWarnings.add("Avoid repeating approach: " + lastFail.getApproach());
            if (lastFail.getLessonLearned() != null && !lastFail.getLessonLearned().isBlank()) {
                failWarnings.add("Lesson learned: " + lastFail.getLessonLearned());
            }

            recommendations.add(CurrentStateResponse.NextActionRecommendation.builder()
                    .priority("HIGH")
                    .actionClass("MODIFY")
                    .action("Investigate failure in '" + lastFail.getApproach() + "' and implement a validated alternative architecture")
                    .reason("Previous trial by " + lastFail.getAgentName() + " failed with error: " + lastFail.getFailureReason())
                    .confidence(0.94)
                    .requiresHumanApproval(false)
                    .warnings(failWarnings)
                    .evidence(List.of("AgentAttemptFailure:" + lastFail.getAgentName()))
                    .build());
        }

        // Priority 3: In Progress Tasks or Active Trials
        if (!inProgressTasks.isEmpty()) {
            recommendations.add(CurrentStateResponse.NextActionRecommendation.builder()
                    .priority("HIGH")
                    .actionClass("MODIFY")
                    .action("Continue active in-progress task: " + inProgressTasks.get(0))
                    .reason("Work was in progress during last session")
                    .confidence(0.92)
                    .requiresHumanApproval(false)
                    .evidence(List.of("TaskInProgress"))
                    .build());
        } else if (!activeTrials.isEmpty()) {
            recommendations.add(CurrentStateResponse.NextActionRecommendation.builder()
                    .priority("HIGH")
                    .actionClass("MODIFY")
                    .action("Resume active trial: " + activeTrials.get(0))
                    .reason("Trial was ongoing during last agent turn")
                    .confidence(0.90)
                    .requiresHumanApproval(false)
                    .evidence(List.of("ActiveTrial"))
                    .build());
        }

        // Priority 4: Working Tree Inspection
        if (!modifiedFiles.isEmpty()) {
            recommendations.add(CurrentStateResponse.NextActionRecommendation.builder()
                    .priority("MEDIUM")
                    .actionClass("INSPECT")
                    .action("Inspect uncommitted modified files in working tree before editing")
                    .reason("Previous agent left dirty changes (" + String.join(", ", modifiedFiles) + ")")
                    .confidence(0.99)
                    .requiresHumanApproval(false)
                    .warnings(List.of("Verify prior agent work before overwriting"))
                    .evidence(modifiedFiles)
                    .build());
        }

        // Priority 5: Default Ready Action
        if (recommendations.isEmpty()) {
            recommendations.add(CurrentStateResponse.NextActionRecommendation.builder()
                    .priority("LOW")
                    .actionClass("OBSERVE")
                    .action("Call brain_start_session and proceed with task implementation")
                    .reason("Working tree is clean and no active blockers are recorded")
                    .confidence(1.00)
                    .requiresHumanApproval(false)
                    .evidence(List.of("CleanState"))
                    .build());
        }

        return recommendations;
    }

    private String buildBriefing(String repoName, String projectName, String targetTask, CurrentStateResponse state) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                   🧠 SECOND BRAIN: CURRENT WORK STATE BRIEFING             ║\n");
        sb.append("╚════════════════════════════════════════════════════════════════════════════╝\n\n");
        sb.append("**Repository:** `").append(repoName).append("` | **Project:** `").append(projectName).append("`\n");
        if (targetTask != null && !targetTask.isBlank()) {
            sb.append("**Target Task:** *").append(targetTask).append("*\n");
        }
        sb.append("\n---\n\n");

        // Cross-Agent Handoff Detection Banner
        if (state.getInheritedFromAgent() != null || state.getInheritedFromSessionId() != null) {
            sb.append("> [!IMPORTANT]\n");
            sb.append("> **🔄 CROSS-AGENT HANDOFF DETECTED**\n");
            if (state.getInheritedFromAgent() != null) {
                sb.append("> - **Inherited From Agent:** `").append(state.getInheritedFromAgent()).append("`\n");
            }
            if (state.getInheritedFromSessionId() != null) {
                sb.append("> - **Parent Session ID:** `").append(state.getInheritedFromSessionId()).append("`\n");
            }
            if (state.getLastActiveTimestamp() != null) {
                sb.append("> - **Previous Activity Time:** ").append(state.getLastActiveTimestamp()).append("\n");
            }
            sb.append(">\n");
            sb.append("> You are seamlessly continuing prior work from ").append(state.getInheritedFromAgent() != null ? state.getInheritedFromAgent() : "a previous agent").append(". Review uncommitted files and past trial lessons below.\n\n");
        }

        // Working tree & Git State
        sb.append("## 🌿 Working Tree & Git Status\n");
        sb.append("- **Branch:** `").append(state.getGitBranch() != null ? state.getGitBranch() : "main").append("`\n");
        sb.append("- **Working Tree State:** `").append(state.getGitStatus() != null ? state.getGitStatus() : "CLEAN").append("`");
        if (state.getModifiedFilesCount() != null && state.getModifiedFilesCount() > 0) {
            sb.append(" (").append(state.getModifiedFilesCount()).append(" uncommitted modified files)");
        }
        sb.append("\n");

        if (!state.getModifiedFiles().isEmpty()) {
            sb.append("  - **Modified:** `").append(String.join("`, `", state.getModifiedFiles())).append("`\n");
        }
        if (!state.getUntrackedFiles().isEmpty()) {
            sb.append("  - **Untracked:** `").append(String.join("`, `", state.getUntrackedFiles())).append("`\n");
        }
        if (!state.getDeletedFiles().isEmpty()) {
            sb.append("  - **Deleted:** `").append(String.join("`, `", state.getDeletedFiles())).append("`\n");
        }

        if (state.getLastCommitSha() != null) {
            sb.append("- **Last Commit:** `").append(state.getLastCommitSha()).append("` — *").append(state.getLastCommitMessage()).append("*\n");
        }
        if (state.getLastActiveAgent() != null) {
            sb.append("- **Last Active Agent:** `").append(state.getLastActiveAgent()).append("` (Session: `").append(state.getLastActiveSessionId()).append("` at ").append(state.getLastActiveTimestamp()).append(")\n");
        }
        sb.append("\n");

        // Working tree safety warning
        if (state.getModifiedFilesCount() != null && state.getModifiedFilesCount() > 0) {
            sb.append("> [!WARNING]\n");
            sb.append("> **Active Uncommitted Changes:** There are uncommitted changes in the working tree. DO NOT overwrite or discard them without verifying prior agent progress.\n\n");
        }

        // Completed Work (Tasks vs Attempts)
        sb.append("## ✅ Completed Tasks & Successful Trials\n");
        if (state.getCompletedTasks().isEmpty() && state.getSuccessfulAttempts().isEmpty()) {
            sb.append("- *No completed items recorded yet.*\n");
        } else {
            for (String t : state.getCompletedTasks()) {
                sb.append("- ✓ **Task:** ").append(t).append("\n");
            }
            for (String a : state.getSuccessfulAttempts()) {
                sb.append("- ✓ **Trial:** ").append(a).append("\n");
            }
        }
        sb.append("\n");

        // In Progress Work
        sb.append("## 🔄 In Progress Tasks & Active Trials\n");
        if (state.getInProgressTasks().isEmpty() && state.getActiveTrials().isEmpty()) {
            sb.append("- *No active in-progress items.*\n");
        } else {
            for (String t : state.getInProgressTasks()) {
                sb.append("- ⏳ **Task:** ").append(t).append("\n");
            }
            for (String a : state.getActiveTrials()) {
                sb.append("- ⏳ **Trial:** ").append(a).append("\n");
            }
        }
        sb.append("\n");

        // Last Failed Trial
        if (state.getLastFailedAttempt() != null) {
            var fail = state.getLastFailedAttempt();
            sb.append("## 🔴 Last Failed Trial\n");
            sb.append("- **Agent:** `").append(fail.getAgentName()).append("`\n");
            sb.append("- **Approach:** ").append(fail.getApproach() != null ? fail.getApproach() : "Trial").append("\n");
            if (fail.getFailureReason() != null) {
                sb.append("- **Failure Reason:** `").append(fail.getFailureReason()).append("`\n");
            }
            if (fail.getLessonLearned() != null) {
                sb.append("- **💡 Lesson Learned:** ").append(fail.getLessonLearned()).append("\n");
            }
            sb.append("\n");
        }

        // Current Blockers
        if (!state.getCurrentBlockers().isEmpty()) {
            sb.append("## ⚠️ Current Blockers\n");
            for (String blocker : state.getCurrentBlockers()) {
                sb.append("- ⛔ ").append(blocker).append("\n");
            }
            sb.append("\n");
        }

        // Relevant Brain Knowledge
        if (!state.getRelevantEstablishedKnowledge().isEmpty()) {
            sb.append("## 🧠 Relevant Established Knowledge\n");
            for (String k : state.getRelevantEstablishedKnowledge()) {
                sb.append("- • ").append(k).append("\n");
            }
            sb.append("\n");
        }

        // Next Recommended Actions
        if (!state.getNextRecommendedActions().isEmpty()) {
            sb.append("## 🎯 Recommended Next Actions\n");
            for (var rec : state.getNextRecommendedActions()) {
                sb.append("- **[").append(rec.getPriority()).append(" | ").append(rec.getActionClass()).append("]** ").append(rec.getAction())
                        .append("\n  - *Reason:* ").append(rec.getReason()).append("\n");
                if (!rec.getWarnings().isEmpty()) {
                    for (String w : rec.getWarnings()) {
                        sb.append("  - ⚠️ ").append(w).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private RepositoryEntity resolveRepository(String repoIdOrName) {
        if (repoIdOrName == null || repoIdOrName.isBlank()) return null;
        try {
            return repositoryRepository.findById(UUID.fromString(repoIdOrName)).orElse(null);
        } catch (IllegalArgumentException ignored) {}
        return repositoryRepository.findByName(repoIdOrName)
                .or(() -> repositoryRepository.findByPath(repoIdOrName))
                .orElse(null);
    }

    private Project resolveProject(String projectIdOrName, RepositoryEntity repo) {
        if (projectIdOrName != null && !projectIdOrName.isBlank()) {
            try {
                return projectRepository.findById(UUID.fromString(projectIdOrName)).orElse(null);
            } catch (IllegalArgumentException ignored) {}
            return projectRepository.findByName(projectIdOrName).orElse(null);
        }
        return repo != null ? repo.getProject() : null;
    }
}
