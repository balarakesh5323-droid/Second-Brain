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
     * Retrieves candidates into independent buckets (recent repo, recent project, semantic repo, semantic project),
     * guarantees zero starvation, and deduplicates across all streams.
     */
    public List<Decision> getDecisionCandidates(String task, UUID repoId, UUID projectId) {
        String repoStr = repoId != null ? repoId.toString() : null;
        String projStr = projectId != null ? projectId.toString() : null;

        List<Decision> recentRepoList = new ArrayList<>();
        List<Decision> recentProjectList = new ArrayList<>();
        List<Decision> semanticRepoList = new ArrayList<>();
        List<Decision> semanticProjectList = new ArrayList<>();

        // Bucket 1: Recent Repo Stream (PostgreSQL)
        if (repoId != null) {
            recentRepoList = decisionRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId, PageRequest.of(0, RECENT_REPO_QUOTA));
        }

        // Bucket 2: Recent Project Stream (PostgreSQL)
        if (projectId != null) {
            recentProjectList = decisionRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, RECENT_PROJECT_QUOTA));
        }

        // Bucket 3 & 4: Semantic Repo & Project Streams (Qdrant Vector Store)
        if (task != null && !task.isBlank()) {
            try {
                if (repoStr != null) {
                    List<SearchResult> repoMatches = semanticSearchService.searchScoped(
                            task, "technical_memory", projStr, repoStr, SEMANTIC_REPO_QUOTA
                    );
                    semanticRepoList = resolveDecisionsFromSearchResults(repoMatches, SEMANTIC_REPO_QUOTA);
                }

                if (projStr != null) {
                    List<SearchResult> projMatches = semanticSearchService.searchScoped(
                            task, "technical_memory", projStr, null, SEMANTIC_PROJECT_QUOTA
                    );
                    semanticProjectList = resolveDecisionsFromSearchResults(projMatches, SEMANTIC_PROJECT_QUOTA);
                }
            } catch (Exception e) {
                log.debug("Semantic decision retrieval skipped: {}", e.getMessage());
            }
        }

        // Deduplicate across all independent buckets preserving priority order
        Map<UUID, Decision> candidateMap = new LinkedHashMap<>();
        for (Decision d : recentRepoList) candidateMap.put(d.getId(), d);
        for (Decision d : semanticRepoList) candidateMap.putIfAbsent(d.getId(), d);
        for (Decision d : recentProjectList) candidateMap.putIfAbsent(d.getId(), d);
        for (Decision d : semanticProjectList) candidateMap.putIfAbsent(d.getId(), d);

        return new ArrayList<>(candidateMap.values());
    }

    /**
     * Hybrid Candidate Retrieval for Failed Attempts (v2):
     * Retrieves failure candidates into independent buckets and deduplicates without stream starvation.
     */
    public List<AgentAttempt> getFailureCandidates(String task, UUID repoId, UUID projectId) {
        String repoStr = repoId != null ? repoId.toString() : null;
        String projStr = projectId != null ? projectId.toString() : null;

        List<AgentAttempt> recentRepoList = new ArrayList<>();
        List<AgentAttempt> recentProjectList = new ArrayList<>();
        List<AgentAttempt> semanticRepoList = new ArrayList<>();
        List<AgentAttempt> semanticProjectList = new ArrayList<>();

        // Bucket 1: Recent Repo Failures (PostgreSQL)
        if (repoId != null) {
            recentRepoList = attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId, PageRequest.of(0, RECENT_REPO_QUOTA))
                    .stream().filter(this::isFailure).toList();
        }

        // Bucket 2: Recent Project Failures (PostgreSQL)
        if (projectId != null) {
            recentProjectList = attemptRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, RECENT_PROJECT_QUOTA))
                    .stream().filter(this::isFailure).toList();
        }

        // Bucket 3 & 4: Semantic Repo & Project Failures (Qdrant Vector Store)
        if (task != null && !task.isBlank()) {
            try {
                if (repoStr != null) {
                    List<SearchResult> repoMatches = semanticSearchService.searchScoped(
                            task, "agent_memory", projStr, repoStr, SEMANTIC_REPO_QUOTA
                    );
                    semanticRepoList = resolveFailuresFromSearchResults(repoMatches, SEMANTIC_REPO_QUOTA);
                }

                if (projStr != null) {
                    List<SearchResult> projMatches = semanticSearchService.searchScoped(
                            task, "agent_memory", projStr, null, SEMANTIC_PROJECT_QUOTA
                    );
                    semanticProjectList = resolveFailuresFromSearchResults(projMatches, SEMANTIC_PROJECT_QUOTA);
                }
            } catch (Exception e) {
                log.debug("Semantic failure retrieval skipped: {}", e.getMessage());
            }
        }

        // Deduplicate across all independent buckets
        Map<UUID, AgentAttempt> candidateMap = new LinkedHashMap<>();
        for (AgentAttempt a : recentRepoList) candidateMap.put(a.getId(), a);
        for (AgentAttempt a : semanticRepoList) candidateMap.putIfAbsent(a.getId(), a);
        for (AgentAttempt a : recentProjectList) candidateMap.putIfAbsent(a.getId(), a);
        for (AgentAttempt a : semanticProjectList) candidateMap.putIfAbsent(a.getId(), a);

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

    private List<Decision> resolveDecisionsFromSearchResults(List<SearchResult> matches, int limit) {
        List<Decision> results = new ArrayList<>();
        for (SearchResult sr : matches) {
            if (results.size() >= limit) break;
            UUID decisionId = extractEntityId(sr, "decisionId");
            if (decisionId != null) {
                decisionRepository.findById(decisionId).ifPresent(results::add);
            }
        }
        return results;
    }

    private List<AgentAttempt> resolveFailuresFromSearchResults(List<SearchResult> matches, int limit) {
        List<AgentAttempt> results = new ArrayList<>();
        for (SearchResult sr : matches) {
            if (results.size() >= limit) break;
            UUID attemptId = extractEntityId(sr, "attemptId");
            if (attemptId != null) {
                attemptRepository.findById(attemptId).ifPresent(a -> {
                    if (isFailure(a)) results.add(a);
                });
            }
        }
        return results;
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
