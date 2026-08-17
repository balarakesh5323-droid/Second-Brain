package com.secondbrain.service;

import com.secondbrain.common.entity.Project;
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

            // Step 6: Store Project and Repository in PostgreSQL
            result.put("step", "persisting");
            String primaryLang = bootstrap.getLanguages().isEmpty() ? "Unknown" : bootstrap.getLanguages().get(0);

            // Guarantee project exists where project name == repository name
            Project project = null;
            if (projectId != null) {
                project = projectRepository.findById(projectId).orElse(null);
            }
            if (project == null) {
                project = projectRepository.findByName(clone.repoName()).orElse(null);
            }
            if (project == null) {
                project = Project.builder()
                    .name(clone.repoName())
                    .description(String.format("GitHub: %s/%s | %s",
                        clone.owner(), clone.repoName(), clone.remoteUrl()))
                    .path(clone.localPath())
                    .build();
                project = projectRepository.save(project);
                log.info("Created project for repository: {} ({})", project.getName(), project.getId());
            } else {
                project.setPath(clone.localPath());
                project = projectRepository.save(project);
            }
            result.put("projectId", project.getId().toString());
            result.put("projectName", project.getName());

            // Check if repo already exists by URL or name, else create new
            RepositoryEntity repoEntity = repositoryRepository.findByUrl(clone.remoteUrl())
                .or(() -> repositoryRepository.findByName(clone.repoName()))
                .orElseGet(() -> RepositoryEntity.builder().name(clone.repoName()).build());

            repoEntity.setName(clone.repoName());
            repoEntity.setUrl(clone.remoteUrl());
            repoEntity.setPath(clone.localPath());
            repoEntity.setDefaultBranch(clone.defaultBranch());
            repoEntity.setPrimaryLanguage(primaryLang);
            repoEntity.setDescription(String.format("GitHub: %s/%s | Languages: %s | Frameworks: %s",
                clone.owner(), clone.repoName(),
                String.join(", ", bootstrap.getLanguages()),
                String.join(", ", bootstrap.getFrameworks())));
            repoEntity.setProject(project);

            repoEntity = repositoryRepository.save(repoEntity);
            result.put("repositoryId", repoEntity.getId().toString());

            // Step 7: Create knowledge graph nodes and project linkage
            result.put("step", "building_graph");
            try {
                int graphNodes = buildKnowledgeGraph(clone, bootstrap, codeStructure, project);
                result.put("graphNodesCreated", graphNodes);
            } catch (Exception e) {
                log.warn("Graph building failed (non-fatal): {}", e.getMessage());
                result.put("graphNodesCreated", 0);
                result.put("graphError", e.getMessage());
            }

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

    @Transactional
    public Map<String, Object> syncRepository(UUID repositoryId) {
        Optional<RepositoryEntity> repoOpt = repositoryRepository.findById(repositoryId);
        if (repoOpt.isEmpty()) {
            return Map.of("status", "failed", "error", "Repository not found: " + repositoryId);
        }
        RepositoryEntity repo = repoOpt.get();
        UUID projectId = repo.getProject() != null ? repo.getProject().getId() : null;
        return ingestFromUrl(repo.getUrl(), projectId);
    }

    @Transactional
    public Map<String, Object> syncRepositoryByUrl(String url) {
        Optional<RepositoryEntity> repoOpt = repositoryRepository.findByUrl(url);
        UUID projectId = repoOpt.map(r -> r.getProject() != null ? r.getProject().getId() : null).orElse(null);
        return ingestFromUrl(url, projectId);
    }

    private int buildKnowledgeGraph(GitHubCloneService.CloneResult clone,
            RepositoryBootstrapService.BootstrapResult bootstrap,
            List<Map<String, Object>> codeStructure,
            Project project) {

        int nodeCount = 0;
        String repoNodeId = clone.owner() + "/" + clone.repoName();
        String projNodeId = project != null ? project.getName() : clone.repoName();

        // Batch 0: Project node and relationship
        if (project != null) {
            graphService.batchCreateNodes("Project", List.of(
                Map.of("id", projNodeId, "props", Map.of(
                    "name", project.getName(),
                    "description", project.getDescription() != null ? project.getDescription() : "",
                    "path", project.getPath() != null ? project.getPath() : ""
                ))
            ));
            graphService.batchCreateRelationshipsTyped("BELONGS_TO", List.of(
                Map.of("fromId", repoNodeId, "toId", projNodeId, "props", Map.of())
            ));
            nodeCount += 2;
        }

        // Batch 1: Repository node
        graphService.batchCreateNodes("Repository", List.of(
            Map.of("id", repoNodeId, "props", Map.of(
                "name", clone.repoName(),
                "owner", clone.owner(),
                "url", clone.remoteUrl(),
                "branch", clone.defaultBranch(),
                "languages", String.join(", ", bootstrap.getLanguages()),
                "frameworks", String.join(", ", bootstrap.getFrameworks()),
                "databases", String.join(", ", bootstrap.getDatabases()),
                "path", clone.localPath()
            ))
        ));
        nodeCount++;

        // Batch 2: Technology nodes
        Set<String> allTechs = new LinkedHashSet<>();
        allTechs.addAll(bootstrap.getFrameworks());
        allTechs.addAll(bootstrap.getDatabases());
        allTechs.addAll(bootstrap.getPackageManagers());

        List<Map<String, Object>> techNodes = new ArrayList<>();
        List<Map<String, Object>> techRels = new ArrayList<>();
        for (String tech : allTechs) {
            techNodes.add(Map.of("id", tech, "props", Map.of("name", tech)));
            techRels.add(Map.of("fromId", repoNodeId, "toId", tech, "props", Map.of()));
        }
        graphService.batchCreateNodes("Technology", techNodes);
        graphService.batchCreateRelationshipsTyped("USES", techRels);
        nodeCount += techNodes.size() * 2;

        // Batch 3: Language nodes
        List<Map<String, Object>> langNodes = new ArrayList<>();
        List<Map<String, Object>> langRels = new ArrayList<>();
        for (String lang : bootstrap.getLanguages()) {
            langNodes.add(Map.of("id", lang, "props", Map.of("name", lang)));
            langRels.add(Map.of("fromId", repoNodeId, "toId", lang, "props", Map.of()));
        }
        graphService.batchCreateNodes("Language", langNodes);
        graphService.batchCreateRelationshipsTyped("WRITTEN_IN", langRels);
        nodeCount += langNodes.size() * 2;

        // Batch 4: Code structure - files, classes, functions
        List<Map<String, Object>> fileNodes = new ArrayList<>();
        List<Map<String, Object>> fileRels = new ArrayList<>();
        List<Map<String, Object>> classNodes = new ArrayList<>();
        List<Map<String, Object>> classRels = new ArrayList<>();
        List<Map<String, Object>> funcNodes = new ArrayList<>();
        List<Map<String, Object>> funcRels = new ArrayList<>();

        for (Map<String, Object> file : codeStructure) {
            String filePath = (String) file.getOrDefault("file", "");
            String shortPath = filePath;
            if (filePath.contains(clone.repoName())) {
                shortPath = filePath.substring(filePath.indexOf(clone.repoName()) + clone.repoName().length() + 1);
            }

            String fileId = repoNodeId + "::" + shortPath;
            fileNodes.add(Map.of("id", fileId, "props", Map.of(
                "path", shortPath,
                "language", file.getOrDefault("language", "unknown")
            )));
            fileRels.add(Map.of("fromId", repoNodeId, "toId", fileId, "props", Map.of()));

            List<Map<String, Object>> classes = (List<Map<String, Object>>) file.getOrDefault("classes", List.of());
            for (Map<String, Object> cls : classes) {
                String className = (String) cls.getOrDefault("name", "unknown");
                String classId = repoNodeId + "::" + className;
                classNodes.add(Map.of("id", classId, "props", Map.of(
                    "name", className,
                    "type", cls.getOrDefault("type", "class"),
                    "file", shortPath
                )));
                classRels.add(Map.of("fromId", fileId, "toId", classId, "props", Map.of()));

                List<String> extendsList = (List<String>) cls.getOrDefault("extends", List.of());
                for (String parent : extendsList) {
                    classRels.add(Map.of("fromId", classId, "toId", repoNodeId + "::" + parent.trim(), "props", Map.of()));
                }
                List<String> implementsList = (List<String>) cls.getOrDefault("implements", List.of());
                for (String iface : implementsList) {
                    classRels.add(Map.of("fromId", classId, "toId", repoNodeId + "::" + iface.trim(), "props", Map.of()));
                }
            }

            List<Map<String, Object>> functions = (List<Map<String, Object>>) file.getOrDefault("functions", List.of());
            for (Map<String, Object> func : functions) {
                String funcName = (String) func.getOrDefault("name", "unknown");
                String funcId = repoNodeId + "::" + shortPath + "::" + funcName;
                funcNodes.add(Map.of("id", funcId, "props", Map.of(
                    "name", funcName,
                    "file", shortPath,
                    "returnType", func.getOrDefault("returnType", "void"),
                    "parameters", String.valueOf(func.getOrDefault("parameters", ""))
                )));
                funcRels.add(Map.of("fromId", fileId, "toId", funcId, "props", Map.of()));
            }
        }

        graphService.batchCreateNodes("File", fileNodes);
        graphService.batchCreateRelationshipsTyped("CONTAINS", fileRels);
        graphService.batchCreateNodes("Class", classNodes);
        graphService.batchCreateRelationshipsTyped("DEFINES", classRels);
        graphService.batchCreateNodes("Function", funcNodes);
        graphService.batchCreateRelationshipsTyped("DEFINES", funcRels);
        nodeCount += (fileNodes.size() + classNodes.size() + funcNodes.size()) * 2;

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
