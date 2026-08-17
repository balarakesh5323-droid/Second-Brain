package com.secondbrain.controller;

import com.secondbrain.service.GitService;
import com.secondbrain.service.RepositoryIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/repository-intel")
@RequiredArgsConstructor
@Slf4j
public class RepositoryIntelligenceController {

    private final GitService gitService;
    private final RepositoryIndexingService indexingService;

    @GetMapping("/commits")
    public ResponseEntity<List<Map<String, Object>>> getCommits(
            @RequestParam String path,
            @RequestParam(defaultValue = "20") int count) throws Exception {
        return ResponseEntity.ok(gitService.getRecentCommits(path, count));
    }

    @GetMapping("/commit/{commitId}")
    public ResponseEntity<Map<String, Object>> getCommitDetails(
            @RequestParam String path,
            @PathVariable String commitId) throws Exception {
        return ResponseEntity.ok(gitService.getCommitDetails(path, commitId));
    }

    @GetMapping("/branches")
    public ResponseEntity<List<String>> getBranches(@RequestParam String path) throws Exception {
        return ResponseEntity.ok(gitService.getBranches(path));
    }

    @GetMapping("/file-history")
    public ResponseEntity<List<String>> getFileHistory(
            @RequestParam String path,
            @RequestParam String filePath,
            @RequestParam(defaultValue = "10") int count) throws Exception {
        return ResponseEntity.ok(gitService.getFileHistory(path, filePath, count));
    }

    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> indexRepository(
            @RequestParam String path,
            @RequestParam UUID repositoryId) {
        return ResponseEntity.ok(indexingService.indexRepository(path, repositoryId));
    }

    @GetMapping("/structure")
    public ResponseEntity<List<Map<String, Object>>> analyzeStructure(@RequestParam String path) {
        return ResponseEntity.ok(indexingService.analyzeCodeStructure(path));
    }
}
