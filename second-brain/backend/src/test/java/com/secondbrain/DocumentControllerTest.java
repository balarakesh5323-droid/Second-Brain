package com.secondbrain;

import com.secondbrain.common.entity.ProjectDocument;
import com.secondbrain.service.DocumentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentService documentService;

    @Test
    @Order(1)
    @DisplayName("GET /api/v1/documents - list all documents returns list")
    void testGetAllDocuments() throws Exception {
        ProjectDocument doc1 = ProjectDocument.builder()
                .title("Architecture RFC")
                .fileName("arch_rfc.md")
                .fileType("DOCUMENT")
                .contentType("text/markdown")
                .storageKey("projects/1/arch.md")
                .build();

        ProjectDocument doc2 = ProjectDocument.builder()
                .title("System Architecture Diagram")
                .fileName("diagram.png")
                .fileType("IMAGE")
                .contentType("image/png")
                .storageKey("projects/1/diagram.png")
                .build();

        when(documentService.getAllDocuments()).thenReturn(List.of(doc1, doc2));

        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Architecture RFC"))
                .andExpect(jsonPath("$[1].fileType").value("IMAGE"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/documents/project/{projectId} - get project documents")
    void testGetProjectDocuments() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectDocument doc = ProjectDocument.builder()
                .title("API Spec")
                .fileName("api.md")
                .fileType("DOCUMENT")
                .contentType("text/markdown")
                .storageKey("projects/" + projectId + "/api.md")
                .build();

        when(documentService.getProjectDocuments(projectId)).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/documents/project/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("API Spec"));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/v1/documents/project/{projectId}/note - create project note")
    void testCreateProjectNote() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectDocument createdDoc = ProjectDocument.builder()
                .title("Database Schema RFC")
                .fileName("db_schema_rfc.md")
                .fileType("DOCUMENT")
                .contentType("text/markdown")
                .storageKey("projects/" + projectId + "/notes/db.md")
                .build();

        when(documentService.createProjectNote(eq(projectId), any(), any(), any(), any()))
                .thenReturn(createdDoc);

        String noteJson = """
            {
                "title": "Database Schema RFC",
                "content": "# DB RFC\\nPostgreSQL schema details",
                "description": "Schema definition",
                "tags": ["schema", "database"]
            }
        """;

        mockMvc.perform(post("/api/v1/documents/project/" + projectId + "/note")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Database Schema RFC"))
                .andExpect(jsonPath("$.fileType").value("DOCUMENT"));
    }
}
