package com.secondbrain.service;

import com.secondbrain.common.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;

    public List<SearchResult> search(String query, String collectionName, int limit) {
        float[] queryVector = embeddingService.embed(query);
        List<Map<String, Object>> points = vectorStoreService.search(collectionName, queryVector, limit);
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

    public List<SearchResult> searchAllCollections(String query, int limit) {
        List<SearchResult> allResults = new ArrayList<>();
        List<String> collections = List.of(
            "global_knowledge",
            "project_knowledge",
            "repository_knowledge",
            "code_knowledge",
            "conversation_memory",
            "agent_memory",
            "technical_memory",
            "documentation"
        );

        for (String collection : collections) {
            try {
                allResults.addAll(search(query, collection, limit));
            } catch (Exception e) {
                log.debug("Search failed on collection {}: {}", collection, e.getMessage());
            }
        }

        allResults.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return allResults.stream().limit(limit).toList();
    }
}
