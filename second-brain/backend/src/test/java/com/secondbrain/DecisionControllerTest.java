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
class DecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/decisions - create decision returns 201")
    void createDecision() throws Exception {
        String json = objectMapper.writeValueAsString(
                TestDataFactory.createDecision("Use PostgreSQL", "Chosen for ACID compliance"));

        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Use PostgreSQL"))
                .andExpect(jsonPath("$.description").value("Chosen for ACID compliance"))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/decisions - get all decisions returns 200")
    void getAllDecisions() throws Exception {
        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createDecision("Decision 1", "Desc 1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createDecision("Decision 2", "Desc 2"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/decisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/decisions/{id} - get by id returns 200")
    void getDecisionById() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createDecision("Findable Decision", "Details"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(get("/api/v1/decisions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Findable Decision"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/decisions/{id} - non-existent id returns 404")
    void getDecisionByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/decisions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/v1/decisions/recent - get recent decisions returns 200")
    void getRecentDecisions() throws Exception {
        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createDecision("Recent Decision", "Recent"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/decisions/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(6)
    @DisplayName("PUT /api/v1/decisions/{id} - update decision returns 200")
    void updateDecision() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createDecision("Old Title", "Old Desc"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        String updateJson = objectMapper.writeValueAsString(
                TestDataFactory.createDecision("New Title", "New Desc"));

        mockMvc.perform(put("/api/v1/decisions/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("New Title"));
    }

    @Test
    @Order(7)
    @DisplayName("DELETE /api/v1/decisions/{id} - delete decision returns 204")
    void deleteDecision() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createDecision("To Delete", "Delete me"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(delete("/api/v1/decisions/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/decisions/" + id))
                .andExpect(status().isNotFound());
    }
}
