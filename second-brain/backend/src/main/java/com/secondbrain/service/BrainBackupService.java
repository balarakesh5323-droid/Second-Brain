package com.secondbrain.service;

import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
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
        int restoredRepositories = 0;
        int restoredDecisions = 0;
        int restoredTasks = 0;
        int restoredMemories = 0;
        int restoredSkills = 0;
        int restoredTechnologies = 0;
        int restoredAttempts = 0;
        int restoredHandoffs = 0;

        if (snapshot == null || snapshot.isEmpty()) {
            stats.put("status", "error");
            stats.put("message", "Empty snapshot payload.");
            return stats;
        }

        // Map old IDs to restored Project and Repository entities
        Map<UUID, Project> projectMap = new HashMap<>();
        Map<UUID, RepositoryEntity> repoMap = new HashMap<>();

        // 1. Restore Projects (Idempotent by ID and Name)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) snapshot.getOrDefault("projects", List.of());
        for (Map<String, Object> p : projects) {
            String name = (String) p.get("name");
            UUID id = parseUUID(p.get("id"));
            if (name != null && !name.isBlank()) {
                Project proj = null;
                if (id != null && projectRepository.existsById(id)) {
                    proj = projectRepository.findById(id).orElse(null);
                } else if (projectRepository.findByName(name).isPresent()) {
                    proj = projectRepository.findByName(name).get();
                }

                if (proj == null) {
                    proj = Project.builder()
                            .name(name)
                            .description((String) p.get("description"))
                            .path((String) p.get("path"))
                            .status((String) p.getOrDefault("status", "active"))
                            .build();
                    if (id != null) proj.setId(id);
                    proj = projectRepository.save(proj);
                    restoredProjects++;
                }
                if (id != null) projectMap.put(id, proj);
            }
        }

        // 2. Restore Repositories (Idempotent by ID and Name)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> repos = (List<Map<String, Object>>) snapshot.getOrDefault("repositories", List.of());
        for (Map<String, Object> r : repos) {
            String name = (String) r.get("name");
            UUID id = parseUUID(r.get("id"));
            UUID projId = parseNestedId(r.get("project"), r.get("projectId"));

            if (name != null && !name.isBlank()) {
                RepositoryEntity repo = null;
                if (id != null && repositoryRepository.existsById(id)) {
                    repo = repositoryRepository.findById(id).orElse(null);
                } else if (repositoryRepository.findByName(name).isPresent()) {
                    repo = repositoryRepository.findByName(name).get();
                }

                if (repo == null) {
                    repo = RepositoryEntity.builder()
                            .name(name)
                            .url((String) r.get("url"))
                            .path((String) r.get("path"))
                            .description((String) r.get("description"))
                            .primaryLanguage((String) r.getOrDefault("primaryLanguage", "Java"))
                            .project(projId != null ? projectMap.get(projId) : null)
                            .build();
                    if (id != null) repo.setId(id);
                    repo = repositoryRepository.save(repo);
                    restoredRepositories++;
                }
                if (id != null) repoMap.put(id, repo);
            }
        }

        // 3. Restore Decisions (Idempotent by ID or Title)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) snapshot.getOrDefault("decisions", List.of());
        for (Map<String, Object> d : decisions) {
            String title = (String) d.get("title");
            UUID id = parseUUID(d.get("id"));
            UUID projId = parseNestedId(d.get("project"), d.get("projectId"));
            UUID repoId = parseNestedId(d.get("repository"), d.get("repositoryId"));

            if (title != null && !title.isBlank()) {
                if (id == null || !decisionRepository.existsById(id)) {
                    Decision dec = Decision.builder()
                            .title(title)
                            .description((String) d.get("description"))
                            .rationale((String) d.get("rationale"))
                            .status((String) d.getOrDefault("status", "ACCEPTED"))
                            .project(projId != null ? projectMap.get(projId) : null)
                            .repository(repoId != null ? repoMap.get(repoId) : null)
                            .build();
                    if (id != null) dec.setId(id);
                    decisionRepository.save(dec);
                    restoredDecisions++;
                }
            }
        }

        // 4. Restore Tasks (Idempotent by ID)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) snapshot.getOrDefault("tasks", List.of());
        for (Map<String, Object> t : tasks) {
            String title = (String) t.get("title");
            UUID id = parseUUID(t.get("id"));
            UUID projId = parseNestedId(t.get("project"), t.get("projectId"));
            UUID repoId = parseNestedId(t.get("repository"), t.get("repositoryId"));

            if (title != null && !title.isBlank()) {
                if (id == null || !taskRepository.existsById(id)) {
                    Task task = Task.builder()
                            .title(title)
                            .description((String) t.get("description"))
                            .status(TaskStatus.OPEN)
                            .priority(1)
                            .project(projId != null ? projectMap.get(projId) : null)
                            .repository(repoId != null ? repoMap.get(repoId) : null)
                            .build();
                    if (id != null) task.setId(id);
                    taskRepository.save(task);
                    restoredTasks++;
                }
            }
        }

        // 5. Restore Memories (Idempotent by ID)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> memories = (List<Map<String, Object>>) snapshot.getOrDefault("memories", List.of());
        for (Map<String, Object> m : memories) {
            String content = (String) m.get("content");
            UUID id = parseUUID(m.get("id"));
            UUID projId = parseNestedId(m.get("project"), m.get("projectId"));
            UUID repoId = parseNestedId(m.get("repository"), m.get("repositoryId"));

            if (content != null && !content.isBlank()) {
                if (id == null || !memoryRepository.existsById(id)) {
                    Memory mem = Memory.builder()
                            .content(content)
                            .type(MemoryType.valueOf((String) m.getOrDefault("type", "DECLARATIVE")))
                            .scope(MemoryScope.valueOf((String) m.getOrDefault("scope", "GLOBAL")))
                            .status(MemoryStatus.CONFIRMED)
                            .confidence(0.9)
                            .importance(0.8)
                            .project(projId != null ? projectMap.get(projId) : null)
                            .repository(repoId != null ? repoMap.get(repoId) : null)
                            .build();
                    if (id != null) mem.setId(id);
                    memoryRepository.save(mem);
                    restoredMemories++;
                }
            }
        }

        // 6. Restore Skills
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skills = (List<Map<String, Object>>) snapshot.getOrDefault("skills", List.of());
        for (Map<String, Object> s : skills) {
            String name = (String) s.get("name");
            UUID id = parseUUID(s.get("id"));
            if (name != null && !name.isBlank()) {
                if (id == null || !skillRepository.existsById(id)) {
                    Skill skill = Skill.builder()
                            .name(name)
                            .description((String) s.get("description"))
                            .version((String) s.getOrDefault("version", "1.0.0"))
                            .confidence(0.9)
                            .build();
                    if (id != null) skill.setId(id);
                    skillRepository.save(skill);
                    restoredSkills++;
                }
            }
        }

        // 7. Restore Technologies
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> techs = (List<Map<String, Object>>) snapshot.getOrDefault("technologies", List.of());
        for (Map<String, Object> tech : techs) {
            String name = (String) tech.get("name");
            UUID id = parseUUID(tech.get("id"));
            if (name != null && !name.isBlank()) {
                if (id == null || !technologyRepository.existsById(id)) {
                    Technology t = Technology.builder()
                            .name(name)
                            .category((String) tech.getOrDefault("category", "framework"))
                            .description((String) tech.get("description"))
                            .experienceLevel((String) tech.getOrDefault("experienceLevel", "EXPERT"))
                            .build();
                    if (id != null) t.setId(id);
                    technologyRepository.save(t);
                    restoredTechnologies++;
                }
            }
        }

        // 8. Restore Attempts
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attempts = (List<Map<String, Object>>) snapshot.getOrDefault("attempts", List.of());
        for (Map<String, Object> att : attempts) {
            String approach = (String) att.get("approach");
            UUID id = parseUUID(att.get("id"));
            UUID projId = parseNestedId(att.get("project"), att.get("projectId"));
            UUID repoId = parseNestedId(att.get("repository"), att.get("repositoryId"));

            if (approach != null && !approach.isBlank()) {
                if (id == null || !attemptRepository.existsById(id)) {
                    AgentAttempt a = AgentAttempt.builder()
                            .agentName((String) att.getOrDefault("agentName", "ai-agent"))
                            .taskDescription((String) att.getOrDefault("taskDescription", "Task trial"))
                            .approach(approach)
                            .status((String) att.getOrDefault("status", "SUCCESS"))
                            .errorMessage((String) att.get("errorMessage"))
                            .lessonLearned((String) att.get("lessonLearned"))
                            .project(projId != null ? projectMap.get(projId) : null)
                            .repository(repoId != null ? repoMap.get(repoId) : null)
                            .build();
                    if (id != null) a.setId(id);
                    attemptRepository.save(a);
                    restoredAttempts++;
                }
            }
        }

        // 9. Restore Handoffs
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> handoffs = (List<Map<String, Object>>) snapshot.getOrDefault("handoffs", List.of());
        for (Map<String, Object> h : handoffs) {
            String taskDesc = (String) h.get("task");
            UUID id = parseUUID(h.get("id"));
            UUID repoId = parseNestedId(h.get("repository"), h.get("repositoryId"));

            if (taskDesc != null && !taskDesc.isBlank()) {
                if (id == null || !handoffRepository.existsById(id)) {
                    AgentHandoff handoff = AgentHandoff.builder()
                            .task(taskDesc)
                            .completedItems((String) h.get("completedItems"))
                            .inProgressItems((String) h.get("inProgressItems"))
                            .blockedItems((String) h.get("blockedItems"))
                            .changedFiles((String) h.get("changedFiles"))
                            .nextSteps((String) h.get("nextSteps"))
                            .decisions((String) h.get("decisions"))
                            .knownIssues((String) h.get("knownIssues"))
                            .repository(repoId != null ? repoMap.get(repoId) : null)
                            .build();
                    if (id != null) handoff.setId(id);
                    handoffRepository.save(handoff);
                    restoredHandoffs++;
                }
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
        stats.put("restoredRepositories", restoredRepositories);
        stats.put("restoredDecisions", restoredDecisions);
        stats.put("restoredTasks", restoredTasks);
        stats.put("restoredMemories", restoredMemories);
        stats.put("restoredSkills", restoredSkills);
        stats.put("restoredTechnologies", restoredTechnologies);
        stats.put("restoredAttempts", restoredAttempts);
        stats.put("restoredHandoffs", restoredHandoffs);

        log.info("Successfully imported idempotent full snapshot: {} projects, {} repos, {} decisions, {} tasks, {} memories, {} attempts, {} handoffs",
                restoredProjects, restoredRepositories, restoredDecisions, restoredTasks, restoredMemories, restoredAttempts, restoredHandoffs);

        return stats;
    }

    private UUID parseUUID(Object val) {
        if (val == null) return null;
        try {
            return UUID.fromString(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private UUID parseNestedId(Object nestedObj, Object directId) {
        if (directId != null) {
            UUID parsed = parseUUID(directId);
            if (parsed != null) return parsed;
        }
        if (nestedObj instanceof Map<?, ?> map) {
            return parseUUID(map.get("id"));
        }
        return null;
    }
}
