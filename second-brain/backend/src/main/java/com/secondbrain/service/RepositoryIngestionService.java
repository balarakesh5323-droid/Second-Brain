package com.secondbrain.service;

import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.parser.LanguageParserFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryIngestionService {

    private final GitHubCloneService gitHubCloneService;
    private final RepositoryBootstrapService bootstrapService;
    private final RepositoryIndexingService indexingService;
    private final GitService gitService;
    private final EmbeddingService embeddingService;
    private final GraphService graphService;
    private final VectorStoreService vectorStoreService;
    private final LanguageParserFactory languageParserFactory;
    private final RepositoryEntityRepository repositoryRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public Map<String, Object> ingestFromUrl(String url, UUID projectId) {
        log.info("Starting full ingestion pipeline for: {}", url);
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Clone
            result.put("step", "cloning");
            GitHubCloneService.CloneResult clone = gitHubCloneService.cloneRepository(url);
            result.put("localPath", clone.localPath());
            result.put("owner", clone.owner());
            result.put("repoName", clone.repoName());
            result.put("branch", clone.defaultBranch());
            result.put("alreadyCloned", clone.alreadyCloned());
            log.info("Clone complete: {} (already existed: {})", clone.localPath(), clone.alreadyCloned());

            // Step 2: Bootstrap detection
            result.put("step", "bootstrapping");
            RepositoryBootstrapService.BootstrapResult bootstrap = bootstrapService.bootstrap(clone.localPath());
            result.put("languages", bootstrap.getLanguages());
            result.put("frameworks", bootstrap.getFrameworks());
            result.put("databases", bootstrap.getDatabases());
            result.put("packageManagers", bootstrap.getPackageManagers());
            result.put("cicd", bootstrap.getCicd());
            log.info("Bootstrap complete: {} languages, {} frameworks",
                bootstrap.getLanguages().size(), bootstrap.getFrameworks().size());

            // Step 3: Analyze code structure
            result.put("step", "analyzing_code");
            List<Map<String, Object>> codeStructure = indexingService.analyzeCodeStructure(clone.localPath());
            result.put("codeStructureCount", codeStructure.size());
            log.info("Code analysis complete: {} files parsed", codeStructure.size());

            // Step 4: Index commits + embed
            result.put("step", "indexing_commits");
            List<Map<String, Object>> commits = new ArrayList<>();
            try {
                commits = gitService.getRecentCommits(clone.localPath(), 50);
            } catch (Exception e) {
                log.warn("Failed to get commits: {}", e.getMessage());
            }

            int embedded = 0;
            for (Map<String, Object> commit : commits) {
                try {
                    String message = (String) commit.get("message");
                    float[] embedding = embeddingService.embed(message);
                    String commitId = UUID.randomUUID().toString();

                    vectorStoreService.upsert("repository_knowledge", commitId, embedding,
                        Map.of(
                            "repository", clone.repoName(),
                            "owner", clone.owner(),
                            "type", "commit",
                            "commitId", (String) commit.get("id"),
                            "message", message,
                            "author", (String) commit.getOrDefault("author", "unknown")
                        ),
                        Map.of()
                    );
                    embedded++;
                } catch (Exception e) {
                    log.debug("Failed to embed commit: {}", e.getMessage());
                }
            }
            result.put("commitsEmbedded", embedded);
            log.info("Embedded {} commits", embedded);

            // Step 5: Embed code structure summaries
            result.put("step", "embedding_code");
            int codeEmbedded = 0;
            for (Map<String, Object> structure : codeStructure) {
                try {
                    String summary = buildCodeSummary(structure);
                    if (summary != null && !summary.isBlank()) {
                        float[] embedding = embeddingService.embed(summary);
                        vectorStoreService.upsert("code_knowledge", UUID.randomUUID().toString(), embedding,
                            Map.of(
                                "repository", clone.repoName(),
                                "owner", clone.owner(),
                                "type", "code_structure",
                                "file", (String) structure.getOrDefault("file", "unknown"),
                                "language", (String) structure.getOrDefault("language", "unknown"),
                                "summary", summary
                            ),
                            Map.of()
                        );
                        codeEmbedded++;
                    }
                } catch (Exception e) {
                    log.debug("Failed to embed code structure: {}", e.getMessage());
                }
            }
            result.put("codeFilesEmbedded", codeEmbedded);
            log.info("Embedded {} code file summaries", codeEmbedded);

            // Step 6: Create knowledge graph nodes
            result.put("step", "building_graph");
            int graphNodes = buildKnowledgeGraph(clone, bootstrap, codeStructure);
            result.put("graphNodesCreated", graphNodes);

            // Step 7: Store repository entity in PostgreSQL
            result.put("step", "persisting");
            String primaryLang = bootstrap.getLanguages().isEmpty() ? "Unknown" : bootstrap.getLanguages().get(0);
            RepositoryEntity repoEntity = RepositoryEntity.builder()
                .name(clone.repoName())
                .url(clone.remoteUrl())
                .path(clone.localPath())
                .defaultBranch(clone.defaultBranch())
                .primaryLanguage(primaryLang)
                .description(String.format("GitHub: %s/%s | Languages: %s | Frameworks: %s",
                    clone.owner(), clone.repoName(),
                    String.join(", ", bootstrap.getLanguages()),
                    String.join(", ", bootstrap.getFrameworks())))
                .build();

            if (projectId != null) {
                projectRepository.findById(projectId).ifPresent(repoEntity::setProject);
            }

            repoEntity = repositoryRepository.save(repoEntity);
            result.put("repositoryId", repoEntity.getId().toString());

            long elapsed = System.currentTimeMillis() - startTime;
            result.put("status", "ingested");
            result.put("elapsedMs", elapsed);
            result.put("step", "complete");

            log.info("Full ingestion complete for {} in {}ms", clone.repoName(), elapsed);
            return result;

        } catch (Exception e) {
            log.error("Ingestion pipeline failed: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
            result.put("step", result.get("step"));
            return result;
        }
    }

    private int buildKnowledgeGraph(GitHubCloneService.CloneResult clone,
            RepositoryBootstrapService.BootstrapResult bootstrap,
            List<Map<String, Object>> codeStructure) {

        int nodeCount = 0;
        String repoNodeId = clone.owner() + "/" + clone.repoName();

        // Repository node
        graphService.createNode("Repository", repoNodeId, Map.of(
            "name", clone.repoName(),
            "owner", clone.owner(),
            "url", clone.remoteUrl(),
            "branch", clone.defaultBranch(),
            "languages", String.join(", ", bootstrap.getLanguages()),
            "frameworks", String.join(", ", bootstrap.getFrameworks()),
            "databases", String.join(", ", bootstrap.getDatabases()),
            "path", clone.localPath()
        ));
        nodeCount++;

        // Technology nodes + relationships
        Set<String> allTechs = new LinkedHashSet<>();
        allTechs.addAll(bootstrap.getFrameworks());
        allTechs.addAll(bootstrap.getDatabases());
        allTechs.addAll(bootstrap.getPackageManagers());

        for (String tech : allTechs) {
            graphService.createNode("Technology", tech, Map.of("name", tech));
            graphService.createRelationship("Repository", repoNodeId, "Technology", tech, "USES", Map.of());
            nodeCount += 2;
        }

        // Language nodes
        for (String lang : bootstrap.getLanguages()) {
            graphService.createNode("Language", lang, Map.of("name", lang));
            graphService.createRelationship("Repository", repoNodeId, "Language", lang, "WRITTEN_IN", Map.of());
            nodeCount += 2;
        }

        // Code structure nodes (classes, functions, imports)
        for (Map<String, Object> file : codeStructure) {
            String filePath = (String) file.getOrDefault("file", "");
            String shortPath = filePath;
            if (filePath.contains(clone.repoName())) {
                shortPath = filePath.substring(filePath.indexOf(clone.repoName()) + clone.repoName().length() + 1);
            }

            // File node
            String fileId = repoNodeId + "::" + shortPath;
            graphService.createNode("File", fileId, Map.of(
                "path", shortPath,
                "language", file.getOrDefault("language", "unknown")
            ));
            graphService.createRelationship("Repository", repoNodeId, "File", fileId, "CONTAINS", Map.of());
            nodeCount += 2;

            // Class/interface nodes
            List<Map<String, Object>> classes = (List<Map<String, Object>>) file.getOrDefault("classes", List.of());
            for (Map<String, Object> cls : classes) {
                String className = (String) cls.getOrDefault("name", "unknown");
                String classId = repoNodeId + "::" + className;
                graphService.createNode("Class", classId, Map.of(
                    "name", className,
                    "type", cls.getOrDefault("type", "class"),
                    "file", shortPath
                ));
                graphService.createRelationship("File", fileId, "Class", classId, "DEFINES", Map.of());
                nodeCount += 2;

                // Extends relationships
                List<String> extendsList = (List<String>) cls.getOrDefault("extends", List.of());
                for (String parent : extendsList) {
                    String parentId = repoNodeId + "::" + parent.trim();
                    graphService.createRelationship("Class", classId, "Class", parentId, "EXTENDS", Map.of());
                    nodeCount++;
                }

                // Implements relationships
                List<String> implementsList = (List<String>) cls.getOrDefault("implements", List.of());
                for (String iface : implementsList) {
                    String ifaceId = repoNodeId + "::" + iface.trim();
                    graphService.createRelationship("Class", classId, "Class", ifaceId, "IMPLEMENTS", Map.of());
                    nodeCount++;
                }
            }

            // Function/method nodes
            List<Map<String, Object>> functions = (List<Map<String, Object>>) file.getOrDefault("functions", List.of());
            for (Map<String, Object> func : functions) {
                String funcName = (String) func.getOrDefault("name", "unknown");
                String funcId = repoNodeId + "::" + shortPath + "::" + funcName;
                graphService.createNode("Function", funcId, Map.of(
                    "name", funcName,
                    "file", shortPath,
                    "returnType", func.getOrDefault("returnType", "void"),
                    "parameters", String.valueOf(func.getOrDefault("parameters", ""))
                ));
                graphService.createRelationship("File", fileId, "Function", funcId, "DEFINES", Map.of());
                nodeCount += 2;
            }

            // Import/dependency nodes
            List<Map<String, Object>> imports = (List<Map<String, Object>>) file.getOrDefault("imports", List.of());
            for (Map<String, Object> imp : imports) {
                String moduleName = (String) imp.getOrDefault("module", "");
                if (moduleName != null && !moduleName.isBlank()) {
                    graphService.createRelationship("File", fileId, "Module", moduleName, "IMPORTS", Map.of());
                    nodeCount++;
                }
            }
        }

        log.info("Created {} graph nodes for {}/{}", nodeCount, clone.owner(), clone.repoName());
        return nodeCount;
    }

    private String buildCodeSummary(Map<String, Object> structure) {
        StringBuilder sb = new StringBuilder();
        String file = (String) structure.getOrDefault("file", "");
        String language = (String) structure.getOrDefault("language", "");
        sb.append("File: ").append(file).append(" (").append(language).append(")\n");

        List<Map<String, Object>> classes = (List<Map<String, Object>>) structure.getOrDefault("classes", List.of());
        for (Map<String, Object> cls : classes) {
            sb.append("  ").append(cls.getOrDefault("type", "class")).append(" ").append(cls.get("name"));
            List<String> extendsList = (List<String>) cls.getOrDefault("extends", List.of());
            if (!extendsList.isEmpty()) {
                sb.append(" extends ").append(String.join(", ", extendsList));
            }
            List<String> implList = (List<String>) cls.getOrDefault("implements", List.of());
            if (!implList.isEmpty()) {
                sb.append(" implements ").append(String.join(", ", implList));
            }
            sb.append("\n");

            List<Map<String, Object>> methods = (List<Map<String, Object>>) cls.getOrDefault("methods", List.of());
            for (Map<String, Object> m : methods) {
                sb.append("    ").append(m.getOrDefault("returnType", "void")).append(" ")
                    .append(m.get("name")).append("(")
                    .append(m.getOrDefault("parameters", "")).append(")\n");
            }

            List<Map<String, String>> fields = (List<Map<String, String>>) cls.getOrDefault("fields", List.of());
            for (Map<String, String> f : fields) {
                sb.append("    field: ").append(f.get("type")).append(" ").append(f.get("name")).append("\n");
            }
        }

        List<Map<String, Object>> functions = (List<Map<String, Object>>) structure.getOrDefault("functions", List.of());
        for (Map<String, Object> func : functions) {
            sb.append("  function ").append(func.get("name")).append("(")
                .append(func.getOrDefault("parameters", "")).append(")");
            if (func.containsKey("returnType")) {
                sb.append(" -> ").append(func.get("returnType"));
            }
            sb.append("\n");
        }

        List<Map<String, Object>> imports = (List<Map<String, Object>>) structure.getOrDefault("imports", List.of());
        if (!imports.isEmpty()) {
            sb.append("  imports: ");
            List<String> names = new ArrayList<>();
            for (Map<String, Object> imp : imports) {
                names.add((String) imp.getOrDefault("module", imp.getOrDefault("name", "")));
            }
            sb.append(String.join(", ", names)).append("\n");
        }

        return sb.toString();
    }
}
