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
class TechnologyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/technologies - create technology returns 201")
    void createTechnology() throws Exception {
        String json = objectMapper.writeValueAsString(
                TestDataFactory.createTechnology("Spring Boot", "framework"));

        mockMvc.perform(post("/api/v1/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Spring Boot"))
                .andExpect(jsonPath("$.category").value("framework"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/technologies - get all technologies returns 200")
    void getAllTechnologies() throws Exception {
        mockMvc.perform(post("/api/v1/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createTechnology("Tech1", "lib"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createTechnology("Tech2", "tool"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/technologies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/technologies/{id} - get by id returns 200")
    void getTechnologyById() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createTechnology("Findable", "db"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(get("/api/v1/technologies/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Findable"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/technologies/{id} - non-existent id returns 404")
    void getTechnologyByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/technologies/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("PUT /api/v1/technologies/{id} - update technology returns 200")
    void updateTechnology() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createTechnology("OldTech", "old"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        String updateJson = objectMapper.writeValueAsString(
                TestDataFactory.createTechnology("NewTech", "new"));

        mockMvc.perform(put("/api/v1/technologies/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("NewTech"));
    }

    @Test
    @Order(6)
    @DisplayName("DELETE /api/v1/technologies/{id} - delete technology returns 204")
    void deleteTechnology() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/technologies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestDataFactory.createTechnology("ToDelete", "del"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(delete("/api/v1/technologies/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/technologies/" + id))
                .andExpect(status().isNotFound());
    }
}
