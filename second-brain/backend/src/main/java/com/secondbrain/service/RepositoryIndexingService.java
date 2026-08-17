package com.secondbrain.service;

import com.secondbrain.parser.LanguageParserFactory;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryIndexingService {

    private final GitService gitService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final GraphService graphService;
    private final LanguageParserFactory languageParserFactory;

    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".go", ".rs",
        ".c", ".cpp", ".cc", ".cxx", ".h", ".hpp", ".hxx", ".hh",
        ".rb", ".php", ".kt", ".kts", ".swift", ".scala", ".sc",
        ".cs", ".m", ".mm", ".dart", ".lua", ".r", ".R",
        ".hs", ".lhs", ".ex", ".exs", ".erl", ".hrl",
        ".sh", ".bash", ".zsh", ".fish",
        ".html", ".htm", ".vue", ".svelte",
        ".sql", ".ddl", ".dml",
        ".yaml", ".yml",
        ".css", ".scss", ".sass", ".less",
        ".groovy", ".gradle", ".sol", ".jl", ".pl", ".pm"
    );

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
                .filter(p -> {
                    String fileName = p.getFileName().toString().toLowerCase();
                    if (fileName.equals("dockerfile") || fileName.startsWith("dockerfile.")) return true;
                    if (fileName.equals("docker-compose.yml") || fileName.equals("docker-compose.yaml")) return true;
                    int lastDot = fileName.lastIndexOf('.');
                    if (lastDot < 0) return false;
                    String ext = fileName.substring(lastDot);
                    return CODE_EXTENSIONS.contains(ext);
                })
                .filter(p -> {
                    String fileName = p.getFileName().toString().toLowerCase();
                    if (fileName.contains("node_modules") || fileName.contains(".gradle") ||
                        fileName.contains("build/") || fileName.contains("target/") ||
                        fileName.contains("dist/") || fileName.contains("__pycache__") ||
                        fileName.contains(".git/")) {
                        return false;
                    }
                    return true;
                })
                .limit(200)
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        Map<String, Object> structure = languageParserFactory.parseFile(path.toString(), content);
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

    public Map<String, Object> analyzeFile(String filePath, String content) {
        return languageParserFactory.parseFile(filePath, content);
    }

    public List<Map<String, String>> extractFileDependencies(String filePath, String content) {
        return languageParserFactory.extractDependencies(filePath, content);
    }

    public Map<String, String> getSupportedLanguages() {
        return languageParserFactory.getExtensionToLanguageMap();
    }
}
