package com.secondbrain;

import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.service.AgentBridgeService;
import com.secondbrain.service.GraphService;
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

import java.util.List;
import java.util.Map;

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
    private ProjectRepository projectRepository;

    @Autowired
    private RepositoryEntityRepository repositoryRepository;

    private Project testProject;
    private RepositoryEntity testRepo;

    @BeforeEach
    void setUp() {
        Mockito.reset(graphService);

        testProject = projectRepository.saveAndFlush(Project.builder()
                .name("Automorium")
                .path("/repos/automorium")
                .status("active")
                .build());

        testRepo = repositoryRepository.saveAndFlush(RepositoryEntity.builder()
                .name("automorium-backend")
                .path("/repos/automorium/automorium-backend")
                .project(testProject)
                .primaryLanguage("Java")
                .build());
    }

    @Test
    @DisplayName("1. Agent Activity Memory: Claude records session with problems, decisions, failed attempts, and handoff -> Codex seamlessly resumes")
    void testClaudeToCodexAgentMemoryLifecycle() throws Exception {
        // Step 1: Claude Code completes session and records rich activity memory
        AgentBridgeService.FullSessionPayload sessionPayload = AgentBridgeService.FullSessionPayload.builder()
                .agentName("Claude Code")
                .agentType("CLAUDE_CODE")
                .repositoryIdOrPath(testRepo.getId().toString())
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

        // Verify GraphService received the exact graph structure
        Mockito.verify(graphService).recordAgentSessionGraph(
                eq("Claude Code"),
                eq("CLAUDE_CODE"),
                eq(recordResult.get("sessionId").toString()),
                any(),
                eq(testRepo.getId().toString()),
                eq(List.of("src/main/java/AuthService.java", "src/main/java/JwtFilter.java", "src/main/java/RedisConfig.java")),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                any()
        );

        // Step 2: Codex starts up and queries the 1-shot workspace state
        mockMvc.perform(get("/api/v1/bridge/workspace-state")
                        .param("repo", testRepo.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository.name").value("automorium-backend"))
                .andExpect(jsonPath("$.latestHandoff.task").value("JWT Redis Token Rotation"))
                .andExpect(jsonPath("$.latestHandoff.nextSteps").value("Add integration tests verifying expired refresh token rejection in multi-instance setup"))
                .andExpect(jsonPath("$.recentAttempts[0].agent").value("Claude Code"))
                .andExpect(jsonPath("$.recentAttempts[0].error").value("Multi-instance clustering test failed: tokens not synchronized across pods"))
                .andExpect(jsonPath("$.recentAttempts[0].lessonLearned").value("In-memory token blacklist cannot scale horizontally; requires distributed store"))
                .andExpect(jsonPath("$.activeDecisions[0].title").value("Redis-backed refresh token rotation"));

        // Step 3: Mock and verify Timeline queries (both global and repo-filtered)
        Mockito.when(graphService.getAgentTimeline(eq(testRepo.getId().toString()), anyInt()))
                .thenReturn(List.of(
                        Map.of(
                                "agentName", "Claude Code",
                                "summary", "Implement robust JWT authentication and Redis refresh token rotation",
                                "decisions", List.of("Redis-backed refresh token rotation"),
                                "problems", List.of("JWT refresh token race condition"),
                                "failedAttempts", List.of("In-memory ConcurrentHashMap token blacklist"),
                                "commits", List.of("abc1234"),
                                "nextSteps", "Add integration tests verifying expired refresh token rejection in multi-instance setup"
                        )
                ));

        mockMvc.perform(get("/api/v1/bridge/timeline")
                        .param("repo", testRepo.getId().toString())
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agentName").value("Claude Code"))
                .andExpect(jsonPath("$[0].summary").value("Implement robust JWT authentication and Redis refresh token rotation"))
                .andExpect(jsonPath("$[0].decisions[0]").value("Redis-backed refresh token rotation"))
                .andExpect(jsonPath("$[0].problems[0]").value("JWT refresh token race condition"))
                .andExpect(jsonPath("$[0].failedAttempts[0]").value("In-memory ConcurrentHashMap token blacklist"))
                .andExpect(jsonPath("$[0].commits[0]").value("abc1234"))
                .andExpect(jsonPath("$[0].nextSteps").value("Add integration tests verifying expired refresh token rejection in multi-instance setup"));

        // Step 4: Verify Global Timeline without repo param delegates with null repoId
        mockMvc.perform(get("/api/v1/bridge/timeline")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        Mockito.verify(graphService).getAgentTimeline(null, 20);
    }

    @Test
    @DisplayName("2. Error Propagation: GraphService exception prevents false success")
    void testGraphServiceFailurePropagates() {
        Mockito.doThrow(new IllegalStateException("Neo4j node creation failed"))
                .when(graphService).recordAgentSessionGraph(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any());

        AgentBridgeService.FullSessionPayload payload = AgentBridgeService.FullSessionPayload.builder()
                .agentName("Claude Code")
                .taskSummary("Test Session")
                .repositoryIdOrPath(testRepo.getId().toString())
                .build();

        assertThatThrownBy(() -> bridgeService.recordFullSession(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Neo4j node creation failed");
    }
}
