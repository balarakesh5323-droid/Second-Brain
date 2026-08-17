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
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/projects - create project returns 201")
    void createProject() throws Exception {
        String json = objectMapper.writeValueAsString(
                TestDataFactory.createProject("SecondBrain"));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("SecondBrain"))
                .andExpect(jsonPath("$.description").value("Test project: SecondBrain"))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/projects - get all projects returns 200")
    void getAllProjects() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createProject("Proj1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createProject("Proj2"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/projects/{id} - get by id returns 200")
    void getProjectById() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createProject("Findable"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(get("/api/v1/projects/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Findable"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/projects/{id} - non-existent id returns 404")
    void getProjectByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("PUT /api/v1/projects/{id} - update project returns 200")
    void updateProject() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createProject("OldName"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        String updateJson = objectMapper.writeValueAsString(
                TestDataFactory.createProject("NewName"));

        mockMvc.perform(put("/api/v1/projects/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("NewName"));
    }

    @Test
    @Order(6)
    @DisplayName("DELETE /api/v1/projects/{id} - delete project returns 204")
    void deleteProject() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createProject("ToDelete"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(delete("/api/v1/projects/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + id))
                .andExpect(status().isNotFound());
    }
}
