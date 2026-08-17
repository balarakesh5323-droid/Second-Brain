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

        List<Map<String, Object>> points = vectorStoreService.searchWithFilter(collectionName, queryVector, mustFilters, limit);
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
            .toList();
    }

    public List<SearchResult> searchSymbols(String query, int limit) {
        return search(query, "symbol_knowledge", limit);
    }

    public List<SearchResult> searchAllCollections(String query, int limit) {
        return searchAllCollectionsScoped(query, null, null, limit);
    }

    /**
     * Hierarchical multi-scope knowledge retrieval:
     * 1. Repository-level knowledge (Weight: 1.0)
     * 2. Project-level knowledge (Weight: 0.8)
     * 3. Global & Documentation knowledge (Weight: 0.6)
     * Reranks and deduplicates before returning top matches.
     */
    public List<SearchResult> searchAllCollectionsScoped(String query, String projectId, String repositoryId, int limit) {
        Map<String, SearchResult> mergedMap = new HashMap<>();

        List<String> collections = List.of(
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

        // Tier 1: Repository Scope (Weight 1.0)
        if (repositoryId != null && !repositoryId.isBlank()) {
            for (String col : collections) {
                try {
                    List<SearchResult> res = searchScoped(query, col, projectId, repositoryId, limit);
                    for (SearchResult r : res) {
                        mergedMap.put(r.getId(), r);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Tier 2: Project Scope (Weight 0.8)
        if (projectId != null && !projectId.isBlank()) {
            for (String col : collections) {
                try {
                    List<SearchResult> res = searchScoped(query, col, projectId, null, limit);
                    for (SearchResult r : res) {
                        if (!mergedMap.containsKey(r.getId())) {
                            SearchResult weighted = SearchResult.builder()
                                    .id(r.getId())
                                    .score(r.getScore() * 0.8f)
                                    .collection(r.getCollection())
                                    .content(r.getContent())
                                    .payload(r.getPayload())
                                    .build();
                            mergedMap.put(r.getId(), weighted);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Tier 3: Global Knowledge & Technical Documentation (Weight 0.6)
        List<String> globalCols = List.of("global_knowledge", "documentation", "technical_memory");
        for (String col : globalCols) {
            try {
                List<SearchResult> res = searchScoped(query, col, null, null, limit);
                for (SearchResult r : res) {
                    if (!mergedMap.containsKey(r.getId())) {
                        SearchResult weighted = SearchResult.builder()
                                .id(r.getId())
                                .score(r.getScore() * 0.6f)
                                .collection(r.getCollection())
                                .content(r.getContent())
                                .payload(r.getPayload())
                                .build();
                        mergedMap.put(r.getId(), weighted);
                    }
                }
            } catch (Exception ignored) {}
        }

        List<SearchResult> finalResults = new ArrayList<>(mergedMap.values());
        finalResults.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return finalResults.stream().limit(limit).toList();
    }
}
