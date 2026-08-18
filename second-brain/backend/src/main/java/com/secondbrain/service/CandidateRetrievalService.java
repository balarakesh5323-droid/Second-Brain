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

    public static final int RECENT_REPO_LIMIT = 25;
    public static final int RECENT_PROJECT_LIMIT = 25;
    public static final int SEMANTIC_SEARCH_LIMIT = 20;
    public static final int MAX_DECISION_BUDGET = 60;
    public static final int MAX_FAILURE_BUDGET = 60;
    public static final int MAX_SYMBOL_BUDGET = 30;

    private final DecisionRepository decisionRepository;
    private final AgentAttemptRepository attemptRepository;
    private final SemanticSearchService semanticSearchService;
    private final GraphService graphService;

    /**
     * Hybrid Candidate Retrieval for Decisions:
     * Combines recent repository/project decisions from PostgreSQL with semantic similarity search from Qdrant.
     */
    public List<Decision> getDecisionCandidates(String task, UUID repoId, UUID projectId) {
        Map<UUID, Decision> candidateMap = new LinkedHashMap<>();

        // 1. Recent Stream (PostgreSQL)
        if (repoId != null) {
            decisionRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId, PageRequest.of(0, RECENT_REPO_LIMIT))
                    .forEach(d -> candidateMap.put(d.getId(), d));
        }
        if (projectId != null) {
            decisionRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, RECENT_PROJECT_LIMIT))
                    .forEach(d -> candidateMap.putIfAbsent(d.getId(), d));
        }

        // 2. Semantic Stream (Qdrant Vector Store)
        if (task != null && !task.isBlank() && candidateMap.size() < MAX_DECISION_BUDGET) {
            try {
                String repoStr = repoId != null ? repoId.toString() : null;
                String projStr = projectId != null ? projectId.toString() : null;

                List<SearchResult> semanticMatches = semanticSearchService.searchScoped(
                        task, "technical_memory", projStr, repoStr, SEMANTIC_SEARCH_LIMIT
                );

                for (SearchResult sr : semanticMatches) {
                    if (candidateMap.size() >= MAX_DECISION_BUDGET) break;
                    UUID decisionId = extractEntityId(sr, "decisionId");
                    if (decisionId != null && !candidateMap.containsKey(decisionId)) {
                        decisionRepository.findById(decisionId).ifPresent(d -> candidateMap.put(d.getId(), d));
                    }
                }
            } catch (Exception e) {
                log.debug("Semantic decision retrieval skipped: {}", e.getMessage());
            }
        }

        return new ArrayList<>(candidateMap.values());
    }

    /**
     * Hybrid Candidate Retrieval for Failed Attempts:
     * Combines recent failure trials from PostgreSQL with semantic similarity search from Qdrant.
     */
    public List<AgentAttempt> getFailureCandidates(String task, UUID repoId, UUID projectId) {
        Map<UUID, AgentAttempt> candidateMap = new LinkedHashMap<>();

        // 1. Recent Stream (PostgreSQL)
        if (repoId != null) {
            attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId, PageRequest.of(0, RECENT_REPO_LIMIT))
                    .forEach(a -> {
                        if (isFailure(a)) candidateMap.put(a.getId(), a);
                    });
        }
        if (projectId != null) {
            attemptRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, RECENT_PROJECT_LIMIT))
                    .forEach(a -> {
                        if (isFailure(a)) candidateMap.putIfAbsent(a.getId(), a);
                    });
        }

        // 2. Semantic Stream (Qdrant Vector Store)
        if (task != null && !task.isBlank() && candidateMap.size() < MAX_FAILURE_BUDGET) {
            try {
                String repoStr = repoId != null ? repoId.toString() : null;
                String projStr = projectId != null ? projectId.toString() : null;

                List<SearchResult> semanticMatches = semanticSearchService.searchScoped(
                        task, "agent_memory", projStr, repoStr, SEMANTIC_SEARCH_LIMIT
                );

                for (SearchResult sr : semanticMatches) {
                    if (candidateMap.size() >= MAX_FAILURE_BUDGET) break;
                    UUID attemptId = extractEntityId(sr, "attemptId");
                    if (attemptId != null && !candidateMap.containsKey(attemptId)) {
                        attemptRepository.findById(attemptId).ifPresent(a -> {
                            if (isFailure(a)) candidateMap.put(a.getId(), a);
                        });
                    }
                }
            } catch (Exception e) {
                log.debug("Semantic failure retrieval skipped: {}", e.getMessage());
            }
        }

        return new ArrayList<>(candidateMap.values());
    }

    /**
     * Semantic Candidate Retrieval for Code Symbols:
     * Retrieves symbols matching task semantics across primary repo with cross-project sibling fallback.
     */
    public List<SearchResult> getSymbolCandidates(String task, UUID repoId, UUID projectId) {
        if (task == null || task.isBlank()) return List.of();

        Map<String, SearchResult> symbolMap = new LinkedHashMap<>();
        String repoStr = repoId != null ? repoId.toString() : null;
        String projStr = projectId != null ? projectId.toString() : null;

        try {
            // Primary repository symbols
            List<SearchResult> repoSymbols = semanticSearchService.searchScoped(
                    task, "symbol_knowledge", projStr, repoStr, SEMANTIC_SEARCH_LIMIT
            );
            for (SearchResult sr : repoSymbols) {
                symbolMap.put(sr.getId(), sr);
            }

            // If primary repo has few matches, search sibling libraries in same project
            if (symbolMap.size() < 5 && projStr != null) {
                List<SearchResult> siblingSymbols = semanticSearchService.searchScoped(
                        task, "symbol_knowledge", projStr, null, 10
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
        try {
            return UUID.fromString(sr.getId());
        } catch (Exception ignored) {}
        return null;
    }
}
