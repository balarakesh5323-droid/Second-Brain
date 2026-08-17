package com.secondbrain;

import com.secondbrain.common.entity.*;
import com.secondbrain.common.repository.*;
import com.secondbrain.service.AgentBridgeService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaudeToCodexContinuityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RepositoryEntityRepository repositoryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AgentBridgeService bridgeService;

    @Test
    @DisplayName("Killer Flow: Claude Code works -> captures failures & diffs -> Codex seamlessly resumes")
    void testClaudeToCodexAutonomousContinuity() throws Exception {
        // Step 0: Setup project and repo
        Project project = projectRepository.saveAndFlush(Project.builder()
                .name("CoreBanking")
                .path("/repos/core-banking")
                .build());

        RepositoryEntity repo = repositoryRepository.saveAndFlush(RepositoryEntity.builder()
                .name("auth-service")
                .path("/repos/core-banking/auth-service")
                .primaryLanguage("Java")
                .project(project)
                .build());

        // ==========================================
        // DAY 1: Claude Code Autonomous Workflow
        // ==========================================

        // 1. Claude executes Approach #1 (Fails)
        String claudeAttempt1 = """
            {
                "agentName": "claude-code",
                "actionType": "TEST_RUN",
                "repositoryId": "%s",
                "command": "mvn test",
                "taskDescription": "Implement OAuth2 refresh token cache",
                "approach": "In-memory ConcurrentHashMap token storage",
                "errorMessage": "NullPointerException: Token expired during cluster sync",
                "notes": "In-memory token cache loses tokens under multi-threaded concurrency",
                "filesChanged": ["src/main/java/com/bank/auth/TokenCache.java"]
            }
        """.formatted(repo.getId());

        mockMvc.perform(post("/api/v1/bridge/activity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claudeAttempt1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 2. Claude pivots to Approach #2 (Succeeds)
        String claudeAttempt2 = """
            {
                "agentName": "claude-code",
                "actionType": "TEST_RUN",
                "repositoryId": "%s",
                "command": "mvn test",
                "taskDescription": "Implement OAuth2 refresh token entity in PostgreSQL",
                "approach": "PostgreSQL RefreshToken table with JPA optimistic locking",
                "notes": "PostgreSQL schema created and repository unit tests green",
                "filesChanged": [
                    "src/main/java/com/bank/auth/RefreshToken.java",
                    "src/main/java/com/bank/auth/RefreshTokenRepository.java",
                    "src/main/java/com/bank/auth/RefreshTokenService.java"
                ],
                "workingTreeDiff": "diff --git a/RefreshToken.java b/RefreshToken.java"
            }
        """.formatted(repo.getId());

        mockMvc.perform(post("/api/v1/bridge/activity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claudeAttempt2))
                .andExpect(status().isOk());

        // 3. Claude leaves a remaining open task
        taskRepository.saveAndFlush(Task.builder()
                .title("Implement RefreshTokenController and OAuth2 endpoint")
                .description("Expose /oauth/token/refresh REST endpoint")
                .status(com.secondbrain.common.enums.TaskStatus.OPEN)
                .priority(1)
                .project(project)
                .repository(repo)
                .build());

        // ==========================================
        // DAY 2: Codex Connects to Second Brain
        // ==========================================

        // Codex calls 1-shot continuity state
        var mvcResult = mockMvc.perform(get("/api/v1/bridge/continuity")
                        .param("repo", repo.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository.name").value("auth-service"))
                .andExpect(jsonPath("$.recentAttempts", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.openTasks", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.structuredBriefing").isNotEmpty())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        System.out.println("\n=== CODEX SEES THIS CONTINUITY STATE FROM SECOND BRAIN ===");
        System.out.println(responseBody);

        // Assertions: Codex knows about the failure and the open task
        assertTrue(responseBody.contains("In-memory ConcurrentHashMap token storage"), "Codex should know the failed approach");
        assertTrue(responseBody.contains("PostgreSQL RefreshToken table with JPA optimistic locking"), "Codex should know the successful approach");
        assertTrue(responseBody.contains("Implement RefreshTokenController"), "Codex should know the next pending task");
    }
}
