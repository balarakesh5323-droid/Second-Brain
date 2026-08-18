package com.secondbrain.service;

import com.secondbrain.common.dto.CurrentStateResponse;
import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.TaskStatus;
import com.secondbrain.common.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentStateServiceTest {

    @Mock
    private RepositoryEntityRepository repositoryRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AgentSessionRepository sessionRepository;

    @Mock
    private AgentAttemptRepository attemptRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private GitService gitService;

    @Mock
    private SemanticSearchService semanticSearchService;

    private CurrentStateService currentStateService;

    @BeforeEach
    void setUp() {
        currentStateService = new CurrentStateService(
                repositoryRepository,
                projectRepository,
                sessionRepository,
                attemptRepository,
                taskRepository,
                memoryRepository,
                gitService,
                semanticSearchService
        );
    }

    @Test
    @DisplayName("Current State Briefing: Synthesizes git status, dirty files, tasks vs attempts, blockers, semantic knowledge, and next actions")
    void testGetCurrentStateBriefing() throws Exception {
        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        Project project = Project.builder().name("CorePlatform").build();
        project.setId(projId);

        RepositoryEntity repo = RepositoryEntity.builder().name("automorium_backend").path("/repos/automorium_backend").project(project).build();
        repo.setId(repoId);

        when(repositoryRepository.findByName("automorium_backend")).thenReturn(Optional.of(repo));
        when(projectRepository.findById(projId)).thenReturn(Optional.of(project));

        // 1. Mock Git Service with Dirty File Lists
        when(gitService.getCurrentBranch("/repos/automorium_backend")).thenReturn("feature/jwt-auth");
        when(gitService.getWorkingTreeStatus("/repos/automorium_backend")).thenReturn(Map.of(
                "state", "DIRTY",
                "modifiedCount", 2,
                "modifiedFiles", List.of("AuthService.java", "RedisTokenStore.java"),
                "untrackedFiles", List.of("AuthIntegrationTest.java"),
                "missingFiles", List.of()
        ));
        when(gitService.getRecentCommits("/repos/automorium_backend", 1)).thenReturn(List.of(
                Map.of("id", "abc1234567890abcdef1234567890abcdef12345", "message", "feat: add token filter")
        ));

        // 2. Mock Last Session
        AgentSession session = AgentSession.builder()
                .agent(Agent.builder().name("Claude Code").build())
                .repository(repo)
                .project(project)
                .build();
        session.setId(UUID.randomUUID());
        session.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(sessionRepository.findByRepositoryId(repoId)).thenReturn(List.of(session));

        // 3. Mock Tasks (Completed, In Progress, Blocked)
        Task doneTask = Task.builder().title("Implement JWT authentication").status(TaskStatus.COMPLETED).project(project).build();
        Task inProgTask = Task.builder().title("Add refresh token rotation").status(TaskStatus.IN_PROGRESS).project(project).build();
        Task blockedTask = Task.builder().title("Setup Cluster Redis").description("Awaiting DevOps IAM role").status(TaskStatus.BLOCKED).project(project).build();
        when(taskRepository.findByProjectId(projId)).thenReturn(List.of(doneTask, inProgTask, blockedTask));

        // 4. Mock Attempts (Success & Failure)
        AgentAttempt successfulAttempt = AgentAttempt.builder()
                .agentName("Claude Code")
                .taskDescription("HMAC SHA-256 Signature Verification")
                .approach("JJWT library")
                .status("SUCCESS")
                .build();

        AgentAttempt failedAttempt = AgentAttempt.builder()
                .agentName("Claude Code")
                .taskDescription("Refresh token rotation")
                .approach("Guava JVM Cache")
                .status("FAILURE")
                .errorMessage("Race condition in concurrent refresh requests")
                .lessonLearned("Must use Redis atomic SETNX with sliding window")
                .build();

        when(attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(eq(repoId), any(Pageable.class)))
                .thenReturn(List.of(successfulAttempt, failedAttempt));

        // 5. Mock Task-Aware Status-Filtered Semantic Memory Retrieval
        when(semanticSearchService.searchScopedWithStatuses(
                eq("authentication"), eq("agent_memory"), any(), any(), eq(List.of("ESTABLISHED", "CONFIRMED")), eq(8)
        )).thenReturn(List.of(
                SearchResult.builder().content("Redis Sliding Window is used for distributed token blacklisting.").score(0.92f).build()
        ));

        // Execute
        CurrentStateResponse state = currentStateService.getCurrentState("automorium_backend", projId.toString(), "authentication");

        assertThat(state).isNotNull();
        assertThat(state.getGitBranch()).isEqualTo("feature/jwt-auth");
        assertThat(state.getGitStatus()).isEqualTo("DIRTY");
        assertThat(state.getModifiedFiles()).contains("AuthService.java", "RedisTokenStore.java");
        assertThat(state.getUntrackedFiles()).contains("AuthIntegrationTest.java");

        // Verify Task vs Attempt Separation
        assertThat(state.getCompletedTasks()).contains("Implement JWT authentication");
        assertThat(state.getSuccessfulAttempts()).anyMatch(s -> s.contains("HMAC SHA-256"));
        assertThat(state.getInProgressTasks()).contains("Add refresh token rotation");
        assertThat(state.getCurrentBlockers()).anyMatch(b -> b.contains("Awaiting DevOps IAM role"));

        // Verify Last Failure
        assertThat(state.getLastFailedAttempt()).isNotNull();
        assertThat(state.getLastFailedAttempt().getFailureReason()).contains("Race condition");
        assertThat(state.getLastFailedAttempt().getLessonLearned()).contains("Redis atomic SETNX");

        // Verify Semantic Established Knowledge
        assertThat(state.getRelevantEstablishedKnowledge()).contains("Redis Sliding Window is used for distributed token blacklisting.");

        // Verify Next Action Recommendations
        assertThat(state.getNextRecommendedActions()).isNotEmpty();
        assertThat(state.getNextRecommendedActions()).anyMatch(r -> r.getPriority().equals("CRITICAL") && r.getAction().contains("Awaiting DevOps IAM role"));
        assertThat(state.getNextRecommendedActions()).anyMatch(r -> r.getPriority().equals("HIGH") && r.getAction().contains("Redis atomic SETNX"));
        assertThat(state.getNextRecommendedActions()).anyMatch(r -> r.getPriority().equals("MEDIUM") && r.getAction().contains("AuthService.java"));

        // Verify Formatted Briefing
        String briefing = state.getFormattedBriefing();
        assertThat(briefing).contains("## 🌿 Working Tree & Git Status");
        assertThat(briefing).contains("AuthService.java");
        assertThat(briefing).contains("## ✅ Completed Tasks & Successful Trials");
        assertThat(briefing).contains("Implement JWT authentication");
        assertThat(briefing).contains("## 🔴 Last Failed Trial");
        assertThat(briefing).contains("Must use Redis atomic SETNX with sliding window");
        assertThat(briefing).contains("## ⚠️ Current Blockers");
        assertThat(briefing).contains("Awaiting DevOps IAM role");
        assertThat(briefing).contains("## 🎯 Recommended Next Actions");
        assertThat(briefing).contains("Redis Sliding Window is used for distributed token blacklisting.");
    }
}
