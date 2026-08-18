package com.secondbrain.service;

import com.secondbrain.common.dto.CurrentStateResponse;
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
                gitService
        );
    }

    @Test
    @DisplayName("Current State Briefing: Synthesizes git status, completed items, last failed trial, and brain knowledge")
    void testGetCurrentStateBriefing() throws Exception {
        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        Project project = Project.builder().name("CorePlatform").build();
        project.setId(projId);

        RepositoryEntity repo = RepositoryEntity.builder().name("automorium_backend").path("/repos/automorium_backend").project(project).build();
        repo.setId(repoId);

        when(repositoryRepository.findByName("automorium_backend")).thenReturn(Optional.of(repo));
        when(projectRepository.findById(projId)).thenReturn(Optional.of(project));

        // Mock Git Service
        when(gitService.getCurrentBranch("/repos/automorium_backend")).thenReturn("feature/jwt-auth");
        when(gitService.getWorkingTreeStatus("/repos/automorium_backend")).thenReturn(Map.of("state", "DIRTY", "modifiedCount", 2));
        when(gitService.getRecentCommits("/repos/automorium_backend", 1)).thenReturn(List.of(
                Map.of("id", "abc1234567890abcdef1234567890abcdef12345", "message", "feat: add token filter")
        ));

        // Mock Last Session
        AgentSession session = AgentSession.builder()
                .agent(Agent.builder().name("Claude Code").build())
                .repository(repo)
                .project(project)
                .build();
        session.setId(UUID.randomUUID());
        session.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(sessionRepository.findByRepositoryId(repoId)).thenReturn(List.of(session));

        // Mock Tasks
        Task doneTask = Task.builder().title("Implement JWT authentication").status(TaskStatus.COMPLETED).project(project).build();
        Task inProgTask = Task.builder().title("Add refresh token rotation").status(TaskStatus.IN_PROGRESS).project(project).build();
        when(taskRepository.findByProjectId(projId)).thenReturn(List.of(doneTask, inProgTask));

        // Mock Last Failed Attempt
        AgentAttempt failedAttempt = AgentAttempt.builder()
                .agentName("Claude Code")
                .taskDescription("Refresh token rotation")
                .approach("Guava JVM Cache")
                .status("FAILURE")
                .errorMessage("Race condition in concurrent refresh requests")
                .lessonLearned("Must use Redis atomic SETNX with sliding window")
                .build();
        when(attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(eq(repoId), any(Pageable.class)))
                .thenReturn(List.of(failedAttempt));

        // Mock Active Knowledge
        Memory mem = Memory.builder()
                .content("Redis is standard for distributed token state.")
                .status(MemoryStatus.ESTABLISHED)
                .project(project)
                .build();
        when(memoryRepository.findAll()).thenReturn(List.of(mem));

        // Execute
        CurrentStateResponse state = currentStateService.getCurrentState("automorium_backend", projId.toString(), "authentication");

        assertThat(state).isNotNull();
        assertThat(state.getGitBranch()).isEqualTo("feature/jwt-auth");
        assertThat(state.getGitStatus()).isEqualTo("DIRTY");
        assertThat(state.getLastActiveAgent()).isEqualTo("Claude Code");
        assertThat(state.getCompletedItems()).contains("Implement JWT authentication");
        assertThat(state.getInProgressItems()).contains("Add refresh token rotation");

        // Verify Last Failure
        assertThat(state.getLastFailedAttempt()).isNotNull();
        assertThat(state.getLastFailedAttempt().getFailureReason()).contains("Race condition");
        assertThat(state.getLastFailedAttempt().getLessonLearned()).contains("Redis atomic SETNX");

        // Verify Formatted Briefing
        String briefing = state.getFormattedBriefing();
        assertThat(briefing).contains("## 🌿 Working Tree & Git Status");
        assertThat(briefing).contains("feature/jwt-auth");
        assertThat(briefing).contains("## ✅ Completed Work");
        assertThat(briefing).contains("Implement JWT authentication");
        assertThat(briefing).contains("## 🔴 Last Failed Trial");
        assertThat(briefing).contains("Must use Redis atomic SETNX with sliding window");
        assertThat(briefing).contains("Redis is standard for distributed token state.");
    }
}
