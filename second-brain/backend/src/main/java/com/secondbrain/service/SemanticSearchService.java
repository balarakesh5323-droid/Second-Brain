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
        return searchScoped(query, collectionName, null, null, limit);
    }

    public List<SearchResult> searchScoped(String query, String collectionName, String projectId, String repositoryId, int limit) {
        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) return List.of();

        List<Map<String, Object>> points = vectorStoreService.search(collectionName, queryVector, limit * 2);
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
            .filter(sr -> {
                if (projectId == null && repositoryId == null) return true;
                Map<String, Object> p = sr.getPayload();
                if (p == null) return true;
                String pointProj = String.valueOf(p.getOrDefault("projectId", p.getOrDefault("project", "")));
                String pointRepo = String.valueOf(p.getOrDefault("repositoryId", p.getOrDefault("repository", "")));

                if (projectId != null && !pointProj.isBlank() && !"global".equalsIgnoreCase(pointProj) && !pointProj.equalsIgnoreCase(projectId)) {
                    return false;
                }
                if (repositoryId != null && !pointRepo.isBlank() && !"global".equalsIgnoreCase(pointRepo) && !pointRepo.equalsIgnoreCase(repositoryId)) {
                    return false;
                }
                return true;
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
        List<SearchResult> allResults = new ArrayList<>();
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

        for (String collection : collections) {
            try {
                allResults.addAll(searchScoped(query, collection, projectId, repositoryId, limit));
            } catch (Exception e) {
                log.debug("Search failed on collection {}: {}", collection, e.getMessage());
            }
        }

        allResults.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return allResults.stream().limit(limit).toList();
    }
}
