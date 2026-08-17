package com.secondbrain.service;

import com.secondbrain.common.entity.*;
import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphSyncService {

    private final GraphService graphService;
    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final AgentRepository agentRepository;
    private final TechnologyRepository technologyRepository;
    private final MemoryRepository memoryRepository;
    private final RepositoryIndexingService indexingService;

    @Transactional(readOnly = true)
    public void syncProjectToGraph(Project project) {
        if (project == null) return;
        String projId = project.getId().toString();
        log.info("Syncing project '{}' ({}) to knowledge graph...", project.getName(), projId);

        graphService.createNode("Project", projId, Map.of(
            "name", project.getName(),
            "description", project.getDescription() != null ? project.getDescription() : "",
            "path", project.getPath() != null ? project.getPath() : "",
            "status", project.getStatus() != null ? project.getStatus() : "active"
        ));

        // If project has local path on disk, scan files and build AST nodes
        if (project.getPath() != null && !project.getPath().isBlank()) {
            Path p = Paths.get(project.getPath());
            if (Files.exists(p)) {
                try {
                    List<Map<String, Object>> codeStructure = indexingService.analyzeCodeStructure(project.getPath());
                    log.info("Parsed {} files in project workspace {}", codeStructure.size(), project.getPath());

                    for (Map<String, Object> file : codeStructure) {
                        String filePath = (String) file.getOrDefault("file", "");
                        String shortPath = filePath;
                        try {
                            shortPath = p.relativize(Paths.get(filePath)).toString();
                        } catch (Exception ignored) {
                            if (filePath.contains(project.getName())) {
                                shortPath = filePath.substring(filePath.indexOf(project.getName()) + project.getName().length() + 1);
                            }
                        }

                        String fileId = projId + "::" + shortPath;
                        String fileName = Paths.get(filePath).getFileName().toString();
                        graphService.createNode("File", fileId, Map.of(
                            "name", fileName,
                            "path", shortPath,
                            "language", file.getOrDefault("language", "unknown")
                        ));
                        graphService.createRelationship("Project", projId, "File", fileId, "CONTAINS", null);

                        // Classes
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> classes = (List<Map<String, Object>>) file.getOrDefault("classes", List.of());
                        for (Map<String, Object> cls : classes) {
                            String clsName = (String) cls.getOrDefault("name", "unknown");
                            String clsId = fileId + "::" + clsName;
                            graphService.createNode("Class", clsId, Map.of(
                                "name", clsName,
                                "type", cls.getOrDefault("type", "class"),
                                "file", shortPath
                            ));
                            graphService.createRelationship("File", fileId, "Class", clsId, "DECLARES", null);
                        }

                        // Functions
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> functions = (List<Map<String, Object>>) file.getOrDefault("functions", List.of());
                        for (Map<String, Object> func : functions) {
                            String funcName = (String) func.getOrDefault("name", "unknown");
                            String funcId = fileId + "::" + funcName;
                            Map<String, Object> funcProps = new HashMap<>();
                            funcProps.put("name", funcName);
                            funcProps.put("file", shortPath);
                            funcProps.put("returnType", func.getOrDefault("returnType", "void"));
                            funcProps.put("parameters", String.valueOf(func.getOrDefault("parameters", "")));
                            graphService.createNode("Function", funcId, funcProps);
                            graphService.createRelationship("File", fileId, "Function", funcId, "DECLARES", null);
                        }

                        // Endpoints
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) file.getOrDefault("endpoints", List.of());
                        for (Map<String, Object> ep : endpoints) {
                            String epPath = (String) ep.getOrDefault("path", "");
                            String method = (String) ep.getOrDefault("httpMethod", "GET");
                            String epId = projId + "::" + method + " " + epPath;
                            graphService.createNode("Endpoint", epId, Map.of(
                                "path", epPath,
                                "method", method,
                                "handler", ep.getOrDefault("handlerMethod", "")
                            ));
                            graphService.createRelationship("Project", projId, "Endpoint", epId, "EXPOSES", null);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Workspace AST indexing failed for project {}: {}", project.getName(), e.getMessage());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public void syncRepositoryToGraph(RepositoryEntity repo) {
        if (repo == null) return;
        graphService.createNode("Repository", repo.getId().toString(), Map.of(
            "name", repo.getName(),
            "url", repo.getUrl() != null ? repo.getUrl() : "",
            "path", repo.getPath() != null ? repo.getPath() : "",
            "primaryLanguage", repo.getPrimaryLanguage() != null ? repo.getPrimaryLanguage() : ""
        ));
        if (repo.getProject() != null) {
            graphService.createRelationship("Repository", repo.getId().toString(),
                "Project", repo.getProject().getId().toString(), "BELONGS_TO", null);
            graphService.createRelationship("Project", repo.getProject().getId().toString(),
                "Repository", repo.getId().toString(), "HAS_REPO", null);
        }
    }

    @Transactional(readOnly = true)
    public void syncAgentToGraph(Agent agent) {
        if (agent == null) return;
        graphService.createNode("Agent", agent.getId().toString(), Map.of(
            "name", agent.getName(),
            "type", agent.getType() != null ? agent.getType() : "",
            "model", agent.getModel() != null ? agent.getModel() : ""
        ));
    }

    @Transactional(readOnly = true)
    public void syncTechnologyToGraph(Technology tech) {
        if (tech == null) return;
        graphService.createNode("Technology", tech.getId().toString(), Map.of(
            "name", tech.getName(),
            "category", tech.getCategory() != null ? tech.getCategory() : "",
            "version", tech.getVersion() != null ? tech.getVersion() : ""
        ));
    }

    @Transactional(readOnly = true)
    public void syncMemoryToGraph(Memory memory) {
        if (memory == null) return;
        graphService.createNode("Memory", memory.getId().toString(), Map.of(
            "content", memory.getContent(),
            "type", memory.getType().name(),
            "scope", memory.getScope().name(),
            "status", memory.getStatus().name(),
            "confidence", memory.getConfidence() != null ? memory.getConfidence() : 0.0
        ));
        if (memory.getProject() != null) {
            graphService.createRelationship("Memory", memory.getId().toString(),
                "Project", memory.getProject().getId().toString(), "ABOUT_PROJECT", null);
        }
        if (memory.getRepository() != null) {
            graphService.createRelationship("Memory", memory.getId().toString(),
                "Repository", memory.getRepository().getId().toString(), "ABOUT_REPOSITORY", null);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            syncAll();
        } catch (Exception e) {
            log.warn("Startup graph sync deferred or non-fatal: {}", e.getMessage());
        }
    }

    public void syncAll() {
        log.info("Starting full graph sync...");
        projectRepository.findAll().forEach(this::syncProjectToGraph);
        repositoryRepository.findAll().forEach(this::syncRepositoryToGraph);
        agentRepository.findAll().forEach(this::syncAgentToGraph);
        technologyRepository.findAll().forEach(this::syncTechnologyToGraph);
        log.info("Full graph sync completed");
    }
}
