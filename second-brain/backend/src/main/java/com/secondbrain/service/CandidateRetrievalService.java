package com.secondbrain.service;

import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.AgentAttempt;
import com.secondbrain.common.entity.Decision;
import com.secondbrain.common.repository.AgentAttemptRepository;
import com.secondbrain.common.repository.DecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateRetrievalService {

    // Dedicated Independent Quotas
    public static final int RECENT_REPO_QUOTA = 20;
    public static final int RECENT_PROJECT_QUOTA = 10;
    public static final int SEMANTIC_REPO_QUOTA = 15;
    public static final int SEMANTIC_PROJECT_QUOTA = 15;

    public static final int MAX_DECISION_BUDGET = 60;
    public static final int MAX_FAILURE_BUDGET = 60;
    public static final int MAX_SYMBOL_BUDGET = 30;

    public static final float SYMBOL_QUALITY_FALLBACK_THRESHOLD = 0.65f;

    private final DecisionRepository decisionRepository;
    private final AgentAttemptRepository attemptRepository;
    private final SemanticSearchService semanticSearchService;
    private final GraphService graphService;

    /**
     * Hybrid Candidate Retrieval for Decisions (v2):
     * Guarantees independent quotas for recent temporal decisions and historical semantic vector hits.
     */
    public List<Decision> getDecisionCandidates(String task, UUID repoId, UUID projectId) {
        Map<UUID, Decision> candidateMap = new LinkedHashMap<>();
        String repoStr = repoId != null ? repoId.toString() : null;
        String projStr = projectId != null ? projectId.toString() : null;

        // 1. Recent Stream (PostgreSQL: Repo & Project)
        if (repoId != null) {
            decisionRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId, PageRequest.of(0, RECENT_REPO_QUOTA))
                    .forEach(d -> candidateMap.put(d.getId(), d));
        }
        if (projectId != null) {
            decisionRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, RECENT_PROJECT_QUOTA))
                    .forEach(d -> candidateMap.putIfAbsent(d.getId(), d));
        }

        // 2. Semantic Stream (Qdrant Vector Store: Repo & Project Scope-Aware)
        if (task != null && !task.isBlank()) {
            try {
                // Tier A: Repository-scoped semantic search
                if (repoStr != null) {
                    List<SearchResult> repoMatches = semanticSearchService.searchScoped(
                            task, "technical_memory", projStr, repoStr, SEMANTIC_REPO_QUOTA
                    );
                    addDecisionMatches(repoMatches, candidateMap);
                }

                // Tier B: Project-wide semantic search (discovers sibling repo decisions)
                if (projStr != null) {
                    List<SearchResult> projMatches = semanticSearchService.searchScoped(
                            task, "technical_memory", projStr, null, SEMANTIC_PROJECT_QUOTA
                    );
                    addDecisionMatches(projMatches, candidateMap);
                }
            } catch (Exception e) {
                log.debug("Semantic decision retrieval skipped: {}", e.getMessage());
            }
        }

        return new ArrayList<>(candidateMap.values());
    }

    /**
     * Hybrid Candidate Retrieval for Failed Attempts (v2):
     * Guarantees independent quotas for recent failures and historical semantic failure records.
     */
    public List<AgentAttempt> getFailureCandidates(String task, UUID repoId, UUID projectId) {
        Map<UUID, AgentAttempt> candidateMap = new LinkedHashMap<>();
        String repoStr = repoId != null ? repoId.toString() : null;
        String projStr = projectId != null ? projectId.toString() : null;

        // 1. Recent Stream (PostgreSQL)
        if (repoId != null) {
            attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId, PageRequest.of(0, RECENT_REPO_QUOTA))
                    .forEach(a -> {
                        if (isFailure(a)) candidateMap.put(a.getId(), a);
                    });
        }
        if (projectId != null) {
            attemptRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, RECENT_PROJECT_QUOTA))
                    .forEach(a -> {
                        if (isFailure(a)) candidateMap.putIfAbsent(a.getId(), a);
                    });
        }

        // 2. Semantic Stream (Qdrant Vector Store: Repo & Project-wide)
        if (task != null && !task.isBlank()) {
            try {
                // Tier A: Repository-scoped failure search
                if (repoStr != null) {
                    List<SearchResult> repoMatches = semanticSearchService.searchScoped(
                            task, "agent_memory", projStr, repoStr, SEMANTIC_REPO_QUOTA
                    );
                    addFailureMatches(repoMatches, candidateMap);
                }

                // Tier B: Project-wide failure search (cross-repo failure intelligence)
                if (projStr != null) {
                    List<SearchResult> projMatches = semanticSearchService.searchScoped(
                            task, "agent_memory", projStr, null, SEMANTIC_PROJECT_QUOTA
                    );
                    addFailureMatches(projMatches, candidateMap);
                }
            } catch (Exception e) {
                log.debug("Semantic failure retrieval skipped: {}", e.getMessage());
            }
        }

        return new ArrayList<>(candidateMap.values());
    }

    /**
     * Semantic Candidate Retrieval for Code Symbols (v2 with Quality-Based Fallback)
     */
    public List<SearchResult> getSymbolCandidates(String task, UUID repoId, UUID projectId) {
        if (task == null || task.isBlank()) return List.of();

        Map<String, SearchResult> symbolMap = new LinkedHashMap<>();
        String repoStr = repoId != null ? repoId.toString() : null;
        String projStr = projectId != null ? projectId.toString() : null;
        float bestRepoScore = 0.0f;

        try {
            // 1. Primary repository symbols
            List<SearchResult> repoSymbols = semanticSearchService.searchScoped(
                    task, "symbol_knowledge", projStr, repoStr, 15
            );
            for (SearchResult sr : repoSymbols) {
                symbolMap.put(sr.getId(), sr);
                if (sr.getScore() > bestRepoScore) {
                    bestRepoScore = sr.getScore();
                }
            }

            // 2. Quality-Based Fallback: If best symbol match is below threshold or empty, search project sibling libraries
            if ((bestRepoScore < SYMBOL_QUALITY_FALLBACK_THRESHOLD || symbolMap.isEmpty()) && projStr != null) {
                List<SearchResult> siblingSymbols = semanticSearchService.searchScoped(
                        task, "symbol_knowledge", projStr, null, 15
                );
                for (SearchResult sr : siblingSymbols) {
                    if (symbolMap.size() >= MAX_SYMBOL_BUDGET) break;
                    symbolMap.putIfAbsent(sr.getId(), sr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed retrieving code symbol candidates: {}", e.getMessage());
        }

        return new ArrayList<>(symbolMap.values());
    }

    /**
     * Graph-RAG Structural Subgraph Retrieval
     */
    public List<Map<String, Object>> getGraphNeighborhood(UUID repoId, int depth) {
        if (repoId == null) return List.of();
        try {
            return graphService.findRelated("Repository", repoId.toString(), null, depth);
        } catch (Exception e) {
            log.warn("Failed retrieving graph neighborhood: {}", e.getMessage());
            return List.of();
        }
    }

    private void addDecisionMatches(List<SearchResult> matches, Map<UUID, Decision> candidateMap) {
        for (SearchResult sr : matches) {
            if (candidateMap.size() >= MAX_DECISION_BUDGET) break;
            UUID decisionId = extractEntityId(sr, "decisionId");
            if (decisionId != null && !candidateMap.containsKey(decisionId)) {
                decisionRepository.findById(decisionId).ifPresent(d -> candidateMap.put(d.getId(), d));
            }
        }
    }

    private void addFailureMatches(List<SearchResult> matches, Map<UUID, AgentAttempt> candidateMap) {
        for (SearchResult sr : matches) {
            if (candidateMap.size() >= MAX_FAILURE_BUDGET) break;
            UUID attemptId = extractEntityId(sr, "attemptId");
            if (attemptId != null && !candidateMap.containsKey(attemptId)) {
                attemptRepository.findById(attemptId).ifPresent(a -> {
                    if (isFailure(a)) candidateMap.put(a.getId(), a);
                });
            }
        }
    }

    private boolean isFailure(AgentAttempt a) {
        return a != null && ("FAILED".equalsIgnoreCase(a.getStatus()) || "FAILURE".equalsIgnoreCase(a.getStatus()));
    }

    private UUID extractEntityId(SearchResult sr, String key) {
        if (sr.getPayload() != null && sr.getPayload().containsKey(key)) {
            try {
                return UUID.fromString(sr.getPayload().get(key).toString());
            } catch (Exception ignored) {}
        }
        if (sr.getPayload() != null && sr.getPayload().containsKey("aggregateId")) {
            try {
                return UUID.fromString(sr.getPayload().get("aggregateId").toString());
            } catch (Exception ignored) {}
        }
        if (sr.getPayload() != null && sr.getPayload().containsKey("entityId")) {
            try {
                return UUID.fromString(sr.getPayload().get("entityId").toString());
            } catch (Exception ignored) {}
        }
        try {
            return UUID.fromString(sr.getId());
        } catch (Exception ignored) {}
        return null;
    }
}
