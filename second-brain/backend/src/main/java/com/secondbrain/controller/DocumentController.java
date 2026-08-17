package com.secondbrain.controller;

import com.secondbrain.common.entity.ProjectDocument;
import com.secondbrain.service.DocumentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/project/{projectId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectDocument> uploadProjectDocument(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) Set<String> tags) {
        ProjectDocument doc = documentService.uploadProjectDocument(projectId, file, title, description, tags);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @PostMapping("/project/{projectId}/note")
    public ResponseEntity<ProjectDocument> createProjectNote(
            @PathVariable UUID projectId,
            @RequestBody ProjectNoteRequest request) {
        ProjectDocument doc = documentService.createProjectNote(
                projectId,
                request.getTitle(),
                request.getContent(),
                request.getDescription(),
                request.getTags()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ProjectDocument>> getProjectDocuments(@PathVariable UUID projectId) {
        return ResponseEntity.ok(documentService.getProjectDocuments(projectId));
    }

    @GetMapping
    public ResponseEntity<List<ProjectDocument>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDocument> getDocumentById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        documentService.deleteProjectDocument(id);
        return ResponseEntity.noContent().build();
    }

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class ProjectNoteRequest {
        private String title;
        private String content;
        private String description;
        private Set<String> tags;
    }
}
