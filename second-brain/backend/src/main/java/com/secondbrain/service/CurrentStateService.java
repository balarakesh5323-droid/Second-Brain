package com.secondbrain.service;

import com.secondbrain.common.dto.CurrentStateResponse;
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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Transactional(readOnly = true)
    public CurrentStateResponse getCurrentState(String repoIdOrName, String projectIdOrName, String targetTask) {
        RepositoryEntity repo = resolveRepository(repoIdOrName);
        Project project = resolveProject(projectIdOrName, repo);

        UUID repoId = repo != null ? repo.getId() : null;
        UUID projId = project != null ? project.getId() : null;

        CurrentStateResponse.CurrentStateResponseBuilder builder = CurrentStateResponse.builder()
                .repository(repo != null ? repo.getName() : (repoIdOrName != null ? repoIdOrName : "Global"))
                .project(project != null ? project.getName() : (projectIdOrName != null ? projectIdOrName : "Default"))
                .task(targetTask != null ? targetTask : "Active development");

        // 1. Git State
        if (repo != null && repo.getPath() != null) {
            try {
                var gitStatus = gitService.getWorkingTreeStatus(repo.getPath());
                builder.gitBranch(gitService.getCurrentBranch(repo.getPath()))
                        .gitStatus((String) gitStatus.get("state"))
                        .modifiedFilesCount((Integer) gitStatus.get("modifiedCount"));

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

        // 2. Last Active Agent & Session
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
        }

        // 3. Completed & In-Progress Items
        List<String> completed = new ArrayList<>();
        List<String> inProgress = new ArrayList<>();
        List<String> knownIssues = new ArrayList<>();

        if (projId != null) {
            List<Task> tasks = taskRepository.findByProjectId(projId);
            for (Task t : tasks) {
                if (t.getStatus() == TaskStatus.COMPLETED) {
                    completed.add(t.getTitle());
                } else if (t.getStatus() == TaskStatus.IN_PROGRESS) {
                    inProgress.add(t.getTitle());
                } else if (t.getStatus() == TaskStatus.BLOCKED) {
                    knownIssues.add("Blocked Task: " + t.getTitle() + (t.getDescription() != null ? " (" + t.getDescription() + ")" : ""));
                }
            }
        }

        // 4. Attempts (Successes & Failures)
        List<AgentAttempt> attempts;
        if (repoId != null) {
            attempts = attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId, PageRequest.of(0, 10));
        } else if (projId != null) {
            attempts = attemptRepository.findByProjectIdOrderByCreatedAtDesc(projId, PageRequest.of(0, 10));
        } else {
            attempts = attemptRepository.findAllOrderByCreatedAtDesc(PageRequest.of(0, 10));
        }

        CurrentStateResponse.LastFailureSummary lastFail = null;
        for (AgentAttempt a : attempts) {
            if ("SUCCESS".equalsIgnoreCase(a.getStatus())) {
                completed.add(a.getTaskDescription() != null ? a.getTaskDescription() : a.getApproach());
            } else if ("FAILURE".equalsIgnoreCase(a.getStatus()) || "FAILED".equalsIgnoreCase(a.getStatus())) {
                if (lastFail == null) {
                    lastFail = CurrentStateResponse.LastFailureSummary.builder()
                            .agentName(a.getAgentName())
                            .approach(a.getApproach())
                            .failureReason(a.getErrorMessage())
                            .lessonLearned(a.getLessonLearned())
                            .build();
                }
                if (a.getErrorMessage() != null) {
                    knownIssues.add(a.getTaskDescription() + ": " + a.getErrorMessage());
                }
            } else if ("IN_PROGRESS".equalsIgnoreCase(a.getStatus())) {
                inProgress.add(a.getTaskDescription() != null ? a.getTaskDescription() : a.getApproach());
            }
        }

        builder.completedItems(completed.stream().distinct().limit(10).toList())
                .inProgressItems(inProgress.stream().distinct().limit(10).toList())
                .knownIssues(knownIssues.stream().distinct().limit(10).toList())
                .lastFailedAttempt(lastFail);

        // 5. Relevant Established Brain Knowledge
        List<String> establishedKnowledge = new ArrayList<>();
        List<Memory> memories = memoryRepository.findAll().stream()
                .filter(m -> m.getStatus() == MemoryStatus.ESTABLISHED || m.getStatus() == MemoryStatus.CONFIRMED)
                .filter(m -> {
                    if (projId != null && m.getProject() != null) {
                        return m.getProject().getId().equals(projId);
                    }
                    return true;
                })
                .limit(8)
                .toList();

        for (Memory m : memories) {
            establishedKnowledge.add(m.getContent());
        }
        builder.relevantEstablishedKnowledge(establishedKnowledge);

        // 6. Build Formatted Markdown Briefing
        String formattedBriefing = buildBriefing(
                repo != null ? repo.getName() : "Global",
                project != null ? project.getName() : "Default",
                targetTask,
                builder.build()
        );
        builder.formattedBriefing(formattedBriefing);

        return builder.build();
    }

    private String buildBriefing(String repoName, String projectName, String targetTask, CurrentStateResponse state) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 📍 Second Brain: Current Work State Briefing\n\n");
        sb.append("**Repository:** `").append(repoName).append("` | **Project:** `").append(projectName).append("`\n");
        if (targetTask != null && !targetTask.isBlank()) {
            sb.append("**Target Task:** *").append(targetTask).append("*\n");
        }
        sb.append("\n---\n\n");

        sb.append("## 🌿 Working Tree & Git Status\n");
        sb.append("- **Branch:** `").append(state.getGitBranch() != null ? state.getGitBranch() : "main").append("`\n");
        sb.append("- **Status:** `").append(state.getGitStatus() != null ? state.getGitStatus() : "CLEAN").append("`");
        if (state.getModifiedFilesCount() != null) {
            sb.append(" (").append(state.getModifiedFilesCount()).append(" modified files)");
        }
        sb.append("\n");
        if (state.getLastCommitSha() != null) {
            sb.append("- **Last Commit:** `").append(state.getLastCommitSha()).append("` — *").append(state.getLastCommitMessage()).append("*\n");
        }
        if (state.getLastActiveAgent() != null) {
            sb.append("- **Last Active Agent:** `").append(state.getLastActiveAgent()).append("` (Session: `").append(state.getLastActiveSessionId()).append("` at ").append(state.getLastActiveTimestamp()).append(")\n");
        }
        sb.append("\n");

        sb.append("## ✅ Completed Work\n");
        if (state.getCompletedItems().isEmpty()) {
            sb.append("- *No completed items recorded yet.*\n");
        } else {
            for (String c : state.getCompletedItems()) {
                sb.append("- ").append(c).append("\n");
            }
        }
        sb.append("\n");

        sb.append("## 🔄 In Progress\n");
        if (state.getInProgressItems().isEmpty()) {
            sb.append("- *No active in-progress items.*\n");
        } else {
            for (String ip : state.getInProgressItems()) {
                sb.append("- ").append(ip).append("\n");
            }
        }
        sb.append("\n");

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

        if (!state.getKnownIssues().isEmpty()) {
            sb.append("## ⚠️ Known Issues & Blockers\n");
            for (String issue : state.getKnownIssues()) {
                sb.append("- ").append(issue).append("\n");
            }
            sb.append("\n");
        }

        if (!state.getRelevantEstablishedKnowledge().isEmpty()) {
            sb.append("## 🧠 Relevant Established Knowledge\n");
            for (String k : state.getRelevantEstablishedKnowledge()) {
                sb.append("- ").append(k).append("\n");
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
