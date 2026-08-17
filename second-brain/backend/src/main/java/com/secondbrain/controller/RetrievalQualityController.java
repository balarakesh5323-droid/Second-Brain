package com.secondbrain.controller;

import com.secondbrain.service.RetrievalQualityService;
import com.secondbrain.service.RetrievalQualityService.QualityReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/quality")
@RequiredArgsConstructor
public class RetrievalQualityController {

    private final RetrievalQualityService retrievalQualityService;

    @GetMapping("/evaluate")
    public ResponseEntity<QualityReport> evaluate(
            @RequestParam(required = false) String projectId) {
        QualityReport report = retrievalQualityService.evaluate(projectId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/dataset")
    public ResponseEntity<?> getTestDataset() {
        return ResponseEntity.ok(retrievalQualityService.getTestDataset());
    }
}
