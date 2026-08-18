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
    private final TaskRepository taskRepository;
    private final AgentOutboxRepository outboxRepository;
    private final GitService gitService;
    private final CandidateRetrievalService candidateRetrievalService;
    private final RelevanceScoringService relevanceScoringService;

    public Map<String, Object> assembleContextPack(String task, String repoIdOrPath, String projectId) {
        log.info("Assembling 1-Shot Context Pack for task: '{}', repo: '{}'", task, repoIdOrPath);

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("task", task != null ? task : "General engineering task");

        // 1. Resolve Primary Repository & Project
        RepositoryEntity primaryRepo = resolveRepository(repoIdOrPath);
        Project project = resolveProject(projectId, primaryRepo);

        UUID primaryRepoId = primaryRepo != null ? primaryRepo.getId() : null;
        UUID activeProjectId = project != null ? project.getId() : null;

        Map<String, Object> repoInfo = new LinkedHashMap<>();
        if (primaryRepo != null) {
            repoInfo.put("id", primaryRepo.getId().toString());
            repoInfo.put("name", primaryRepo.getName());
            repoInfo.put("path", primaryRepo.getPath());
            repoInfo.put("url", primaryRepo.getUrl());

            try {
                var gitStatus = gitService.getWorkingTreeStatus(primaryRepo.getPath());
                repoInfo.put("branch", gitService.getCurrentBranch(primaryRepo.getPath()));
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

        // 2. Multi-Repository Awareness: Sibling Repositories in Project
        List<Map<String, Object>> siblingRepos = new ArrayList<>();
        if (project != null) {
            List<RepositoryEntity> projectRepos = repositoryRepository.findByProjectId(project.getId());
            for (RepositoryEntity r : projectRepos) {
                if (primaryRepo != null && r.getId().equals(primaryRepo.getId())) {
                    continue; // Skip primary repo
                }
                Map<String, Object> sib = new LinkedHashMap<>();
                sib.put("id", r.getId().toString());
                sib.put("name", r.getName());
                sib.put("path", r.getPath());
                try {
                    var status = gitService.getWorkingTreeStatus(r.getPath());
                    sib.put("gitStatus", status.get("state"));
                    sib.put("branch", gitService.getCurrentBranch(r.getPath()));
                } catch (Exception ignored) {
                    sib.put("gitStatus", "UNKNOWN");
                }
                siblingRepos.add(sib);
            }
        }
        pack.put("siblingRepositories", siblingRepos);

        // 3. Active Sessions on this Repository
        List<Map<String, Object>> activeSessions = new ArrayList<>();
        if (primaryRepo != null) {
            var sessions = sessionRepository.findByRepositoryId(primaryRepo.getId());
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

        // 4. Latest Handoff
        Map<String, Object> latestHandoffMap = null;
        if (primaryRepo != null) {
            var handoffOpt = handoffRepository.findFirstByRepositoryIdOrderByCreatedAtDesc(primaryRepo.getId());
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

        Set<String> taskTokens = relevanceScoringService.extractTokens(task);

        // 5. Relevant Decisions (CandidateRetrievalService -> RelevanceScoringService -> Threshold Filter)
        List<Decision> rawDecisionCandidates = candidateRetrievalService.getDecisionCandidates(task, primaryRepoId, activeProjectId);
        List<RelevanceScoringService.ScoredCandidate<Decision>> decisionCandidates = new ArrayList<>();

        for (Decision d : rawDecisionCandidates) {
            String text = (d.getTitle() != null ? d.getTitle() : "") + " " + (d.getRationale() != null ? d.getRationale() : "");
            decisionCandidates.add(relevanceScoringService.scoreCandidate(
                    d, text, primaryRepoId, activeProjectId,
                    d.getRepository() != null ? d.getRepository().getId() : null,
                    d.getProject() != null ? d.getProject().getId() : null,
                    d.getRepository() != null ? d.getRepository().getName() : (primaryRepo != null ? primaryRepo.getName() : null),
                    d.getCreatedAt(), taskTokens
            ));
        }

        List<RelevanceScoringService.ScoredCandidate<Decision>> rankedDecisions = relevanceScoringService.rankAndFilter(
                decisionCandidates, RelevanceScoringService.DEFAULT_MIN_RELEVANCE, 5
        );

        List<Map<String, Object>> relevantDecisions = new ArrayList<>();
        for (var scored : rankedDecisions) {
            Decision d = scored.getItem();
            Map<String, Object> dMap = new LinkedHashMap<>();
            dMap.put("title", d.getTitle());
            dMap.put("rationale", d.getRationale());
            dMap.put("status", d.getStatus());
            dMap.put("scope", scored.getScope());
            if (scored.getRepositoryName() != null) {
                dMap.put("repositoryName", scored.getRepositoryName());
            }
            dMap.put("relevance", scored.getRelevance());
            dMap.put("reason", scored.getReason());
            dMap.put("createdAt", d.getCreatedAt());
            relevantDecisions.add(dMap);
        }
        pack.put("relevantDecisions", relevantDecisions);

        // 6. Relevant Failed Attempts (CandidateRetrievalService -> RelevanceScoringService -> Threshold Filter)
        List<AgentAttempt> rawFailureCandidates = candidateRetrievalService.getFailureCandidates(task, primaryRepoId, activeProjectId);
        List<RelevanceScoringService.ScoredCandidate<AgentAttempt>> failureCandidates = new ArrayList<>();

        for (AgentAttempt a : rawFailureCandidates) {
            String fullText = (a.getApproach() != null ? a.getApproach() : "") + " "
                    + (a.getErrorMessage() != null ? a.getErrorMessage() : "") + " "
                    + (a.getLessonLearned() != null ? a.getLessonLearned() : "") + " "
                    + (a.getTaskDescription() != null ? a.getTaskDescription() : "");

            failureCandidates.add(relevanceScoringService.scoreCandidate(
                    a, fullText, primaryRepoId, activeProjectId,
                    a.getRepository() != null ? a.getRepository().getId() : null,
                    a.getProject() != null ? a.getProject().getId() : null,
                    a.getRepository() != null ? a.getRepository().getName() : (primaryRepo != null ? primaryRepo.getName() : null),
                    a.getCreatedAt(), taskTokens
            ));
        }

        List<RelevanceScoringService.ScoredCandidate<AgentAttempt>> rankedFailures = relevanceScoringService.rankAndFilter(
                failureCandidates, RelevanceScoringService.DEFAULT_MIN_RELEVANCE, 5
        );

        List<Map<String, Object>> relevantFailures = new ArrayList<>();
        for (var scored : rankedFailures) {
            AgentAttempt a = scored.getItem();
            Map<String, Object> aMap = new LinkedHashMap<>();
            aMap.put("approach", a.getApproach());
            aMap.put("errorMessage", a.getErrorMessage());
            aMap.put("lessonLearned", a.getLessonLearned());
            aMap.put("agentName", a.getAgentName());
            aMap.put("scope", scored.getScope());
            if (scored.getRepositoryName() != null) {
                aMap.put("repositoryName", scored.getRepositoryName());
            }
            aMap.put("relevance", scored.getRelevance());
            aMap.put("reason", scored.getReason());
            aMap.put("createdAt", a.getCreatedAt());
            relevantFailures.add(aMap);
        }
        pack.put("relevantFailures", relevantFailures);

        // 7. Relevant Code Symbols (CandidateRetrievalService -> RelevanceScoringService -> Threshold Filter)
        List<SearchResult> rawSymbols = candidateRetrievalService.getSymbolCandidates(task, primaryRepoId, activeProjectId);
        List<RelevanceScoringService.ScoredCandidate<SearchResult>> scoredSymbols = new ArrayList<>();

        for (SearchResult sr : rawSymbols) {
            scoredSymbols.add(relevanceScoringService.scoreSymbolCandidate(
                    sr, primaryRepoId, activeProjectId,
                    primaryRepo != null ? primaryRepo.getName() : null,
                    taskTokens
            ));
        }

        List<RelevanceScoringService.ScoredCandidate<SearchResult>> rankedSymbols = relevanceScoringService.rankAndFilter(
                scoredSymbols, RelevanceScoringService.DEFAULT_MIN_RELEVANCE, 5
        );

        List<Map<String, Object>> relevantSymbols = new ArrayList<>();
        for (var scored : rankedSymbols) {
            SearchResult sr = scored.getItem();
            Map<String, Object> sym = new LinkedHashMap<>();
            sym.put("relevance", scored.getRelevance());
            sym.put("scope", scored.getScope());
            sym.put("reason", scored.getReason());
            if (sr.getPayload() != null) {
                sym.put("name", sr.getPayload().get("name"));
                sym.put("file", sr.getPayload().get("file"));
                sym.put("type", sr.getPayload().get("type"));
                sym.put("signature", sr.getPayload().get("signature"));
            }
            relevantSymbols.add(sym);
        }
        pack.put("relevantSymbols", relevantSymbols);

        // 8. Graph-RAG Structural Subgraph
        List<Map<String, Object>> relevantGraph = candidateRetrievalService.getGraphNeighborhood(primaryRepoId, 2);
        pack.put("relevantGraph", relevantGraph.stream().limit(10).toList());

        // 9. Open Tasks
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

        // 10. Automated Intelligent Warnings (with Multi-Repo Checks)
        List<String> warnings = new ArrayList<>();
        if (repoInfo.containsKey("gitStatus") && !"CLEAN".equalsIgnoreCase((String) repoInfo.get("gitStatus"))) {
            warnings.add("⚠️ Working tree has uncommitted modifications (" + repoInfo.get("modifiedFilesCount") + " modified files).");
        }
        if (!relevantFailures.isEmpty()) {
            Map<String, Object> topFail = relevantFailures.get(0);
            warnings.add("⚠️ Previous failed attempt (" + topFail.get("scope") + "): '" + topFail.get("approach") + "'. Lesson: " + topFail.get("lessonLearned"));
        }
        for (Map<String, Object> sib : siblingRepos) {
            if ("MODIFIED".equalsIgnoreCase((String) sib.get("gitStatus")) || "MIXED".equalsIgnoreCase((String) sib.get("gitStatus"))) {
                warnings.add("ℹ️ Sibling repository '" + sib.get("name") + "' has uncommitted changes in branch '" + sib.get("branch") + "'.");
            }
        }
        long pendingOutbox = outboxRepository.countByStatus(OutboxStatus.PENDING);
        if (pendingOutbox > 0) {
            warnings.add("ℹ️ Outbox projection queue has " + pendingOutbox + " pending projections.");
        }
        pack.put("warnings", warnings);

        // 11. Recommended Next Actions
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
