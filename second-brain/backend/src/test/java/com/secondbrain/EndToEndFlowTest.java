package com.secondbrain;

import com.secondbrain.test.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String projectId;
    private static String repositoryId;
    private static String agentId;
    private static String sessionId;
    private static String memoryId;
    private static String decisionId;
    private static String taskId;

    @Test
    @Order(1)
    @DisplayName("Full E2E: Create project, repo, agent, session, memory, decision, task")
    void fullWorkflow() throws Exception {
        // Step 1: Create a project
        String projectJson = objectMapper.writeValueAsString(
                TestDataFactory.createProject("Automorium"));
        var projectResult = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Automorium"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString())
                .get("id").asText();

        // Step 2: Create a repository linked to the project
        String repoJson = objectMapper.writeValueAsString(
                TestDataFactory.createRepository("automorium_backend", null));
        var repoResult = mockMvc.perform(post("/api/v1/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(repoJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("automorium_backend"))
                .andReturn();
        repositoryId = objectMapper.readTree(repoResult.getResponse().getContentAsString())
                .get("id").asText();

        // Step 3: Create an agent
        String agentJson = objectMapper.writeValueAsString(
                TestDataFactory.createAgent("claude-code"));
        var agentResult = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("claude-code"))
                .andReturn();
        agentId = objectMapper.readTree(agentResult.getResponse().getContentAsString())
                .get("id").asText();

        // Step 4: Start a session
        var sessionResult = mockMvc.perform(post("/api/v1/sessions/start")
                        .param("agentId", agentId)
                        .param("task", "Implement OAuth refresh tokens")
                        .param("repositoryId", repositoryId)
                        .param("projectId", projectId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task").value("Implement OAuth refresh tokens"))
                .andExpect(jsonPath("$.status").value("active"))
                .andReturn();
        sessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("id").asText();

        // Step 5: Store a memory
        String memoryJson = objectMapper.writeValueAsString(Map.of(
                "content", "Use PostgreSQL for refresh token persistence",
                "type", "DECLARATIVE",
                "scope", "PROJECT",
                "status", "NEW",
                "confidence", 0.8,
                "importance", 0.7,
                "tags", java.util.List.of("database", "auth")
        ));
        var memoryResult = mockMvc.perform(post("/api/v1/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memoryJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Use PostgreSQL for refresh token persistence"))
                .andExpect(jsonPath("$.type").value("DECLARATIVE"))
                .andReturn();
        memoryId = objectMapper.readTree(memoryResult.getResponse().getContentAsString())
                .get("id").asText();

        // Step 6: Record a decision
        String decisionJson = objectMapper.writeValueAsString(
                TestDataFactory.createDecision(
                        "Use Stripe for payment processing",
                        "Chose Stripe API for payment integration"));
        var decisionResult = mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Use Stripe for payment processing"))
                .andReturn();
        decisionId = objectMapper.readTree(decisionResult.getResponse().getContentAsString())
                .get("id").asText();

        // Step 7: Create a task
        String taskJson = objectMapper.writeValueAsString(
                TestDataFactory.createTask(
                        "Implement payment webhook",
                        "Handle Stripe webhook events"));
        var taskResult = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Implement payment webhook"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        taskId = objectMapper.readTree(taskResult.getResponse().getContentAsString())
                .get("id").asText();

        // Step 8: Verify project was created
        mockMvc.perform(get("/api/v1/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Automorium"));

        // Step 9: Verify memory search works
        mockMvc.perform(get("/api/v1/memory/search").param("q", "PostgreSQL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        // Step 10: Verify open tasks
        mockMvc.perform(get("/api/v1/tasks/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        // Step 11: End the session
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/end")
                        .param("summary", "Completed OAuth refresh token implementation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.summary").value("Completed OAuth refresh token implementation"));

        // Step 12: Update task status
        mockMvc.perform(put("/api/v1/tasks/" + taskId + "/status")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Step 13: Get recent sessions
        mockMvc.perform(get("/api/v1/sessions/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        // Step 14: Get recent decisions
        mockMvc.perform(get("/api/v1/decisions/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        // Step 15: Delete memory
        mockMvc.perform(delete("/api/v1/memory/" + memoryId))
                .andExpect(status().isNoContent());

        // Step 16: Verify memory is gone
        mockMvc.perform(get("/api/v1/memory/" + memoryId))
                .andExpect(status().isNotFound());
    }
}
