package com.secondbrain;

import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.service.AgentBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private ProjectRepository projectRepository;

    @Autowired
    private RepositoryEntityRepository repositoryRepository;

    private Project testProject;
    private RepositoryEntity testRepo;

    @BeforeEach
    void setUp() {
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
                                "id", "fail::in_memory_store",
                                "problemId", "prob::jwt_race",
                                "approach", "In-memory ConcurrentHashMap token blacklist",
                                "errorMessage", "Multi-instance clustering test failed: tokens not synchronized across pods",
                                "lessonLearned", "In-memory token blacklist cannot scale horizontally; requires distributed store"
                        )
                ))
                .decisions(List.of(
                        Map.of(
                                "id", "dec::redis_token_store",
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

        // Step 3: Verify Timeline Endpoint
        mockMvc.perform(get("/api/v1/bridge/timeline")
                        .param("repo", testRepo.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
