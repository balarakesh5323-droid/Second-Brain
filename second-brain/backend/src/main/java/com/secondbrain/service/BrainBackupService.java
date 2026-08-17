package com.secondbrain.service;

import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.TaskStatus;
import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrainBackupService {

    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final DecisionRepository decisionRepository;
    private final AgentHandoffRepository handoffRepository;
    private final AgentAttemptRepository attemptRepository;
    private final MemoryRepository memoryRepository;
    private final TaskRepository taskRepository;
    private final TechnologyRepository technologyRepository;
    private final SkillRepository skillRepository;
    private final GraphService graphService;
    private final GraphSyncService graphSyncService;

    public Map<String, Object> exportSnapshot() {
        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("version", "1.0.0");
        backup.put("exportedAt", LocalDateTime.now().toString());

        backup.put("projects", projectRepository.findAll());
        backup.put("repositories", repositoryRepository.findAll());
        backup.put("decisions", decisionRepository.findAll());
        backup.put("handoffs", handoffRepository.findAll());
        backup.put("attempts", attemptRepository.findAll());
        backup.put("memories", memoryRepository.findAll());
        backup.put("tasks", taskRepository.findAll());
        backup.put("technologies", technologyRepository.findAll());
        backup.put("skills", skillRepository.findAll());

        // Export Neo4j Knowledge Graph
        try {
            backup.put("graph", graphService.getVisualGraph(2000));
        } catch (Exception e) {
            log.warn("Graph export fallback: {}", e.getMessage());
        }

        log.info("Successfully exported Second Brain backup snapshot");
        return backup;
    }

    @Transactional
    public Map<String, Object> importSnapshot(Map<String, Object> snapshot) {
        Map<String, Object> stats = new LinkedHashMap<>();
        int restoredProjects = 0;
        int restoredDecisions = 0;
        int restoredTasks = 0;

        if (snapshot == null || snapshot.isEmpty()) {
            stats.put("status", "error");
            stats.put("message", "Empty snapshot payload.");
            return stats;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) snapshot.getOrDefault("projects", List.of());
        for (Map<String, Object> p : projects) {
            String name = (String) p.get("name");
            if (name != null && !name.isBlank()) {
                if (projectRepository.findByName(name).isEmpty()) {
                    Project proj = Project.builder()
                            .name(name)
                            .description((String) p.get("description"))
                            .path((String) p.get("path"))
                            .status((String) p.getOrDefault("status", "active"))
                            .build();
                    projectRepository.save(proj);
                    restoredProjects++;
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) snapshot.getOrDefault("decisions", List.of());
        for (Map<String, Object> d : decisions) {
            String title = (String) d.get("title");
            if (title != null && !title.isBlank()) {
                Decision dec = Decision.builder()
                        .title(title)
                        .description((String) d.get("description"))
                        .rationale((String) d.get("rationale"))
                        .status("ACCEPTED")
                        .build();
                decisionRepository.save(dec);
                restoredDecisions++;
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) snapshot.getOrDefault("tasks", List.of());
        for (Map<String, Object> t : tasks) {
            String title = (String) t.get("title");
            if (title != null && !title.isBlank()) {
                Task task = Task.builder()
                        .title(title)
                        .description((String) t.get("description"))
                        .status(TaskStatus.OPEN)
                        .priority(1)
                        .build();
                taskRepository.save(task);
                restoredTasks++;
            }
        }

        // Re-sync all entities into Neo4j
        try {
            graphSyncService.syncAll();
        } catch (Exception e) {
            log.warn("Post-import graph sync: {}", e.getMessage());
        }

        stats.put("status", "success");
        stats.put("restoredProjects", restoredProjects);
        stats.put("restoredDecisions", restoredDecisions);
        stats.put("restoredTasks", restoredTasks);

        log.info("Successfully imported snapshot: {} projects, {} decisions, {} tasks",
                restoredProjects, restoredDecisions, restoredTasks);

        return stats;
    }
}
