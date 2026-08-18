package com.secondbrain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.common.entity.AgentOutbox;
import com.secondbrain.common.enums.OutboxStatus;
import com.secondbrain.common.enums.OutboxTarget;
import com.secondbrain.common.repository.AgentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
        String jsonPayload;
        try {
            jsonPayload = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize outbox payload", e);
        }

        AgentOutbox outbox = AgentOutbox.builder()
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
    public AgentOutbox enqueueAndProcess(UUID sessionId, UUID eventId, OutboxTarget target, String aggregateType, String aggregateId, Object payload) {
        AgentOutbox outbox = enqueue(sessionId, eventId, target, aggregateType, aggregateId, payload);
        processSingleOutbox(outbox);
        return outbox;
    }

    @Transactional
    public boolean processSingleOutbox(AgentOutbox outbox) {
        if (outbox == null) return false;

        outbox.setStatus(OutboxStatus.PROCESSING);
        outboxRepository.save(outbox);

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

    @Transactional
    public boolean processSingleOutbox(UUID outboxId) {
        AgentOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null) return false;
        return processSingleOutbox(outbox);
    }

    @Scheduled(fixedDelay = 3000)
    public void processPendingOutbox() {
        LocalDateTime now = LocalDateTime.now();
        List<AgentOutbox> pending = outboxRepository.findReadyToProcess(
                OutboxStatus.PENDING, OutboxStatus.FAILED, now, PageRequest.of(0, 50));

        if (pending.isEmpty()) return;

        log.debug("Processing {} pending outbox items", pending.size());
        for (AgentOutbox item : pending) {
            if (item.getRetryCount() < item.getMaxRetries()) {
                processSingleOutbox(item.getId());
            }
        }
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
