package com.secondbrain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryIndexingService {

    private final GitService gitService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final GraphService graphService;

    public Map<String, Object> indexRepository(String repoPath, UUID repositoryId) {
        log.info("Starting repository indexing: {}", repoPath);
        Map<String, Object> result = new HashMap<>();

        try {
            String currentBranch = gitService.getCurrentBranch(repoPath);
            List<String> branches = gitService.getBranches(repoPath);
            List<Map<String, Object>> recentCommits = gitService.getRecentCommits(repoPath, 20);

            result.put("branch", currentBranch);
            result.put("branches", branches);
            result.put("commitCount", recentCommits.size());
            result.put("status", "indexed");

            for (Map<String, Object> commit : recentCommits) {
                String content = (String) commit.get("message");
                float[] embedding = embeddingService.embed(content);
            }

            graphService.createNode("Repository", repositoryId.toString(), Map.of(
                "branch", currentBranch,
                "branchCount", branches.size()
            ));

            log.info("Repository indexing completed: {} branches, {} recent commits", branches.size(), recentCommits.size());
        } catch (Exception e) {
            log.error("Repository indexing failed: {}", e.getMessage());
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }

    public List<Map<String, Object>> analyzeCodeStructure(String repoPath) {
        List<Map<String, Object>> structures = new ArrayList<>();
        try {
            Files.walk(Paths.get(repoPath))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .limit(100)
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        Map<String, Object> structure = JavaParserService.parseJavaFile(path.toString(), content);
                        if (structure != null) {
                            structures.add(structure);
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse {}: {}", path, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.error("Code structure analysis failed: {}", e.getMessage());
        }
        return structures;
    }
}
