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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createAgent(String name) throws Exception {
        String result = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createAgent(name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(result).get("id").asText());
    }

    private UUID startSession(UUID agentId, String task) throws Exception {
        String result = mockMvc.perform(post("/api/v1/sessions/start")
                        .param("agentId", agentId.toString())
                        .param("task", task))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(result).get("id").asText());
    }

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/events - record event returns 201")
    void recordEvent() throws Exception {
        UUID agentId = createAgent("event-agent");
        UUID sessionId = startSession(agentId, "Event test session");

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "sessionId", sessionId.toString(),
                "eventType", "SESSION_STARTED",
                "description", "Session initialized",
                "status", "success"
        ));

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.eventType").value("SESSION_STARTED"))
                .andExpect(jsonPath("$.description").value("Session initialized"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/events - get recent events returns 200")
    void getRecentEvents() throws Exception {
        UUID agentId = createAgent("recent-event-agent");
        UUID sessionId = startSession(agentId, "Recent events session");

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "sessionId", sessionId.toString(),
                "eventType", "FILE_READ",
                "description", "Read a file",
                "status", "success"
        ));

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/events/type/{type} - get by type returns 200")
    void getEventsByType() throws Exception {
        UUID agentId = createAgent("type-event-agent");
        UUID sessionId = startSession(agentId, "Type filter session");

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "sessionId", sessionId.toString(),
                "eventType", "TEST_PASSED",
                "description", "All tests passed",
                "status", "success"
        ));

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/events/type/TEST_PASSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }
}
