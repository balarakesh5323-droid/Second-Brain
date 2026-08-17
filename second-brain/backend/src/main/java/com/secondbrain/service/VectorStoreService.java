package com.secondbrain.service;

import com.secondbrain.config.QdrantConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Points;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.grpc.Collections.Distance;
import static io.qdrant.client.grpc.Collections.VectorParams;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorStoreService {

    private final QdrantConfig qdrantConfig;
    private final EmbeddingService embeddingService;
    private QdrantClient qdrantClient;

    private static final List<String> COLLECTIONS = List.of(
        "global_knowledge",
        "project_knowledge",
        "repository_knowledge",
        "code_knowledge",
        "conversation_memory",
        "agent_memory",
        "technical_memory",
        "documentation"
    );

    @PostConstruct
    public void init() {
        try {
            qdrantClient = new QdrantClient(
                QdrantGrpcClient.newBuilder(
                    qdrantConfig.getHost(),
                    qdrantConfig.getGrpcPort(),
                    false
                ).build()
            );
            log.info("Qdrant client initialized: {}:{}", qdrantConfig.getHost(), qdrantConfig.getGrpcPort());
        } catch (Exception e) {
            log.error("Failed to initialize Qdrant client: {}", e.getMessage());
        }
    }

    public void ensureCollectionsExist() {
        int dimension = embeddingService.getEmbeddingDimensions();
        for (String collectionName : COLLECTIONS) {
            try {
                qdrantClient.createCollectionAsync(
                    collectionName,
                    VectorParams.newBuilder()
                        .setDistance(Distance.Cosine)
                        .setSize(dimension)
                        .build()
                ).get();
                log.info("Created/verified collection: {} (dim={})", collectionName, dimension);
            } catch (Exception e) {
                log.debug("Collection {} already exists or error: {}", collectionName, e.getMessage());
            }
        }
    }

    public void upsert(String collectionName, String id, float[] vector, Map<String, String> stringPayload, Map<String, Double> doublePayload) {
        try {
            Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = new HashMap<>();
            for (Map.Entry<String, String> entry : stringPayload.entrySet()) {
                payload.put(entry.getKey(), value(entry.getValue()));
            }
            for (Map.Entry<String, Double> entry : doublePayload.entrySet()) {
                payload.put(entry.getKey(), value(entry.getValue()));
            }

            Points.PointStruct point = Points.PointStruct.newBuilder()
                .setId(id(UUID.fromString(id)))
                .setVectors(vectors(vector))
                .putAllPayload(payload)
                .build();

            qdrantClient.upsertAsync(collectionName, List.of(point)).get();
            log.debug("Upserted point {} to collection {}", id, collectionName);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to upsert point {} to collection {}: {}", id, collectionName, e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public List<Map<String, Object>> search(String collectionName, float[] vector, int limit) {
        try {
            List<Points.ScoredPoint> points = qdrantClient.searchAsync(
                Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(toFloatList(vector))
                    .setLimit(limit)
                    .build()
            ).get();

            List<Map<String, Object>> results = new ArrayList<>();
            for (Points.ScoredPoint point : points) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", point.getId().getUuid());
                result.put("score", point.getScore());
                result.put("payload", extractPayload(point.getPayloadMap()));
                results.add(result);
            }
            return results;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to search collection {}: {}", collectionName, e.getMessage());
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    public void delete(String collectionName, String id) {
        try {
            qdrantClient.deleteAsync(
                collectionName,
                List.of(id(UUID.fromString(id)))
            ).get();
            log.debug("Deleted point {} from collection {}", id, collectionName);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete point {} from collection {}: {}", id, collectionName, e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public void wipeAllCollections() {
        for (String collectionName : COLLECTIONS) {
            try {
                qdrantClient.deleteCollectionAsync(collectionName).get();
                log.info("Deleted Qdrant collection {}", collectionName);
            } catch (Exception e) {
                log.warn("Failed to delete Qdrant collection {}: {}", collectionName, e.getMessage());
            }
        }
        ensureCollectionsExist();
    }

    @PreDestroy
    public void close() {
        if (qdrantClient != null) {
            try {
                qdrantClient.close();
                log.info("Qdrant client closed");
            } catch (Exception e) {
                log.error("Error closing Qdrant client: {}", e.getMessage());
            }
        }
    }

    private Map<String, Object> extractPayload(Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, io.qdrant.client.grpc.JsonWithInt.Value> entry : payloadMap.entrySet()) {
            io.qdrant.client.grpc.JsonWithInt.Value val = entry.getValue();
            switch (val.getKindCase()) {
                case STRING_VALUE -> result.put(entry.getKey(), val.getStringValue());
                case DOUBLE_VALUE -> result.put(entry.getKey(), val.getDoubleValue());
                case INTEGER_VALUE -> result.put(entry.getKey(), val.getIntegerValue());
                case BOOL_VALUE -> result.put(entry.getKey(), val.getBoolValue());
                default -> result.put(entry.getKey(), val.toString());
            }
        }
        return result;
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float v : vector) {
            result.add(v);
        }
        return result;
    }
}
