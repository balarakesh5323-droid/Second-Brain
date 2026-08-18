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

        Set<String> taskKeywords = extractKeywords(task);

        // 4. Relevant Decisions (Task-aware relevance ranked)
        List<Map<String, Object>> relevantDecisions = new ArrayList<>();
        if (repo != null || project != null) {
            List<Decision> allDecisions = repo != null 
                    ? decisionRepository.findByRepositoryId(repo.getId())
                    : (project != null ? decisionRepository.findByProjectId(project.getId()) : List.of());

            List<ScoredItem<Decision>> scoredDecisions = new ArrayList<>();
            for (var d : allDecisions) {
                String fullText = (d.getTitle() != null ? d.getTitle() : "") + " " + (d.getRationale() != null ? d.getRationale() : "");
                double score = computeRelevanceScore(fullText, taskKeywords, 0.70);
                List<String> matched = findMatchedKeywords(fullText, taskKeywords);
                String reason = matched.isEmpty()
                        ? "Recent architectural decision in repository"
                        : "Matches task keywords [" + String.join(", ", matched) + "] in repository";
                scoredDecisions.add(new ScoredItem<>(d, score, reason, matched));
            }

            scoredDecisions.sort((a, b) -> Double.compare(b.score, a.score));

            for (var item : scoredDecisions) {
                Decision d = item.item;
                Map<String, Object> dMap = new LinkedHashMap<>();
                dMap.put("title", d.getTitle());
                dMap.put("rationale", d.getRationale());
                dMap.put("status", d.getStatus());
                dMap.put("relevance", Math.round(item.score * 100.0) / 100.0);
                dMap.put("reason", item.reason);
                dMap.put("createdAt", d.getCreatedAt());
                relevantDecisions.add(dMap);
                if (relevantDecisions.size() >= 5) break;
            }
        }
        pack.put("relevantDecisions", relevantDecisions);

        // 5. Relevant Failed & Successful Attempts (Failure Avoidance & Task-aware relevance ranked)
        List<Map<String, Object>> relevantFailures = new ArrayList<>();
        if (repo != null || project != null) {
            List<AgentAttempt> allAttempts = repo != null
                    ? attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repo.getId())
                    : (project != null ? attemptRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()) : List.of());

            List<ScoredItem<AgentAttempt>> scoredFailures = new ArrayList<>();
            for (var a : allAttempts) {
                if ("FAILED".equalsIgnoreCase(a.getStatus()) || "FAILURE".equalsIgnoreCase(a.getStatus())) {
                    String fullText = (a.getApproach() != null ? a.getApproach() : "") + " "
                            + (a.getErrorMessage() != null ? a.getErrorMessage() : "") + " "
                            + (a.getLessonLearned() != null ? a.getLessonLearned() : "") + " "
                            + (a.getTaskDescription() != null ? a.getTaskDescription() : "");
                    double score = computeRelevanceScore(fullText, taskKeywords, 0.75);
                    List<String> matched = findMatchedKeywords(fullText, taskKeywords);
                    String reason = matched.isEmpty()
                            ? "Prior failed trial in repository"
                            : "Prior failure matching task technologies [" + String.join(", ", matched) + "]";
                    scoredFailures.add(new ScoredItem<>(a, score, reason, matched));
                }
            }

            scoredFailures.sort((a, b) -> Double.compare(b.score, a.score));

            for (var item : scoredFailures) {
                AgentAttempt a = item.item;
                Map<String, Object> aMap = new LinkedHashMap<>();
                aMap.put("approach", a.getApproach());
                aMap.put("errorMessage", a.getErrorMessage());
                aMap.put("lessonLearned", a.getLessonLearned());
                aMap.put("agentName", a.getAgentName());
                aMap.put("relevance", Math.round(item.score * 100.0) / 100.0);
                aMap.put("reason", item.reason);
                aMap.put("createdAt", a.getCreatedAt());
                relevantFailures.add(aMap);
                if (relevantFailures.size() >= 5) break;
            }
        }
        pack.put("relevantFailures", relevantFailures);

        // 6. Relevant Code Symbols (Vector search on task with semantic score & reason)
        List<Map<String, Object>> relevantSymbols = new ArrayList<>();
        if (task != null && !task.isBlank()) {
            try {
                String repoScope = repo != null ? repo.getId().toString() : null;
                List<SearchResult> symbolResults = semanticSearchService.searchScoped(task, "symbol_knowledge", null, repoScope, 5);
                for (SearchResult sr : symbolResults) {
                    Map<String, Object> sym = new LinkedHashMap<>();
                    sym.put("relevance", Math.round(sr.getScore() * 100.0) / 100.0);
                    sym.put("reason", "Semantic vector match for task in repository symbols");
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

    private Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> stopWords = Set.of(
                "a", "an", "the", "in", "on", "at", "to", "for", "of", "and", "or", "is", "are", "with", "by", "from", "as"
        );
        Set<String> keywords = new HashSet<>();
        String[] tokens = text.toLowerCase().split("[^a-zA-Z0-9_-]+");
        for (String t : tokens) {
            if (t.length() > 2 && !stopWords.contains(t)) {
                keywords.add(t);
            }
        }
        return keywords;
    }

    private double computeRelevanceScore(String text, Set<String> taskKeywords, double baseScore) {
        if (text == null || taskKeywords.isEmpty()) return baseScore;
        String lower = text.toLowerCase();
        int matches = 0;
        for (String kw : taskKeywords) {
            if (lower.contains(kw)) {
                matches++;
            }
        }
        if (matches == 0) return baseScore;
        double boost = Math.min(0.25, matches * 0.08);
        return Math.min(0.98, baseScore + boost);
    }

    private List<String> findMatchedKeywords(String text, Set<String> taskKeywords) {
        if (text == null || taskKeywords.isEmpty()) return List.of();
        String lower = text.toLowerCase();
        List<String> matched = new ArrayList<>();
        for (String kw : taskKeywords) {
            if (lower.contains(kw)) {
                matched.add(kw);
            }
        }
        return matched;
    }

    private record ScoredItem<T>(T item, double score, String reason, List<String> matchedKeywords) {}
}
