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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/agents - create agent returns 201")
    void createAgent() throws Exception {
        String json = objectMapper.writeValueAsString(TestDataFactory.createAgent("claude-code"));

        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("claude-code"))
                .andExpect(jsonPath("$.type").value("test-agent"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/agents - get all agents returns 200")
    void getAllAgents() throws Exception {
        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createAgent("agent-1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createAgent("agent-2"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/agents/{id} - get by id returns 200")
    void getAgentById() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createAgent("findable-agent"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(get("/api/v1/agents/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("findable-agent"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/agents/{id} - non-existent id returns 404")
    void getAgentByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/agents/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("PUT /api/v1/agents/{id} - update agent returns 200")
    void updateAgent() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createAgent("old-name"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        String updateJson = objectMapper.writeValueAsString(TestDataFactory.createAgent("new-name"));

        mockMvc.perform(put("/api/v1/agents/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("new-name"));
    }

    @Test
    @Order(6)
    @DisplayName("DELETE /api/v1/agents/{id} - delete agent returns 204")
    void deleteAgent() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createAgent("to-delete"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(delete("/api/v1/agents/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/agents/" + id))
                .andExpect(status().isNotFound());
    }
}
