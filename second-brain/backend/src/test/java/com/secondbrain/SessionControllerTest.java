package com.secondbrain;

import com.secondbrain.test.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createAgent(String name) throws Exception {
        String result = mockMvc.perform(post("/api/v1/agents")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(TestDataFactory.createAgent(name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(result).get("id").asText());
    }

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/sessions/start - start session returns 201")
    void startSession() throws Exception {
        UUID agentId = createAgent("session-agent");

        mockMvc.perform(post("/api/v1/sessions/start")
                        .param("agentId", agentId.toString())
                        .param("task", "Implement caching layer"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.task").value("Implement caching layer"))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/sessions/{id} - get by id returns 200")
    void getSessionById() throws Exception {
        UUID agentId = createAgent("findable-agent");

        String startResult = mockMvc.perform(post("/api/v1/sessions/start")
                        .param("agentId", agentId.toString())
                        .param("task", "Find session task"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID sessionId = UUID.fromString(objectMapper.readTree(startResult).get("id").asText());

        mockMvc.perform(get("/api/v1/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.task").value("Find session task"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/sessions/{id} - non-existent id returns 404")
    void getSessionByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/sessions/recent - get recent sessions returns 200")
    void getRecentSessions() throws Exception {
        UUID agentId = createAgent("recent-agent");

        mockMvc.perform(post("/api/v1/sessions/start")
                        .param("agentId", agentId.toString())
                        .param("task", "Recent session"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/sessions/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/v1/sessions/{id}/end - end session returns 200")
    void endSession() throws Exception {
        UUID agentId = createAgent("ending-agent");

        String startResult = mockMvc.perform(post("/api/v1/sessions/start")
                        .param("agentId", agentId.toString())
                        .param("task", "Session to end"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID sessionId = UUID.fromString(objectMapper.readTree(startResult).get("id").asText());

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/end")
                        .param("summary", "Task completed successfully"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.summary").value("Task completed successfully"));
    }
}
