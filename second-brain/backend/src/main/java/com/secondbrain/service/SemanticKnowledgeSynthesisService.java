package com.secondbrain.service;

import com.secondbrain.common.dto.KnowledgeProposal;
import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.*;
import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticKnowledgeSynthesisService {

    private final MemoryRepository memoryRepository;
    private final DecisionRepository decisionRepository;
    private final AgentAttemptRepository attemptRepository;
    private final SemanticSearchService semanticSearchService;
    private final GraphService graphService;
    private final LlmSynthesisEngine llmSynthesisEngine;
    private final OutboxProjectionService outboxService;

    /**
     * Synthesizes and promotes architectural decisions into durable long-term memories.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Memory> synthesizeAndPromoteArchitecturalKnowledge(
            String topic, String projectKey, List<Decision> decisions, Project project, RepositoryEntity repository) {

        if (decisions == null || decisions.isEmpty()) return Optional.empty();

        // 1. Gather semantic candidate memories from Qdrant
        List<Memory> existingMemories = new ArrayList<>();
        try {
            List<SearchResult> searchResults = semanticSearchService.searchScoped(
                    topic, "technical_memory", projectKey.equals("GLOBAL") ? null : projectKey, null, 10
            );
            for (SearchResult sr : searchResults) {
                if (sr.getPayload() != null && sr.getPayload().containsKey("id")) {
                    try {
                        UUID memId = UUID.fromString(sr.getPayload().get("id").toString());
                        memoryRepository.findById(memId).ifPresent(existingMemories::add);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.debug("Semantic search during synthesis skipped: {}", e.getMessage());
        }

        // 2. Gather graph neighborhood from Neo4j
        List<Map<String, Object>> graphContext = List.of();
        if (repository != null && repository.getId() != null) {
            try {
                graphContext = graphService.findRelated("Repository", repository.getId().toString(), null, 2);
            } catch (Exception ignored) {}
        }

        // 3. Generate structured KnowledgeProposal via LLM Engine
        Optional<KnowledgeProposal> proposalOpt = llmSynthesisEngine.synthesizeArchitecturalProposal(
                topic, projectKey, decisions, existingMemories, graphContext
        );

        if (proposalOpt.isEmpty()) return Optional.empty();
        KnowledgeProposal proposal = proposalOpt.get();

        // 4. Anti-Hallucination & Evidence Validation: Filter to only valid, existing entity IDs
        Set<String> validatedEvidence = validateEvidenceSources(proposal.getEvidenceSources());
        if (validatedEvidence.isEmpty() && !decisions.isEmpty()) {
            for (Decision d : decisions) {
                if (d.getId() != null) validatedEvidence.add("decision:" + d.getId());
            }
        }

        // 5. Transactional Memory Upsert
        Optional<Memory> existingOpt = memoryRepository.findByMemoryKey(proposal.getMemoryKey());
        Memory savedMemory;

        if (existingOpt.isPresent()) {
            Memory memory = existingOpt.get();
            int newEvidenceCount = 0;
            for (String ev : validatedEvidence) {
                if (memory.getEvidenceSources().add(ev)) {
                    newEvidenceCount++;
                }
            }
            if (newEvidenceCount > 0) {
                memory.setObservationCount((memory.getObservationCount() != null ? memory.getObservationCount() : 0) + newEvidenceCount);
                memory.setEvidenceCount(memory.getEvidenceSources().size());
                memory.setConfidence(proposal.getConfidence() != null ? proposal.getConfidence() : 0.92);
                memory.setStatus(proposal.getStatus() != null ? proposal.getStatus() : MemoryStatus.CONFIRMED);
                memory.setLastSeenAt(LocalDateTime.now());
                savedMemory = memoryRepository.save(memory);
                enqueueOutboxProjections(savedMemory);
            } else {
                savedMemory = memory;
            }
        } else {
            Memory memory = Memory.builder()
                    .memoryKey(proposal.getMemoryKey())
                    .content(proposal.getKnowledge())
                    .type(proposal.getType() != null ? proposal.getType() : MemoryType.ARCHITECTURAL)
                    .scope(project != null ? MemoryScope.PROJECT : MemoryScope.GLOBAL)
                    .project(project)
                    .repository(repository)
                    .status(proposal.getStatus() != null ? proposal.getStatus() : MemoryStatus.CONFIRMED)
                    .confidence(proposal.getConfidence() != null ? proposal.getConfidence() : 0.90)
                    .importance(0.88)
                    .observationCount(validatedEvidence.size())
                    .evidenceCount(validatedEvidence.size())
                    .evidenceSources(validatedEvidence)
                    .provenanceSource("MULTI_AGENT_CONSENSUS")
                    .tags(proposal.getSuggestedTags() != null ? proposal.getSuggestedTags() : new HashSet<>(Set.of(topic.toLowerCase())))
                    .firstSeenAt(LocalDateTime.now())
                    .lastSeenAt(LocalDateTime.now())
                    .build();

            savedMemory = memoryRepository.save(memory);
            enqueueOutboxProjections(savedMemory);
        }

        // 6. Handle Superseded Knowledge
        if (proposal.getSupersedesMemoryKeys() != null) {
            for (String supersededKey : proposal.getSupersedesMemoryKeys()) {
                memoryRepository.findByMemoryKey(supersededKey).ifPresent(oldMem -> {
                    oldMem.setStatus(MemoryStatus.SUPERSEDED);
                    oldMem.setSupersededBy(savedMemory.getId());
                    oldMem.setSupersededAt(LocalDateTime.now());
                    oldMem.setConfidence(Math.max(0.1, (oldMem.getConfidence() != null ? oldMem.getConfidence() : 0.5) * 0.5));
                    memoryRepository.save(oldMem);
                    enqueueOutboxProjections(oldMem);
                    log.info("🔄 Memory [{}] superseded by new standard [{}]", oldMem.getMemoryKey(), savedMemory.getMemoryKey());
                });
            }
        }

        return Optional.of(savedMemory);
    }

    private Set<String> validateEvidenceSources(Set<String> sources) {
        if (sources == null || sources.isEmpty()) return new HashSet<>();
        Set<String> validated = new HashSet<>();
        for (String ev : sources) {
            try {
                if (ev.startsWith("decision:")) {
                    UUID id = UUID.fromString(ev.substring("decision:".length()));
                    if (decisionRepository.existsById(id)) validated.add(ev);
                } else if (ev.startsWith("attempt:")) {
                    UUID id = UUID.fromString(ev.substring("attempt:".length()));
                    if (attemptRepository.existsById(id)) validated.add(ev);
                }
            } catch (Exception ignored) {}
        }
        return validated;
    }

    private void enqueueOutboxProjections(Memory memory) {
        if (memory == null || memory.getId() == null) return;
        try {
            outboxService.enqueue(
                    null, null, OutboxTarget.QDRANT, "MEMORY",
                    memory.getId().toString(),
                    Map.of(
                            "id", memory.getId().toString(),
                            "memoryKey", memory.getMemoryKey() != null ? memory.getMemoryKey() : "",
                            "content", memory.getContent(),
                            "type", memory.getType() != null ? memory.getType().name() : "GENERAL",
                            "scope", memory.getScope() != null ? memory.getScope().name() : "GLOBAL",
                            "confidence", memory.getConfidence() != null ? memory.getConfidence() : 0.8,
                            "status", memory.getStatus() != null ? memory.getStatus().name() : "CONFIRMED"
                    )
            );
            outboxService.enqueue(
                    null, null, OutboxTarget.NEO4J, "MEMORY",
                    memory.getId().toString(),
                    Map.of(
                            "id", memory.getId().toString(),
                            "memoryKey", memory.getMemoryKey() != null ? memory.getMemoryKey() : "",
                            "content", memory.getContent(),
                            "type", memory.getType() != null ? memory.getType().name() : "GENERAL"
                    )
            );
        } catch (Exception e) {
            log.warn("Failed enqueuing memory outbox projection: {}", e.getMessage());
        }
    }
}
