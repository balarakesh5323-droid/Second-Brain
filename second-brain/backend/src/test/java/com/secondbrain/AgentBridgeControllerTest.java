package com.secondbrain;

import com.secondbrain.common.entity.AgentAttempt;
import com.secondbrain.service.AgentBridgeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentBridgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentBridgeService bridgeService;

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/bridge/activity - ingest autonomous activity")
    void testIngestActivity() throws Exception {
        String json = """
            {
                "agentName": "claude-code",
                "actionType": "TEST_RUN",
                "command": "mvn test",
                "taskDescription": "Fix auth token refresh",
                "approach": "In-memory token cache",
                "errorMessage": "NullPointerException at line 42",
                "notes": "Failed because token cache was not initialized",
                "workingTreeDiff": "diff --git a/Auth.java b/Auth.java"
            }
        """;

        mockMvc.perform(post("/api/v1/bridge/activity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.attemptId").isNotEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/bridge/attempts - record explicit engineering attempt")
    void testRecordAttempt() throws Exception {
        String json = """
            {
                "agentName": "codex",
                "taskDescription": "Implement Redis PubSub message broker",
                "approach": "Redis pub/sub channels",
                "status": "FAILURE",
                "errorMessage": "Message loss under concurrency",
                "lessonLearned": "Use RabbitMQ / Kafka for guaranteed delivery",
                "filesChanged": ["src/Broker.java"]
            }
        """;

        mockMvc.perform(post("/api/v1/bridge/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agentName").value("codex"))
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.lessonLearned").value("Use RabbitMQ / Kafka for guaranteed delivery"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/bridge/attempts - list all attempts")
    void testGetAllAttempts() throws Exception {
        bridgeService.recordAttempt(AgentBridgeService.AgentAttemptDto.builder()
                .agentName("claude-code")
                .taskDescription("DB Migration")
                .approach("Flyway script")
                .status("SUCCESS")
                .lessonLearned("Flyway V2 migration succeeded")
                .build());

        mockMvc.perform(get("/api/v1/bridge/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/bridge/continuity - fetch cross-agent continuity state")
    void testGetContinuityState() throws Exception {
        mockMvc.perform(get("/api/v1/bridge/continuity"))
                .andExpect(status().isOk());
    }
}
