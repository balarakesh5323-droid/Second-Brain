package com.secondbrain;

import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.enums.AgentSessionStatus;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.service.AgentBridgeService;
import com.secondbrain.service.EmbeddingService;
import com.secondbrain.service.GraphService;
import com.secondbrain.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AgentActivityMemoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentBridgeService bridgeService;

    @Autowired
    private GraphService graphService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RepositoryEntityRepository repositoryRepository;

    private Project testProject;
    private RepositoryEntity testRepoA;
    private RepositoryEntity testRepoB;

    @BeforeEach
    void setUp() {
        Mockito.reset(graphService, vectorStoreService, embeddingService);

        // Default embedding stub
        Mockito.when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        testProject = projectRepository.saveAndFlush(Project.builder()
                .name("Automorium")
                .path("/repos/automorium")
                .status("active")
                .build());

        testRepoA = repositoryRepository.saveAndFlush(RepositoryEntity.builder()
                .name("automorium-backend")
                .path("/repos/automorium/automorium-backend")
                .project(testProject)
                .primaryLanguage("Java")
                .build());

        testRepoB = repositoryRepository.saveAndFlush(RepositoryEntity.builder()
                .name("automorium-frontend")
                .path("/repos/automorium/automorium-frontend")
                .project(testProject)
                .primaryLanguage("TypeScript")
                .build());
    }

    @Test
    @DisplayName("1. Claude -> Codex Continuity: Full session recording with decisions, problems, failures, commits, and handoffs")
    void testClaudeToCodexAgentMemoryLifecycle() throws Exception {
        AgentBridgeService.FullSessionPayload sessionPayload = AgentBridgeService.FullSessionPayload.builder()
                .agentName("Claude Code")
                .agentType("CLAUDE_CODE")
                .repositoryIdOrPath(testRepoA.getId().toString())
                .projectId(testProject.getId().toString())
                .branch("feature/auth")
                .headCommit("abc1234")
                .taskSummary("Implement robust JWT authentication and Redis refresh token rotation")
                .status("COMPLETED")
                .touchedFiles(List.of(
                        "src/main/java/AuthService.java",
                        "src/main/java/JwtFilter.java",
                        "src/main/java/RedisConfig.java"
                ))
                .problems(List.of(
                        Map.of(
                                "id", "prob::jwt_race",
                                "title", "JWT refresh token race condition",
                                "description", "Concurrent requests cause token invalidation conflicts"
                        )
                ))
                .failedAttempts(List.of(
                        Map.of(
                                "problemId", "prob::jwt_race",
                                "approach", "In-memory ConcurrentHashMap token blacklist",
                                "errorMessage", "Multi-instance clustering test failed: tokens not synchronized across pods",
                                "lessonLearned", "In-memory token blacklist cannot scale horizontally; requires distributed store"
                        )
                ))
                .decisions(List.of(
                        Map.of(
                                "solvedProblemId", "prob::jwt_race",
                                "title", "Redis-backed refresh token rotation",
                                "rationale", "Redis provides sub-millisecond atomic TTL and multi-pod cache invalidation"
                        )
                ))
                .commits(List.of(
                        Map.of(
                                "id", "abc1234",
                                "hash", "abc1234",
                                "message", "feat: add redis refresh token rotation",
                                "branch", "feature/auth"
                        )
                ))
                .handoff(Map.of(
                        "targetAgent", "Codex",
                        "task", "JWT Redis Token Rotation",
                        "completedItems", "RedisConfig, JwtFilter, AuthService token rotation logic",
                        "inProgressItems", "AuthService unit and integration test suite",
                        "blockedItems", "none",
                        "nextSteps", "Add integration tests verifying expired refresh token rejection in multi-instance setup"
                ))
                .build();

        Map<String, Object> recordResult = bridgeService.recordFullSession(sessionPayload);
        assertThat(recordResult.get("status")).isEqualTo("success");
        assertThat(recordResult.get("sessionId")).isNotNull();

        // Verify GraphService received canonical IDs and exact touched file structure
        Mockito.verify(graphService).recordAgentSessionGraph(
                eq("Claude Code"),
                eq("CLAUDE_CODE"),
                eq(recordResult.get("sessionId").toString()),
                any(),
                eq(testRepoA.getId().toString()),
                eq(List.of("src/main/java/AuthService.java", "src/main/java/JwtFilter.java", "src/main/java/RedisConfig.java")),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                any()
        );

        // Verify Codex receives full briefing from 1-shot workspace-state
        mockMvc.perform(get("/api/v1/bridge/workspace-state")
                        .param("repo", testRepoA.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository.name").value("automorium-backend"))
                .andExpect(jsonPath("$.latestHandoff.task").value("JWT Redis Token Rotation"))
                .andExpect(jsonPath("$.latestHandoff.nextSteps").value("Add integration tests verifying expired refresh token rejection in multi-instance setup"))
                .andExpect(jsonPath("$.recentAttempts[0].agent").value("Claude Code"))
                .andExpect(jsonPath("$.recentAttempts[0].error").value("Multi-instance clustering test failed: tokens not synchronized across pods"))
                .andExpect(jsonPath("$.recentAttempts[0].lessonLearned").value("In-memory token blacklist cannot scale horizontally; requires distributed store"))
                .andExpect(jsonPath("$.activeDecisions[0].title").value("Redis-backed refresh token rotation"));
    }

    @Test
    @DisplayName("2. Error Propagation: Neo4j failure throws IllegalStateException")
    void testNeo4jFailurePropagates() {
        Mockito.doThrow(new IllegalStateException("Neo4j node creation failed"))
                .when(graphService).recordAgentSessionGraph(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any());

        AgentBridgeService.FullSessionPayload payload = AgentBridgeService.FullSessionPayload.builder()
                .agentName("Claude Code")
                .taskSummary("Test Session")
                .repositoryIdOrPath(testRepoA.getId().toString())
                .build();

        assertThatThrownBy(() -> bridgeService.recordFullSession(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Neo4j node creation failed");
    }

    @Test
    @DisplayName("3. Error Propagation: Qdrant failure throws IllegalStateException")
    void testQdrantFailurePropagates() {
        Mockito.doThrow(new RuntimeException("Qdrant connection refused"))
                .when(vectorStoreService).upsert(eq("decision_knowledge"), anyString(), any(), any(), any());

        AgentBridgeService.FullSessionPayload payload = AgentBridgeService.FullSessionPayload.builder()
                .agentName("Claude Code")
                .taskSummary("Test Decision Session")
                .repositoryIdOrPath(testRepoA.getId().toString())
                .decisions(List.of(Map.of("title", "Use PostgreSQL", "rationale", "ACID support")))
                .build();

        assertThatThrownBy(() -> bridgeService.recordFullSession(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to vectorize decision");
    }

    @Test
    @DisplayName("4. Global Timeline: Queries across all repositories without repo parameter")
    void testGlobalTimelineQuery() throws Exception {
        Mockito.when(graphService.getAgentTimeline(null, 20))
                .thenReturn(List.of(
                        Map.of("agentName", "Claude Code", "summary", "Repo A task"),
                        Map.of("agentName", "Codex", "summary", "Repo B task")
                ));

        mockMvc.perform(get("/api/v1/bridge/timeline")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].summary").value("Repo A task"))
                .andExpect(jsonPath("$[1].summary").value("Repo B task"));

        Mockito.verify(graphService).getAgentTimeline(null, 20);
    }

    @Test
    @DisplayName("5. Repository Timeline: Scoped retrieval for specific repository")
    void testRepositoryTimelineFiltering() throws Exception {
        Mockito.when(graphService.getAgentTimeline(eq(testRepoA.getId().toString()), eq(10)))
                .thenReturn(List.of(
                        Map.of("agentName", "Claude Code", "summary", "Repo A backend work")
                ));

        mockMvc.perform(get("/api/v1/bridge/timeline")
                        .param("repo", testRepoA.getId().toString())
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].summary").value("Repo A backend work"));

        Mockito.verify(graphService).getAgentTimeline(eq(testRepoA.getId().toString()), eq(10));
    }

    @Test
    @DisplayName("6. Active Session Lifecycle: IN_PROGRESS session has endedAt == null")
    void testActiveSessionLifecycle() {
        AgentBridgeService.StartSessionPayload startPayload = AgentBridgeService.StartSessionPayload.builder()
                .agentName("Cursor")
                .agentType("CURSOR")
                .repositoryIdOrPath(testRepoA.getId().toString())
                .branch("feature/dark-mode")
                .task("Implement dark mode toggle UI")
                .build();

        Map<String, Object> startRes = bridgeService.startSession(startPayload);
        assertThat(startRes.get("status")).isEqualTo("success");
        assertThat(startRes.get("sessionId")).isNotNull();
        assertThat(startRes.get("sessionStatus")).isEqualTo(AgentSessionStatus.IN_PROGRESS.name());

        UUID sessionId = (UUID) startRes.get("sessionId");

        // Verify GraphService recorded active session without endedAt
        Mockito.verify(graphService).recordAgentSessionGraph(
                eq("Cursor"),
                eq("CURSOR"),
                eq(sessionId.toString()),
                any(),
                eq(testRepoA.getId().toString()),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                any()
        );
    }

    @Test
    @DisplayName("7. Invalid Lifecycle Rejection: endedAt before startedAt throws IllegalArgumentException")
    void testInvalidTimestampRejection() {
        LocalDateTime now = LocalDateTime.now();
        AgentBridgeService.FullSessionPayload payload = AgentBridgeService.FullSessionPayload.builder()
                .agentName("Claude Code")
                .taskSummary("Time traveler session")
                .repositoryIdOrPath(testRepoA.getId().toString())
                .status("COMPLETED")
                .startedAt(now)
                .endedAt(now.minusHours(2)) // Invalid: 2 hours in the past
                .build();

        assertThatThrownBy(() -> bridgeService.recordFullSession(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endedAt cannot be before startedAt");
    }

    @Test
    @DisplayName("8. Incremental Session Progression: Start -> Append Events -> Complete with Handoff")
    void testIncrementalSessionProgression() {
        // Step A: Start Session
        AgentBridgeService.StartSessionPayload startPayload = AgentBridgeService.StartSessionPayload.builder()
                .agentName("Claude Code")
                .agentType("CLAUDE_CODE")
                .repositoryIdOrPath(testRepoA.getId().toString())
                .branch("feature/search")
                .task("Build semantic code search")
                .build();

        Map<String, Object> startResult = bridgeService.startSession(startPayload);
        UUID sessionId = (UUID) startResult.get("sessionId");
        assertThat(sessionId).isNotNull();

        // Step B: Append Decision Event
        AgentBridgeService.SessionEventPayload decisionEvent = AgentBridgeService.SessionEventPayload.builder()
                .eventType("DECISION")
                .decision(Map.of("title", "Use Qdrant HNSW cosine metric", "rationale", "Sub-10ms similarity search"))
                .build();
        Map<String, Object> decRes = bridgeService.appendSessionEvent(sessionId, decisionEvent);
        assertThat(decRes.get("status")).isEqualTo("success");

        // Step C: Append Failed Attempt Event
        AgentBridgeService.SessionEventPayload attemptEvent = AgentBridgeService.SessionEventPayload.builder()
                .eventType("FAILED_ATTEMPT")
                .failedAttempt(Map.of(
                        "approach", "TF-IDF in-memory search",
                        "errorMessage", "Out of memory on 50k symbols",
                        "lessonLearned", "Dense vector database required for scalability"
                ))
                .build();
        Map<String, Object> attRes = bridgeService.appendSessionEvent(sessionId, attemptEvent);
        assertThat(attRes.get("status")).isEqualTo("success");

        // Step D: Complete Session with Handoff
        AgentBridgeService.CompleteSessionPayload completePayload = AgentBridgeService.CompleteSessionPayload.builder()
                .status("COMPLETED")
                .summary("Successfully indexed all symbols and connected Qdrant")
                .handoff(Map.of(
                        "targetAgent", "Codex",
                        "task", "Semantic Search Benchmark",
                        "nextSteps", "Run accuracy evaluation suite"
                ))
                .build();
        Map<String, Object> compRes = bridgeService.completeSession(sessionId, completePayload);
        assertThat(compRes.get("status")).isEqualTo("success");
        assertThat(compRes.get("sessionStatus")).isEqualTo("COMPLETED");
        assertThat(compRes.get("endedAt")).isNotNull();

        // Step E: Verify chronological immutable Event Log
        var events = bridgeService.getSessionEvents(sessionId);
        assertThat(events).hasSize(5);
        assertThat(events.get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(events.get(0).getEventType()).isEqualTo(com.secondbrain.common.enums.EventType.SESSION_STARTED);

        assertThat(events.get(1).getSequenceNumber()).isEqualTo(2);
        assertThat(events.get(1).getEventType()).isEqualTo(com.secondbrain.common.enums.EventType.DECISION_MADE);

        assertThat(events.get(2).getSequenceNumber()).isEqualTo(3);
        assertThat(events.get(2).getEventType()).isEqualTo(com.secondbrain.common.enums.EventType.FAILED_ATTEMPT);

        assertThat(events.get(3).getSequenceNumber()).isEqualTo(4);
        assertThat(events.get(3).getEventType()).isEqualTo(com.secondbrain.common.enums.EventType.HANDOFF_CREATED);

        assertThat(events.get(4).getSequenceNumber()).isEqualTo(5);
        assertThat(events.get(4).getEventType()).isEqualTo(com.secondbrain.common.enums.EventType.SESSION_ENDED);

        // Step F: Reject appending new events after session completion
        AgentBridgeService.SessionEventPayload lateEvent = AgentBridgeService.SessionEventPayload.builder()
                .eventType("DECISION")
                .decision(Map.of("title", "Late Decision", "rationale", "Too late"))
                .build();
        assertThatThrownBy(() -> bridgeService.appendSessionEvent(sessionId, lateEvent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot append event to session");

        // Step G: Verify Idempotent completeSession calls
        Map<String, Object> idempotentRes = bridgeService.completeSession(sessionId, completePayload);
        assertThat(idempotentRes.get("status")).isEqualTo("success");
        assertThat(idempotentRes.get("idempotent")).isEqualTo(true);
        assertThat(idempotentRes.get("sessionStatus")).isEqualTo("COMPLETED");

        // Step H: Verify payload mismatch rejection
        AgentBridgeService.SessionEventPayload mismatchedPayload = AgentBridgeService.SessionEventPayload.builder()
                .eventType("DECISION")
                .filePath("src/main/java/SomeService.java") // Unrelated field for a DECISION
                .build();
        assertThatThrownBy(() -> bridgeService.appendSessionEvent(UUID.randomUUID(), mismatchedPayload))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
