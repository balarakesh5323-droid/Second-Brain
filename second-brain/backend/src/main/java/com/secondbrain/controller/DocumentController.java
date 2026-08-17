package com.secondbrain.controller;

import com.secondbrain.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file) {
        try {
            String name = documentService.uploadDocument(
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    file.getContentType(),
                    file.getSize()
            );
            return ResponseEntity.ok(Map.of("name", name, "status", "uploaded"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<String>> listDocuments() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    @GetMapping("/{name}/url")
    public ResponseEntity<Map<String, String>> getDocumentUrl(@PathVariable String name) {
        return ResponseEntity.ok(Map.of("url", documentService.getDocumentUrl(name)));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String name) {
        documentService.deleteDocument(name);
        return ResponseEntity.noContent().build();
    }
}
