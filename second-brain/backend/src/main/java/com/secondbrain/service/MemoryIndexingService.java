package com.secondbrain.service;

import com.secondbrain.common.entity.Memory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryIndexingService {

    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;

    public void indexMemory(Memory memory) {
        String collectionName = resolveCollectionName(memory);
        float[] embedding = embeddingService.embed(memory.getContent());

        Map<String, String> stringPayload = Map.of(
            "memory_id", memory.getId().toString(),
            "content", memory.getContent(),
            "type", memory.getType().name(),
            "scope", memory.getScope().name(),
            "status", memory.getStatus().name()
        );

        Map<String, Double> doublePayload = Map.of(
            "confidence", memory.getConfidence(),
            "observation_count", (double) memory.getObservationCount()
        );

        vectorStoreService.upsert(collectionName, memory.getId().toString(), embedding, stringPayload, doublePayload);
        log.info("Indexed memory {} in collection {}", memory.getId(), collectionName);
    }

    public void removeMemory(Memory memory) {
        String collectionName = resolveCollectionName(memory);
        vectorStoreService.delete(collectionName, memory.getId().toString());
        log.info("Removed memory {} from collection {}", memory.getId(), collectionName);
    }

    private String resolveCollectionName(Memory memory) {
        return switch (memory.getScope()) {
            case GLOBAL -> "global_knowledge";
            case PROJECT -> "project_knowledge";
            case REPOSITORY -> "repository_knowledge";
        };
    }
}
