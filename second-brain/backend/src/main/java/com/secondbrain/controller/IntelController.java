package com.secondbrain.controller;

import com.secondbrain.service.BrainBackupService;
import com.secondbrain.service.CodeReviewService;
import com.secondbrain.service.DiagramIngestionService;
import com.secondbrain.service.ImpactAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intel")
@RequiredArgsConstructor
public class IntelController {

    private final ImpactAnalysisService impactAnalysisService;
    private final CodeReviewService codeReviewService;
    private final DiagramIngestionService diagramIngestionService;
    private final BrainBackupService backupService;

    @PostMapping("/impact-analysis")
    public ResponseEntity<Map<String, Object>> analyzeImpact(@RequestBody Map<String, String> payload) {
        String filePath = payload.get("filePath");
        String diff = payload.get("diff");
        String projIdStr = payload.get("projectId");
        UUID projId = (projIdStr != null && !projIdStr.isBlank()) ? UUID.fromString(projIdStr) : null;

        return ResponseEntity.ok(impactAnalysisService.analyzeImpact(filePath, diff, projId));
    }

    @PostMapping("/review")
    public ResponseEntity<Map<String, Object>> reviewCode(@RequestBody Map<String, String> payload) {
        String diff = payload.get("diff");
        String projIdStr = payload.get("projectId");
        String repoIdStr = payload.get("repositoryId");

        UUID projId = (projIdStr != null && !projIdStr.isBlank()) ? UUID.fromString(projIdStr) : null;
        UUID repoId = (repoIdStr != null && !repoIdStr.isBlank()) ? UUID.fromString(repoIdStr) : null;

        return ResponseEntity.ok(codeReviewService.reviewChanges(diff, projId, repoId));
    }

    @PostMapping("/ingest-diagram")
    public ResponseEntity<Map<String, Object>> ingestDiagram(@RequestBody Map<String, String> payload) {
        String diagram = payload.get("diagram");
        String format = payload.getOrDefault("format", "mermaid");
        String projIdStr = payload.get("projectId");
        UUID projId = (projIdStr != null && !projIdStr.isBlank()) ? UUID.fromString(projIdStr) : null;

        return ResponseEntity.ok(diagramIngestionService.ingestDiagram(diagram, format, projId));
    }

    @GetMapping("/backup/export")
    public ResponseEntity<Map<String, Object>> exportBackup() {
        return ResponseEntity.ok(backupService.exportSnapshot());
    }

    @PostMapping("/backup/import")
    public ResponseEntity<Map<String, Object>> importBackup(@RequestBody Map<String, Object> snapshot) {
        return ResponseEntity.ok(backupService.importSnapshot(snapshot));
    }
}
