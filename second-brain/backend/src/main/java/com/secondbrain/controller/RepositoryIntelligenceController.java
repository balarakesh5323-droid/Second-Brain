package com.secondbrain.controller;

import com.secondbrain.service.GitService;
import com.secondbrain.service.GitHubCloneService;
import com.secondbrain.service.RepositoryIndexingService;
import com.secondbrain.service.RepositoryIngestionService;
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
    private final RepositoryIngestionService ingestionService;
    private final GitHubCloneService gitHubCloneService;

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

    @PostMapping("/add-url")
    public ResponseEntity<Map<String, Object>> addRepositoryByUrl(
            @RequestBody Map<String, String> request) {
        String url = request.get("url");
        String projectIdStr = request.get("projectId");

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "URL is required",
                "status", "failed"
            ));
        }

        if (!gitHubCloneService.isGitHubUrl(url)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Not a valid GitHub URL: " + url,
                "status", "failed"
            ));
        }

        UUID projectId = null;
        if (projectIdStr != null && !projectIdStr.isBlank()) {
            try {
                projectId = UUID.fromString(projectIdStr);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid projectId UUID",
                    "status", "failed"
                ));
            }
        }

        log.info("Adding repository from URL: {} (project: {})", url, projectId);
        Map<String, Object> result = ingestionService.ingestFromUrl(url, projectId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync/{repositoryId}")
    public ResponseEntity<Map<String, Object>> syncRepository(@PathVariable UUID repositoryId) {
        log.info("Triggering git sync for repository ID: {}", repositoryId);
        Map<String, Object> result = ingestionService.syncRepository(repositoryId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/webhook/github")
    public ResponseEntity<Map<String, Object>> handleGitHubWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "push") String event) {
        log.info("Received GitHub webhook event: {}", event);

        if (!"push".equalsIgnoreCase(event)) {
            return ResponseEntity.ok(Map.of("status", "ignored", "message", "Only 'push' events are ingested"));
        }

        try {
            Map<String, Object> repoData = (Map<String, Object>) payload.get("repository");
            if (repoData == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No repository payload found"));
            }

            String htmlUrl = (String) repoData.get("html_url");
            String cloneUrl = (String) repoData.get("clone_url");
            String targetUrl = htmlUrl != null ? htmlUrl : cloneUrl;

            if (targetUrl == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No repository URL found in webhook"));
            }

            log.info("Auto-ingesting git push event for: {}", targetUrl);
            Map<String, Object> syncResult = ingestionService.syncRepositoryByUrl(targetUrl);
            return ResponseEntity.ok(Map.of(
                "status", "synced",
                "repository", targetUrl,
                "syncResult", syncResult
            ));
        } catch (Exception e) {
            log.error("GitHub webhook ingestion failed", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/git-hook-script")
    public ResponseEntity<Map<String, String>> getGitHookScript(
            @RequestParam(defaultValue = "http://localhost:8080") String serverUrl) {
        String script = "#!/bin/sh\n" +
            "# Second Brain Auto-Ingestion Post-Commit Hook\n" +
            "# Place this file in .git/hooks/post-commit and run: chmod +x .git/hooks/post-commit\n\n" +
            "REPO_URL=$(git config --get remote.origin.url 2>/dev/null || echo \"\")\n" +
            "if [ -n \"$REPO_URL\" ]; then\n" +
            "  echo \"[Second Brain] Syncing commit to Second Brain...\"\n" +
            "  curl -s -X POST \"" + serverUrl + "/api/v1/repository-intel/add-url\" \\\n" +
            "    -H \"Content-Type: application/json\" \\\n" +
            "    -d \"{\\\"url\\\": \\\"$REPO_URL\\\"}\" > /dev/null 2>&1 &\n" +
            "fi\n";

        return ResponseEntity.ok(Map.of(
            "filename", "post-commit",
            "instructions", "Save as .git/hooks/post-commit in your repository and make it executable (chmod +x .git/hooks/post-commit)",
            "script", script
        ));
    }
}
