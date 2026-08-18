package com.secondbrain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.common.entity.AgentOutbox;
import com.secondbrain.common.enums.OutboxStatus;
import com.secondbrain.common.enums.OutboxTarget;
import com.secondbrain.common.repository.AgentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxProjectionService {

    private final AgentOutboxRepository outboxRepository;
    private final GraphService graphService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentOutbox enqueue(UUID sessionId, UUID eventId, OutboxTarget target, String aggregateType, String aggregateId, Object payload) {
        String idempotencyKey = (eventId != null ? eventId.toString() : (sessionId != null ? sessionId.toString() : aggregateId) + ":" + aggregateType) + ":" + target.name();

        var existing = outboxRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        String jsonPayload;
        try {
            jsonPayload = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize outbox payload", e);
        }

        AgentOutbox outbox = AgentOutbox.builder()
                .idempotencyKey(idempotencyKey)
                .sessionId(sessionId)
                .eventId(eventId)
                .target(target)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payload(jsonPayload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(5)
                .nextRetryAt(LocalDateTime.now())
                .build();

        return outboxRepository.save(outbox);
    }

    @Transactional
    public List<AgentOutbox> claimBatch(int limit) {
        List<AgentOutbox> claimed = outboxRepository.claimReadyForProcessing(LocalDateTime.now(), limit);
        LocalDateTime now = LocalDateTime.now();
        for (AgentOutbox item : claimed) {
            item.setStatus(OutboxStatus.PROCESSING);
            item.setProcessingStartedAt(now);
            outboxRepository.save(item);
        }
        return claimed;
    }

    @Transactional
    public int recoverStuckProcessing(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<AgentOutbox> stuck = outboxRepository.findStuckProcessing(OutboxStatus.PROCESSING, threshold);
        for (AgentOutbox item : stuck) {
            item.setStatus(OutboxStatus.PENDING);
            item.setLastError("Processing timeout recovery - reset to PENDING");
            item.setNextRetryAt(LocalDateTime.now());
            item.setProcessingStartedAt(null);
            outboxRepository.save(item);
        }
        if (!stuck.isEmpty()) {
            log.info("🔄 Outbox Recovery: Reset {} stuck PROCESSING items back to PENDING", stuck.size());
        }
        return stuck.size();
    }

    @Transactional
    public boolean executeProjection(UUID outboxId) {
        AgentOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null) return false;
        if (outbox.getStatus() == OutboxStatus.COMPLETED) return true;

        try {
            if (outbox.getTarget() == OutboxTarget.NEO4J) {
                projectToNeo4j(outbox);
            } else if (outbox.getTarget() == OutboxTarget.QDRANT) {
                projectToQdrant(outbox);
            }

            outbox.setStatus(OutboxStatus.COMPLETED);
            outbox.setProcessedAt(LocalDateTime.now());
            outbox.setLastError(null);
            outboxRepository.save(outbox);
            return true;
        } catch (Exception e) {
            int retries = outbox.getRetryCount() + 1;
            outbox.setRetryCount(retries);
            outbox.setLastError(e.getMessage());
            outbox.setStatus(retries >= outbox.getMaxRetries() ? OutboxStatus.FAILED : OutboxStatus.PENDING);
            long delaySec = Math.min(300, (long) Math.pow(2, retries));
            outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySec));
            outboxRepository.save(outbox);
            log.warn("Outbox projection failed for {} (id: {}, retry: {}): {}", outbox.getTarget(), outbox.getId(), retries, e.getMessage());
            return false;
        }
    }

    @Scheduled(fixedDelay = 2000)
    public int processPendingOutbox() {
        recoverStuckProcessing(5);
        List<AgentOutbox> claimed = claimBatch(50);
        int successCount = 0;
        for (AgentOutbox item : claimed) {
            if (executeProjection(item.getId())) {
                successCount++;
            }
        }
        return successCount;
    }

    @SuppressWarnings("unchecked")
    private void projectToNeo4j(AgentOutbox outbox) throws Exception {
        Map<String, Object> map = objectMapper.readValue(outbox.getPayload(), new TypeReference<Map<String, Object>>() {});
        String agentName = (String) map.getOrDefault("agentName", "unknown-agent");
        String agentType = (String) map.getOrDefault("agentType", "CLI");
        String sessionId = (String) map.getOrDefault("sessionId", "");
        Map<String, Object> sessionProps = (Map<String, Object>) map.getOrDefault("sessionProps", Map.of());
        String repositoryId = (String) map.get("repositoryId");
        List<String> touchedFiles = (List<String>) map.getOrDefault("touchedFiles", List.of());
        List<Map<String, Object>> problems = (List<Map<String, Object>>) map.getOrDefault("problems", List.of());
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) map.getOrDefault("decisions", List.of());
        List<Map<String, Object>> failedAttempts = (List<Map<String, Object>>) map.getOrDefault("failedAttempts", List.of());
        List<Map<String, Object>> commits = (List<Map<String, Object>>) map.getOrDefault("commits", List.of());
        Map<String, Object> handoff = (Map<String, Object>) map.get("handoff");

        graphService.recordAgentSessionGraph(
                agentName,
                agentType,
                sessionId,
                sessionProps,
                repositoryId,
                touchedFiles,
                problems,
                decisions,
                failedAttempts,
                commits,
                handoff
        );
    }

    @SuppressWarnings("unchecked")
    private void projectToQdrant(AgentOutbox outbox) throws Exception {
        Map<String, Object> map = objectMapper.readValue(outbox.getPayload(), new TypeReference<Map<String, Object>>() {});
        String collection = (String) map.get("collection");
        String pointId = (String) map.get("pointId");
        String textToEmbed = (String) map.get("textToEmbed");
        Map<String, String> payloadMap = (Map<String, String>) map.getOrDefault("payload", Map.of());
        Map<String, Double> metadataMap = (Map<String, Double>) map.getOrDefault("metadata", Map.of());

        float[] vector = embeddingService.embed(textToEmbed);
        if (vector != null) {
            vectorStoreService.upsert(collection, pointId, vector, payloadMap, metadataMap != null ? metadataMap : Map.of());
        }
    }
}
