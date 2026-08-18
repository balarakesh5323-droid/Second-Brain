package com.secondbrain.service;

import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.OutboxStatus;
import com.secondbrain.common.enums.TaskStatus;
import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContextPackService {

    private final RepositoryEntityRepository repositoryRepository;
    private final ProjectRepository projectRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentHandoffRepository handoffRepository;
    private final AgentAttemptRepository attemptRepository;
    private final DecisionRepository decisionRepository;
    private final TaskRepository taskRepository;
    private final AgentOutboxRepository outboxRepository;
    private final SemanticSearchService semanticSearchService;
    private final GraphService graphService;
    private final GitService gitService;

    public Map<String, Object> assembleContextPack(String task, String repoIdOrPath, String projectId) {
        log.info("Assembling 1-Shot Context Pack for task: '{}', repo: '{}'", task, repoIdOrPath);

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("task", task != null ? task : "General engineering task");

        // 1. Resolve Repository & Git Status
        RepositoryEntity repo = resolveRepository(repoIdOrPath);
        Project project = resolveProject(projectId, repo);

        Map<String, Object> repoInfo = new LinkedHashMap<>();
        if (repo != null) {
            repoInfo.put("id", repo.getId().toString());
            repoInfo.put("name", repo.getName());
            repoInfo.put("path", repo.getPath());
            repoInfo.put("url", repo.getUrl());

            try {
                var gitStatus = gitService.getWorkingTreeStatus(repo.getPath());
                repoInfo.put("branch", gitService.getCurrentBranch(repo.getPath()));
                repoInfo.put("gitStatus", gitStatus.get("state"));
                repoInfo.put("modifiedFilesCount", gitStatus.get("modifiedCount"));
            } catch (Exception e) {
                repoInfo.put("gitStatus", "UNKNOWN");
            }
        }
        pack.put("repository", repoInfo);

        Map<String, Object> projInfo = new LinkedHashMap<>();
        if (project != null) {
            projInfo.put("id", project.getId().toString());
            projInfo.put("name", project.getName());
            projInfo.put("description", project.getDescription());
        }
        pack.put("project", projInfo);

        // 2. Active Sessions on this Repository
        List<Map<String, Object>> activeSessions = new ArrayList<>();
        if (repo != null) {
            var sessions = sessionRepository.findByRepositoryId(repo.getId());
            for (var s : sessions) {
                if ("IN_PROGRESS".equalsIgnoreCase(s.getStatus()) || "active".equalsIgnoreCase(s.getStatus())) {
                    Map<String, Object> sMap = new LinkedHashMap<>();
                    sMap.put("sessionId", s.getId().toString());
                    sMap.put("agentName", s.getAgent() != null ? s.getAgent().getName() : "Unknown");
                    sMap.put("task", s.getTask());
                    sMap.put("startedAt", s.getStartedAt());
                    activeSessions.add(sMap);
                }
            }
        }
        pack.put("activeSessions", activeSessions);

        // 3. Latest Handoff
        Map<String, Object> latestHandoffMap = null;
        if (repo != null) {
            var handoffOpt = handoffRepository.findFirstByRepositoryIdOrderByCreatedAtDesc(repo.getId());
            if (handoffOpt.isPresent()) {
                AgentHandoff h = handoffOpt.get();
                latestHandoffMap = new LinkedHashMap<>();
                latestHandoffMap.put("id", h.getId().toString());
                latestHandoffMap.put("fromAgent", h.getAgent() != null ? h.getAgent().getName() : "Previous Agent");
                latestHandoffMap.put("task", h.getTask());
                latestHandoffMap.put("completedItems", h.getCompletedItems());
                latestHandoffMap.put("inProgressItems", h.getInProgressItems());
                latestHandoffMap.put("blockedItems", h.getBlockedItems());
                latestHandoffMap.put("nextSteps", h.getNextSteps());
                latestHandoffMap.put("createdAt", h.getCreatedAt());
            }
        }
        pack.put("latestHandoff", latestHandoffMap);

        // 4. Relevant Decisions
        List<Map<String, Object>> relevantDecisions = new ArrayList<>();
        if (repo != null) {
            var decisions = decisionRepository.findByRepositoryId(repo.getId());
            for (var d : decisions) {
                Map<String, Object> dMap = new LinkedHashMap<>();
                dMap.put("title", d.getTitle());
                dMap.put("rationale", d.getRationale());
                dMap.put("status", d.getStatus());
                dMap.put("createdAt", d.getCreatedAt());
                relevantDecisions.add(dMap);
                if (relevantDecisions.size() >= 5) break;
            }
        }
        pack.put("relevantDecisions", relevantDecisions);

        // 5. Relevant Failed & Successful Attempts (Failure Avoidance)
        List<Map<String, Object>> relevantFailures = new ArrayList<>();
        if (repo != null) {
            var attempts = attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repo.getId());
            for (var a : attempts) {
                if ("FAILED".equalsIgnoreCase(a.getStatus()) || "FAILURE".equalsIgnoreCase(a.getStatus())) {
                    Map<String, Object> aMap = new LinkedHashMap<>();
                    aMap.put("approach", a.getApproach());
                    aMap.put("errorMessage", a.getErrorMessage());
                    aMap.put("lessonLearned", a.getLessonLearned());
                    aMap.put("agentName", a.getAgentName());
                    aMap.put("createdAt", a.getCreatedAt());
                    relevantFailures.add(aMap);
                }
            }
        }
        pack.put("relevantFailures", relevantFailures);

        // 6. Relevant Code Symbols (Vector search on task)
        List<Map<String, Object>> relevantSymbols = new ArrayList<>();
        if (task != null && !task.isBlank()) {
            try {
                String repoScope = repo != null ? repo.getId().toString() : null;
                List<SearchResult> symbolResults = semanticSearchService.searchScoped(task, "symbol_knowledge", null, repoScope, 5);
                for (SearchResult sr : symbolResults) {
                    Map<String, Object> sym = new LinkedHashMap<>();
                    sym.put("score", sr.getScore());
                    if (sr.getPayload() != null) {
                        sym.put("name", sr.getPayload().get("name"));
                        sym.put("file", sr.getPayload().get("file"));
                        sym.put("type", sr.getPayload().get("type"));
                        sym.put("signature", sr.getPayload().get("signature"));
                    }
                    relevantSymbols.add(sym);
                }
            } catch (Exception e) {
                log.warn("Failed searching symbols for context pack: {}", e.getMessage());
            }
        }
        pack.put("relevantSymbols", relevantSymbols);

        // 7. Graph-RAG Structural Subgraph
        List<Map<String, Object>> relevantGraph = new ArrayList<>();
        try {
            if (repo != null) {
                relevantGraph = graphService.findRelated("Repository", repo.getId().toString(), null, 2);
            }
        } catch (Exception e) {
            log.warn("Failed fetching graph context: {}", e.getMessage());
        }
        pack.put("relevantGraph", relevantGraph.stream().limit(10).toList());

        // 8. Open Tasks
        List<Map<String, Object>> openTasks = new ArrayList<>();
        if (project != null) {
            var tasks = taskRepository.findByStatusAndProjectId(TaskStatus.OPEN, project.getId());
            for (var t : tasks) {
                openTasks.add(Map.of(
                        "id", t.getId().toString(),
                        "title", t.getTitle(),
                        "priority", t.getPriority() != null ? t.getPriority() : 3
                ));
            }
        }
        pack.put("openTasks", openTasks);

        // 9. Automated Intelligent Warnings
        List<String> warnings = new ArrayList<>();
        if (repoInfo.containsKey("gitStatus") && !"CLEAN".equalsIgnoreCase((String) repoInfo.get("gitStatus"))) {
            warnings.add("⚠️ Working tree has uncommitted modifications (" + repoInfo.get("modifiedFilesCount") + " modified files).");
        }
        if (!relevantFailures.isEmpty()) {
            Map<String, Object> latestFail = relevantFailures.get(0);
            warnings.add("⚠️ Previous failed attempt: '" + latestFail.get("approach") + "'. Lesson: " + latestFail.get("lessonLearned"));
        }
        long pendingOutbox = outboxRepository.countByStatus(OutboxStatus.PENDING);
        if (pendingOutbox > 0) {
            warnings.add("ℹ️ Outbox projection queue has " + pendingOutbox + " pending projections.");
        }
        pack.put("warnings", warnings);

        // 10. Recommended Next Actions
        List<String> recommendations = new ArrayList<>();
        if (latestHandoffMap != null && latestHandoffMap.get("nextSteps") != null && !((String) latestHandoffMap.get("nextSteps")).isBlank()) {
            recommendations.add("Review latest handoff nextSteps: " + latestHandoffMap.get("nextSteps"));
        }
        if (!openTasks.isEmpty()) {
            recommendations.add("Check open task: " + openTasks.get(0).get("title"));
        }
        recommendations.add("Call brain_start_session before modifying architectural components.");
        pack.put("recommendedNextActions", recommendations);

        return pack;
    }

    private RepositoryEntity resolveRepository(String repoIdOrPath) {
        if (repoIdOrPath == null || repoIdOrPath.isBlank()) {
            return repositoryRepository.findAll().stream().findFirst().orElse(null);
        }
        try {
            UUID id = UUID.fromString(repoIdOrPath);
            return repositoryRepository.findById(id).orElse(null);
        } catch (IllegalArgumentException e) {
            return repositoryRepository.findByName(repoIdOrPath)
                    .or(() -> repositoryRepository.findByPath(repoIdOrPath))
                    .orElse(null);
        }
    }

    private Project resolveProject(String projectId, RepositoryEntity repo) {
        if (projectId != null && !projectId.isBlank()) {
            try {
                return projectRepository.findById(UUID.fromString(projectId)).orElse(null);
            } catch (Exception ignored) {
                return projectRepository.findByName(projectId).orElse(null);
            }
        }
        if (repo != null && repo.getProject() != null) {
            return repo.getProject();
        }
        return projectRepository.findAll().stream().findFirst().orElse(null);
    }
}
