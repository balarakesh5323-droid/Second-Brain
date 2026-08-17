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
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createMemoryDtoJson(String content, String type, String scope) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "content", content,
                "type", type,
                "scope", scope,
                "status", "NEW",
                "confidence", 0.8,
                "importance", 0.5,
                "tags", Set.of("test", "api")
        ));
    }

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/memory - create memory returns 201")
    void createMemory() throws Exception {
        String json = createMemoryDtoJson(
                "PostgreSQL is preferred for relational data", "DECLARATIVE", "GLOBAL");

        mockMvc.perform(post("/api/v1/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.content").value("PostgreSQL is preferred for relational data"))
                .andExpect(jsonPath("$.type").value("DECLARATIVE"))
                .andExpect(jsonPath("$.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.confidence").value(0.8))
                .andExpect(jsonPath("$.importance").value(0.5));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/memory - get all memories returns 200")
    void getAllMemories() throws Exception {
        mockMvc.perform(post("/api/v1/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createMemoryDtoJson("Memory one", "SEMANTIC", "PROJECT")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createMemoryDtoJson("Memory two", "EPISODIC", "GLOBAL")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/memory/{id} - get by id returns 200")
    void getMemoryById() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createMemoryDtoJson("Findable memory", "PROCEDURAL", "GLOBAL")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(get("/api/v1/memory/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.content").value("Findable memory"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/memory/{id} - non-existent id returns 404")
    void getMemoryByIdNotFound() throws Exception {
        UUID fakeId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/memory/" + fakeId))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/v1/memory/type/{type} - get by type returns 200")
    void getMemoryByType() throws Exception {
        mockMvc.perform(post("/api/v1/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createMemoryDtoJson("Semantic memory", "SEMANTIC", "GLOBAL")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/memory/type/SEMANTIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].type").value("SEMANTIC"));
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/v1/memory/search?q={query} - search returns 200")
    void searchMemory() throws Exception {
        mockMvc.perform(post("/api/v1/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createMemoryDtoJson("Use Redis for caching sessions", "DECLARATIVE", "PROJECT")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/memory/search").param("q", "Redis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(7)
    @DisplayName("PUT /api/v1/memory/{id} - update memory returns 200")
    void updateMemory() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createMemoryDtoJson("Original content", "DECLARATIVE", "GLOBAL")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        String updateJson = objectMapper.writeValueAsString(Map.of(
                "content", "Updated content",
                "importance", 0.95
        ));

        mockMvc.perform(put("/api/v1/memory/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.content").value("Updated content"))
                .andExpect(jsonPath("$.importance").value(0.95));
    }

    @Test
    @Order(8)
    @DisplayName("DELETE /api/v1/memory/{id} - delete memory returns 204")
    void deleteMemory() throws Exception {
        String createResult = mockMvc.perform(post("/api/v1/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createMemoryDtoJson("To be deleted", "DECLARATIVE", "GLOBAL")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(delete("/api/v1/memory/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/memory/" + id))
                .andExpect(status().isNotFound());
    }
}
