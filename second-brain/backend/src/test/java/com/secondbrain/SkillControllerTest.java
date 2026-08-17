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
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/skills - create skill returns 201")
    void createSkill() throws Exception {
        String json = objectMapper.writeValueAsString(TestDataFactory.createSkill("code-review"));

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("code-review"))
                .andExpect(jsonPath("$.scope").value("global"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/skills - get all skills returns 200")
    void getAllSkills() throws Exception {
        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createSkill("skill-1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createSkill("skill-2"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/skills/{id} - get by id returns 200")
    void getSkillById() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createSkill("findable"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(get("/api/v1/skills/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("findable"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/skills/{id} - non-existent id returns 404")
    void getSkillByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/skills/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/v1/skills/scope/{scope} - get by scope returns 200")
    void getSkillsByScope() throws Exception {
        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createSkill("scoped-skill"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/skills/scope/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].scope").value("global"));
    }

    @Test
    @Order(6)
    @DisplayName("PUT /api/v1/skills/{id} - update skill returns 200")
    void updateSkill() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createSkill("old-name"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        String updateJson = objectMapper.writeValueAsString(TestDataFactory.createSkill("new-name"));

        mockMvc.perform(put("/api/v1/skills/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("new-name"));
    }

    @Test
    @Order(7)
    @DisplayName("DELETE /api/v1/skills/{id} - delete skill returns 204")
    void deleteSkill() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.createSkill("to-delete"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(delete("/api/v1/skills/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/skills/" + id))
                .andExpect(status().isNotFound());
    }
}
