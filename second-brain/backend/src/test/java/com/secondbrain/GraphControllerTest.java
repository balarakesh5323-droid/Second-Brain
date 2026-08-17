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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.secondbrain.service.GraphService graphService;

    @Autowired
    private com.secondbrain.service.GraphSyncService graphSyncService;

    @Test
    @Order(1)
    @DisplayName("GET /api/v1/graph/stats - returns mocked graph stats")
    void getStats() throws Exception {
        when(graphService.getStats()).thenReturn(Map.of(
                "nodeCount", 42,
                "relationshipCount", 15,
                "labels", List.of("Project", "Agent", "Memory")
        ));

        mockMvc.perform(get("/api/v1/graph/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeCount").value(42))
                .andExpect(jsonPath("$.relationshipCount").value(15))
                .andExpect(jsonPath("$.labels").isArray());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/graph/sync - triggers sync and returns status")
    void syncAll() throws Exception {
        mockMvc.perform(post("/api/v1/graph/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sync completed"));
    }
}
