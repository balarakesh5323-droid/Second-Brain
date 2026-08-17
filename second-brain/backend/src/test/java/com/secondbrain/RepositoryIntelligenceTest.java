package com.secondbrain;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepositoryIntelligenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.secondbrain.service.GitService gitService;

    @Autowired
    private com.secondbrain.service.RepositoryIndexingService repositoryIndexingService;

    @Test
    @Order(1)
    @DisplayName("GET /api/v1/repository-intel/commits - returns mocked commits")
    void getCommits() throws Exception {
        when(gitService.getRecentCommits(anyString(), anyInt()))
                .thenReturn(List.of(
                        Map.of("id", "abc123", "message", "Initial commit", "author", "dev"),
                        Map.of("id", "def456", "message", "Add feature", "author", "dev")
                ));

        mockMvc.perform(get("/api/v1/repository-intel/commits")
                        .param("path", "/test/repo")
                        .param("count", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("abc123"))
                .andExpect(jsonPath("$[1].message").value("Add feature"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/repository-intel/branches - returns mocked branches")
    void getBranches() throws Exception {
        when(gitService.getBranches(anyString()))
                .thenReturn(List.of("refs/heads/main", "refs/heads/feature/auth"));

        mockMvc.perform(get("/api/v1/repository-intel/branches")
                        .param("path", "/test/repo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0]").value("refs/heads/main"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/repository-intel/structure - returns mocked structure")
    void analyzeStructure() throws Exception {
        when(repositoryIndexingService.analyzeCodeStructure(anyString()))
                .thenReturn(List.of(
                        Map.of("file", "Main.java", "classes", List.of("MainApp")),
                        Map.of("file", "Config.java", "classes", List.of("AppConfig"))
                ));

        mockMvc.perform(get("/api/v1/repository-intel/structure")
                        .param("path", "/test/repo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].file").value("Main.java"));
    }
}
