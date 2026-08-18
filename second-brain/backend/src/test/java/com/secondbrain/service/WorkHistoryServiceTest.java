package com.secondbrain.service;

import com.secondbrain.common.dto.WorkHistoryResponse;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkHistoryServiceTest {

    @Mock
    private RepositoryEntityRepository repositoryRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AgentAttemptRepository attemptRepository;

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private MemoryRepository memoryRepository;

    private WorkHistoryService workHistoryService;

    @BeforeEach
    void setUp() {
        workHistoryService = new WorkHistoryService(
                repositoryRepository,
                projectRepository,
                attemptRepository,
                decisionRepository,
                memoryRepository
        );
    }

    @Test
    @DisplayName("Work History: Aggregates Claude Code failures and Codex decisions into unified cross-agent narrative")
    void testGetWorkHistoryCrossAgentContinuity() {
        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        Project project = Project.builder().name("AuthPlatform").build();
        project.setId(projId);

        RepositoryEntity repo = RepositoryEntity.builder().name("auth-service").project(project).build();
        repo.setId(repoId);

        when(repositoryRepository.findByName("auth-service")).thenReturn(Optional.of(repo));

        // 1. Claude Code failed attempt
        AgentAttempt claudeAttempt = AgentAttempt.builder()
                .agentName("Claude Code")
                .taskDescription("Implement token revocation in local memory")
                .approach("Guava Cache in JVM")
                .status("FAILURE")
                .errorMessage("Tokens not revoked across cluster nodes")
                .lessonLearned("Local memory does not sync across multiple replicas; must use Redis")
                .filesChanged(List.of("src/main/java/com/auth/TokenStore.java"))
                .repository(repo)
                .project(project)
                .build();
        claudeAttempt.setCreatedAt(LocalDateTime.now().minusDays(1));

        when(attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(eq(repoId), any(Pageable.class)))
                .thenReturn(List.of(claudeAttempt));

        // 2. Codex decision following Claude's lesson
        Agent codexAgent = Agent.builder().name("Codex").build();
        Decision codexDecision = Decision.builder()
                .title("Standardize on Redis for Cluster Token Revocation")
                .rationale("Prevents multi-replica race conditions identified in previous trials")
                .agent(codexAgent)
                .repository(repo)
                .project(project)
                .build();
        codexDecision.setCreatedAt(LocalDateTime.now().minusHours(2));

        when(decisionRepository.findByRepositoryIdOrderByCreatedAtDesc(eq(repoId), any(Pageable.class)))
                .thenReturn(List.of(codexDecision));

        // 3. Consolidated established memory
        Memory memory = Memory.builder()
                .memoryKey("ARCHITECTURAL_STANDARD:REDIS_TOKENS")
                .content("Redis Sliding Window is used for all distributed token blacklisting.")
                .status(MemoryStatus.ESTABLISHED)
                .confidence(0.94)
                .project(project)
                .repository(repo)
                .provenanceSource("MULTI_AGENT_CONSENSUS")
                .build();

        when(memoryRepository.findAll()).thenReturn(List.of(memory));

        // Execute
        WorkHistoryResponse response = workHistoryService.getWorkHistory("auth-service", projId.toString(), "token revocation", 10);

        assertThat(response).isNotNull();
        assertThat(response.getRepository()).isEqualTo("auth-service");
        assertThat(response.getTotalActivities()).isEqualTo(2);

        // Verify narrative structure
        String narrative = response.getFormattedNarrative();
        assertThat(narrative).contains("Agent: Claude Code");
        assertThat(narrative).contains("Failed Trial");
        assertThat(narrative).contains("Tokens not revoked across cluster nodes");
        assertThat(narrative).contains("Local memory does not sync across multiple replicas; must use Redis");
        assertThat(narrative).contains("src/main/java/com/auth/TokenStore.java");

        assertThat(narrative).contains("Agent: Codex");
        assertThat(narrative).contains("Architectural Decision");
        assertThat(narrative).contains("Standardize on Redis for Cluster Token Revocation");
        assertThat(narrative).contains("Redis Sliding Window is used for all distributed token blacklisting.");
    }
}
