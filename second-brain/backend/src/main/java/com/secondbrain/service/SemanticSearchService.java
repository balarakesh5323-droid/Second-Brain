package com.secondbrain.service;

import com.secondbrain.common.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;

    public List<SearchResult> search(String query, String collectionName, int limit) {
        return searchScoped(query, collectionName, null, null, limit);
    }

    public List<SearchResult> searchScoped(String query, String collectionName, String projectId, String repositoryId, int limit) {
        return searchScopedWithStatuses(query, collectionName, projectId, repositoryId, null, limit);
    }

    /**
     * Executes vector search scoped by repository/project and strictly filtered by allowed memory lifecycle statuses
     * (e.g. ESTABLISHED, CONFIRMED) to prevent untrusted or superseded memories from leaking into agent context.
     */
    public List<SearchResult> searchScopedWithStatuses(String query, String collectionName, String projectId, String repositoryId, List<String> allowedStatuses, int limit) {
        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) return List.of();

        // Native Qdrant filter pushdown: filters vectors before top-K distance scoring
        Map<String, String> mustFilters = new HashMap<>();
        if (!"global_knowledge".equalsIgnoreCase(collectionName) && !"documentation".equalsIgnoreCase(collectionName)) {
            if (repositoryId != null && !repositoryId.isBlank()) {
                mustFilters.put("repositoryId", repositoryId);
            } else if (projectId != null && !projectId.isBlank()) {
                mustFilters.put("projectId", projectId);
            }
        }

        Map<String, List<String>> mustMatchAny = new HashMap<>();
        if (allowedStatuses != null && !allowedStatuses.isEmpty()) {
            mustMatchAny.put("status", allowedStatuses);
        }

        List<Map<String, Object>> points = vectorStoreService.searchWithAdvancedFilter(
                collectionName, queryVector, mustFilters, mustMatchAny, limit
        );
        
        return points.stream()
            .map(point -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) point.get("payload");
                String content = payload != null ? (String) payload.getOrDefault("content", "") : "";
                return SearchResult.builder()
                    .id((String) point.get("id"))
                    .score(((Number) point.get("score")).floatValue())
                    .collection(collectionName)
                    .content(content)
                    .payload(payload != null ? payload : Map.of())
                    .build();
            })
            .filter(res -> {
                if (allowedStatuses == null || allowedStatuses.isEmpty()) return true;
                Object statusObj = res.getPayload().get("status");
                if (statusObj == null) return false; // Strict safety: null/untyped status is rejected from authoritative knowledge
                String statusStr = statusObj.toString().toUpperCase();
                return allowedStatuses.stream().anyMatch(statusStr::equalsIgnoreCase);
            })
            .limit(limit)
            .toList();
    }

    public List<SearchResult> searchSymbols(String query, int limit) {
        return search(query, "symbol_knowledge", limit);
    }

    public List<SearchResult> searchAllCollections(String query, int limit) {
        return searchAllCollectionsScoped(query, null, null, limit);
    }

    public List<SearchResult> searchAllCollectionsScoped(String query, String projectId, String repositoryId, int limit) {
        List<SearchResult> results = new ArrayList<>();
        List<String> targetCollections = List.of(
            "global_knowledge",
            "project_knowledge",
            "repository_knowledge",
            "code_knowledge",
            "symbol_knowledge",
            "conversation_memory",
            "agent_memory",
            "technical_memory",
            "documentation"
        );

        int perCollectionLimit = Math.max(2, limit / 3);

        for (String collection : targetCollections) {
            try {
                results.addAll(searchScoped(query, collection, projectId, repositoryId, perCollectionLimit));
            } catch (Exception e) {
                log.debug("Skipping collection {} during search: {}", collection, e.getMessage());
            }
        }

        results.sort(Comparator.comparing(SearchResult::getScore).reversed());
        return results.stream().limit(limit).toList();
    }
}
