package com.secondbrain.controller;

import com.secondbrain.service.GraphService;
import com.secondbrain.service.GraphSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final GraphSyncService graphSyncService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(graphService.getStats());
    }

    @GetMapping("/nodes/{label}")
    public ResponseEntity<List<Map<String, Object>>> getNodes(
            @PathVariable String label,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(graphService.getNodesByLabel(label, limit));
    }

    @GetMapping("/related/{label}/{id}")
    public ResponseEntity<List<Map<String, Object>>> getRelated(
            @PathVariable String label,
            @PathVariable String id,
            @RequestParam(defaultValue = "2") int depth) {
        return ResponseEntity.ok(graphService.findRelated(label, id, null, depth));
    }

    @GetMapping("/path")
    public ResponseEntity<List<Map<String, Object>>> findPath(
            @RequestParam String fromLabel,
            @RequestParam String fromId,
            @RequestParam String toLabel,
            @RequestParam String toId,
            @RequestParam(defaultValue = "5") int maxDepth) {
        return ResponseEntity.ok(graphService.findPath(fromLabel, fromId, toLabel, toId, maxDepth));
    }

    @GetMapping("/search/{label}")
    public ResponseEntity<List<Map<String, Object>>> search(
            @PathVariable String label,
            @RequestParam String property,
            @RequestParam String value,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(graphService.searchByProperty(label, property, value, limit));
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> syncAll() {
        graphSyncService.syncAll();
        return ResponseEntity.ok(Map.of("status", "sync completed"));
    }
}
