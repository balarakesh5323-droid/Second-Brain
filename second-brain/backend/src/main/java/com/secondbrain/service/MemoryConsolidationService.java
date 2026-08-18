package com.secondbrain.service;

import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.*;
import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryConsolidationService {

    public static final int BATCH_SIZE = 50;

    private final MemoryRepository memoryRepository;
    private final AgentEventRepository agentEventRepository;
    private final DecisionRepository decisionRepository;
    private final AgentAttemptRepository attemptRepository;
    private final AgentSessionRepository sessionRepository;
    private final ConsolidationCheckpointRepository checkpointRepository;
    private final SemanticSearchService semanticSearchService;
    private final SemanticKnowledgeSynthesisService semanticKnowledgeSynthesisService;
    private final OutboxProjectionService outboxService;
    private final ConsolidationLockService lockService;

    /**
     * Autonomous consolidation cycle: runs daily at 2:00 AM, or on-demand via API / MCP.
     * Protected by PostgreSQL distributed advisory lock holding a dedicated connection.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public Map<String, Object> runConsolidationCycle() {
        if (!lockService.tryAcquireLock()) {
            log.warn("⚠️ Consolidation lock already held by another pod/worker. Skipping cycle.");
            return Map.of("status", "skipped", "reason", "lock_held");
        }

        try {
            log.info("🧠 Starting autonomous Second Brain memory consolidation cycle...");
            long startTime = System.currentTimeMillis();

            int synthesizedDecisions = consolidateArchitecturalDecisions();
            int antiPatternsLearned = consolidateFailureAntiPatterns();
            int preferencesLearned = consolidateDeveloperPreferences();
            int resolvedContradictions = resolveContradictionsAndSupersede();
            int compoundedCount = compoundConfidence();
            int decayedCount = decayStaleMemories();
            int consolidatedHotspots = consolidateHotspots();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("🧠 Consolidation cycle finished in {}ms: {} decisions synthesized, {} anti-patterns learned, {} preferences, {} contradictions resolved, {} compounded, {} decayed",
                    elapsed, synthesizedDecisions, antiPatternsLearned, preferencesLearned, resolvedContradictions, compoundedCount, decayedCount);

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("status", "success");
            report.put("elapsedMs", elapsed);
            report.put("decisionsSynthesized", synthesizedDecisions);
            report.put("antiPatternsLearned", antiPatternsLearned);
            report.put("preferencesLearned", preferencesLearned);
            report.put("contradictionsResolved", resolvedContradictions);
            report.put("memoriesCompounded", compoundedCount);
            report.put("memoriesDecayed", decayedCount);
            report.put("hotspotsConsolidated", consolidatedHotspots);
            report.put("timestamp", LocalDateTime.now().toString());
            return report;
        } finally {
            lockService.releaseLock();
        }
    }

    /**
     * 1. Architectural Decision Learning (Incremental & Database-Backed):
     * Incremental batch processing with composite (timestamp, id) cursor, deterministic memory keys, and evidence linking.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int consolidateArchitecturalDecisions() {
        ConsolidationCheckpoint checkpoint = getOrCreateCheckpoint("DECISION_CURSOR");
        LocalDateTime cursor = checkpoint.getLastProcessedAt();
        UUID lastId = checkpoint.getLastProcessedId();

        List<Decision> batch = decisionRepository.findIncremental(cursor, lastId, PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return 0;

        int synthesized = 0;
        Decision lastDecision = batch.get(batch.size() - 1);

        // Group batch by project & topic
        Map<String, List<Decision>> topicClusters = new LinkedHashMap<>();
        for (Decision d : batch) {
            String title = d.getTitle() != null ? d.getTitle() : "";
            String topic = extractMainTopic(title);
            if (topic != null) {
                String projectKey = d.getProject() != null ? d.getProject().getId().toString() : "GLOBAL";
                String clusterKey = projectKey + ":" + topic;
                topicClusters.computeIfAbsent(clusterKey, k -> new ArrayList<>()).add(d);
            }
        }

        for (Map.Entry<String, List<Decision>> entry : topicClusters.entrySet()) {
            String clusterKey = entry.getKey();
            List<Decision> cluster = entry.getValue();
            String[] parts = clusterKey.split(":", 2);
            String projectKey = parts[0];
            String topic = parts[1];

            Decision sample = cluster.get(0);
            Optional<Memory> synthesizedMem = semanticKnowledgeSynthesisService.synthesizeAndPromoteArchitecturalKnowledge(
                    topic, projectKey, cluster, sample.getProject(), sample.getRepository()
            );
            if (synthesizedMem.isPresent()) {
                synthesized++;
            }
        }

        // Advance composite checkpoint cursor
        checkpoint.setLastProcessedAt(lastDecision.getCreatedAt());
        checkpoint.setLastProcessedId(lastDecision.getId());
        checkpoint.setProcessedCount(checkpoint.getProcessedCount() + batch.size());
        checkpoint.setLastRunStatus("SUCCESS");
        checkpointRepository.save(checkpoint);

        return synthesized;
    }

    /**
     * 2. Failure Anti-Pattern Learning (Incremental & Database-Backed with Composite Cursor)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int consolidateFailureAntiPatterns() {
        ConsolidationCheckpoint checkpoint = getOrCreateCheckpoint("ATTEMPT_CURSOR");
        LocalDateTime cursor = checkpoint.getLastProcessedAt();
        UUID lastId = checkpoint.getLastProcessedId();

        List<AgentAttempt> batch = attemptRepository.findIncrementalFailures(List.of("FAILED", "FAILURE"), cursor, lastId, PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return 0;

        int learned = 0;
        AgentAttempt lastAttempt = batch.get(batch.size() - 1);

        for (AgentAttempt fa : batch) {
            String lesson = fa.getLessonLearned();
            String topic = extractMainTopic(lesson != null ? lesson : fa.getApproach());

            if (topic != null) {
                String projectKey = fa.getProject() != null ? fa.getProject().getId().toString() : "GLOBAL";
                String memoryKey = "ANTI_PATTERN:" + projectKey + ":" + topic.toUpperCase();

                Optional<Memory> existingOpt = memoryRepository.findByMemoryKey(memoryKey);
                if (existingOpt.isPresent()) {
                    Memory memory = existingOpt.get();
                    boolean added = fa.getId() != null && memory.getEvidenceSources().add("attempt:" + fa.getId());
                    if (added) {
                        memory.setObservationCount((memory.getObservationCount() != null ? memory.getObservationCount() : 0) + 1);
                        memory.setEvidenceCount(memory.getEvidenceSources().size());
                        memory.setConfidence(calculateDiversityConfidence(memory.getEvidenceSources().size(), 0.95));
                        if (memory.getEvidenceSources().size() >= 2) {
                            memory.setStatus(MemoryStatus.ESTABLISHED);
                        }
                        memory.setLastSeenAt(LocalDateTime.now());
                        memoryRepository.save(memory);
                        enqueueOutboxProjections(memory);
                    }
                    learned++;
                } else {
                    String content = String.format(
                            "Anti-Pattern Prevention [%s]: Observed in approach '%s'. Lesson Learned: %s",
                            topic, fa.getApproach() != null ? fa.getApproach() : "Trial", fa.getLessonLearned()
                    );

                    Set<String> evidence = new HashSet<>();
                    if (fa.getId() != null) evidence.add("attempt:" + fa.getId());

                    Memory memory = Memory.builder()
                            .memoryKey(memoryKey)
                            .content(content)
                            .type(MemoryType.PROCEDURAL)
                            .scope(fa.getProject() != null ? MemoryScope.PROJECT : MemoryScope.GLOBAL)
                            .project(fa.getProject())
                            .repository(fa.getRepository())
                            .status(MemoryStatus.CONFIRMED)
                            .confidence(0.92)
                            .importance(0.90)
                            .observationCount(1)
                            .evidenceCount(1)
                            .evidenceSources(evidence)
                            .provenanceSource("AGENT_EXPERIENCE")
                            .tags(new HashSet<>(Set.of(topic.toLowerCase(), "anti-pattern", "failure-prevention", "learned-rule")))
                            .firstSeenAt(fa.getCreatedAt() != null ? fa.getCreatedAt() : LocalDateTime.now())
                            .lastSeenAt(LocalDateTime.now())
                            .build();

                    Memory saved = memoryRepository.save(memory);
                    enqueueOutboxProjections(saved);
                    learned++;
                }
            }
        }

        checkpoint.setLastProcessedAt(lastAttempt.getCreatedAt());
        checkpoint.setLastProcessedId(lastAttempt.getId());
        checkpoint.setProcessedCount(checkpoint.getProcessedCount() + batch.size());
        checkpoint.setLastRunStatus("SUCCESS");
        checkpointRepository.save(checkpoint);

        return learned;
    }

    /**
     * 3. Developer Preferences & Conventions (Incremental with Composite Cursor & Evidence Diversity)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int consolidateDeveloperPreferences() {
        ConsolidationCheckpoint checkpoint = getOrCreateCheckpoint("SESSION_CURSOR");
        LocalDateTime cursor = checkpoint.getLastProcessedAt();
        UUID lastId = checkpoint.getLastProcessedId();

        List<AgentSession> batch = sessionRepository.findIncrementalSessions("COMPLETED", cursor, lastId, PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return 0;

        int learned = 0;
        AgentSession lastSession = batch.get(batch.size() - 1);

        // Group evidence by technology
        Map<String, Set<String>> techAgents = new HashMap<>();
        Map<String, Set<UUID>> techRepos = new HashMap<>();
        Map<String, Set<UUID>> techSessions = new HashMap<>();

        for (AgentSession s : batch) {
            if (s.getTask() != null) {
                String task = s.getTask().toLowerCase();
                String agentName = s.getAgent() != null ? s.getAgent().getName() : "agent";
                UUID repoId = s.getRepository() != null ? s.getRepository().getId() : null;

                trackTechEvidence("Redis", task.contains("redis"), s.getId(), agentName, repoId, techAgents, techRepos, techSessions);
                trackTechEvidence("JWT/OAuth2", task.contains("jwt") || task.contains("oauth"), s.getId(), agentName, repoId, techAgents, techRepos, techSessions);
                trackTechEvidence("Neo4j Graph", task.contains("neo4j") || task.contains("graph"), s.getId(), agentName, repoId, techAgents, techRepos, techSessions);
                trackTechEvidence("Qdrant Vector", task.contains("qdrant") || task.contains("vector"), s.getId(), agentName, repoId, techAgents, techRepos, techSessions);
            }
        }

        for (String tech : techSessions.keySet()) {
            int agentCount = techAgents.getOrDefault(tech, Set.of()).size();
            int sessionCount = techSessions.getOrDefault(tech, Set.of()).size();
            int repoCount = techRepos.getOrDefault(tech, Set.of()).size();

            // Calibrated confidence from evidence diversity
            double diversityConfidence = Math.min(0.95, 0.50 + (agentCount * 0.15) + (repoCount * 0.10) + (sessionCount * 0.05));
            String provenance = (agentCount >= 2) ? "MULTI_AGENT_CONSENSUS" : "INFERRED_AGENT_EXPERIENCE";
            MemoryStatus status = (agentCount >= 2 && sessionCount >= 2) ? MemoryStatus.ESTABLISHED : MemoryStatus.CONFIRMED;

            String memoryKey = "DEVELOPER_PREFERENCE:" + tech.toUpperCase().replaceAll("[^A-Z0-9]", "_");
            String content = String.format("Developer Preference: Standardized on %s for platform architecture across agent sessions.", tech);

            Set<String> evidenceSources = techSessions.getOrDefault(tech, Set.of()).stream()
                    .map(id -> "session:" + id)
                    .collect(Collectors.toSet());

            Optional<Memory> existingOpt = memoryRepository.findByMemoryKey(memoryKey);
            if (existingOpt.isPresent()) {
                Memory memory = existingOpt.get();
                memory.setObservationCount((memory.getObservationCount() != null ? memory.getObservationCount() : 1) + sessionCount);
                memory.getEvidenceSources().addAll(evidenceSources);
                memory.setEvidenceCount(memory.getEvidenceSources().size());
                memory.setConfidence(diversityConfidence);
                memory.setProvenanceSource(provenance);
                memory.setStatus(status);
                memory.setLastSeenAt(LocalDateTime.now());
                memoryRepository.save(memory);
                enqueueOutboxProjections(memory);
                learned++;
            } else {
                Memory memory = Memory.builder()
                        .memoryKey(memoryKey)
                        .content(content)
                        .type(MemoryType.PREFERENCE)
                        .scope(MemoryScope.GLOBAL)
                        .status(status)
                        .confidence(diversityConfidence)
                        .importance(0.80)
                        .observationCount(sessionCount)
                        .evidenceCount(evidenceSources.size())
                        .evidenceSources(evidenceSources)
                        .provenanceSource(provenance)
                        .tags(new HashSet<>(Set.of(tech.toLowerCase().replaceAll("[^a-z0-9]", "-"), "developer-preference", "convention")))
                        .firstSeenAt(LocalDateTime.now())
                        .lastSeenAt(LocalDateTime.now())
                        .build();

                Memory saved = memoryRepository.save(memory);
                enqueueOutboxProjections(saved);
                learned++;
            }
        }

        checkpoint.setLastProcessedAt(lastSession.getCreatedAt());
        checkpoint.setLastProcessedId(lastSession.getId());
        checkpoint.setProcessedCount(checkpoint.getProcessedCount() + batch.size());
        checkpoint.setLastRunStatus("SUCCESS");
        checkpointRepository.save(checkpoint);

        return learned;
    }

    /**
     * 4. Contradiction Resolution & Knowledge Superseding
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int resolveContradictionsAndSupersede() {
        List<Memory> activeMemories = memoryRepository.findByStatus(MemoryStatus.CONFIRMED);
        activeMemories.addAll(memoryRepository.findByStatus(MemoryStatus.OBSERVED));
        activeMemories.addAll(memoryRepository.findByStatus(MemoryStatus.ESTABLISHED));
        int resolved = 0;

        for (int i = 0; i < activeMemories.size(); i++) {
            Memory m1 = activeMemories.get(i);
            if (m1.getStatus() == MemoryStatus.SUPERSEDED || m1.getStatus() == MemoryStatus.DEPRECATED) continue;

            for (int j = i + 1; j < activeMemories.size(); j++) {
                Memory m2 = activeMemories.get(j);
                if (m2.getStatus() == MemoryStatus.SUPERSEDED || m2.getStatus() == MemoryStatus.DEPRECATED) continue;

                if (isContradiction(m1.getContent(), m2.getContent())) {
                    if (m1.getLastSeenAt() != null && m2.getLastSeenAt() != null && m2.getLastSeenAt().isAfter(m1.getLastSeenAt())) {
                        m1.setStatus(MemoryStatus.SUPERSEDED);
                        m1.setSupersededBy(m2.getId());
                        m1.setSupersededAt(LocalDateTime.now());
                        m1.setConfidence(Math.max(0.1, (m1.getConfidence() != null ? m1.getConfidence() : 0.5) * 0.5));
                        memoryRepository.save(m1);
                        enqueueOutboxProjections(m1);
                        resolved++;
                    } else if (m2.getLastSeenAt() != null && m1.getLastSeenAt() != null && m1.getLastSeenAt().isAfter(m2.getLastSeenAt())) {
                        m2.setStatus(MemoryStatus.SUPERSEDED);
                        m2.setSupersededBy(m1.getId());
                        m2.setSupersededAt(LocalDateTime.now());
                        m2.setConfidence(Math.max(0.1, (m2.getConfidence() != null ? m2.getConfidence() : 0.5) * 0.5));
                        memoryRepository.save(m2);
                        enqueueOutboxProjections(m2);
                        resolved++;
                    }
                }
            }
        }
        return resolved;
    }

    /**
     * 5. Confidence Compounding
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int compoundConfidence() {
        List<Memory> memories = memoryRepository.findByStatus(MemoryStatus.CONFIRMED);
        memories.addAll(memoryRepository.findByStatus(MemoryStatus.ESTABLISHED));
        int compounded = 0;

        for (Memory m : memories) {
            if (m.getObservationCount() != null && m.getObservationCount() >= 3) {
                double currentConf = m.getConfidence() != null ? m.getConfidence() : 0.5;
                double newConf = Math.min(0.99, currentConf + 0.05);
                m.setConfidence(newConf);
                m.setLastConfirmedAt(LocalDateTime.now());
                memoryRepository.save(m);
                compounded++;
            }
        }
        return compounded;
    }

    /**
     * 6. Memory Decay for Stale Unreinforced Items
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int decayStaleMemories() {
        List<Memory> memories = memoryRepository.findAll();
        int decayed = 0;
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        for (Memory m : memories) {
            if (m.getStatus() != MemoryStatus.SUPERSEDED && m.getStatus() != MemoryStatus.DEPRECATED) {
                if (m.getLastSeenAt() != null && m.getLastSeenAt().isBefore(thirtyDaysAgo) && (m.getObservationCount() == null || m.getObservationCount() <= 2)) {
                    double currentConf = m.getConfidence() != null ? m.getConfidence() : 0.5;
                    double newConf = Math.max(0.1, currentConf * 0.85);
                    m.setConfidence(newConf);
                    if (newConf < 0.25) {
                        m.setStatus(MemoryStatus.DEPRECATED);
                    }
                    memoryRepository.save(m);
                    decayed++;
                }
            }
        }
        return decayed;
    }

    /**
     * 7. Hotspot Code Activity Consolidation (Incremental & Database-Backed)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int consolidateHotspots() {
        ConsolidationCheckpoint checkpoint = getOrCreateCheckpoint("EVENT_CURSOR");
        LocalDateTime cursor = checkpoint.getLastProcessedAt();
        UUID lastId = checkpoint.getLastProcessedId();

        List<AgentEvent> batch = agentEventRepository.findIncrementalEvents(cursor, lastId, PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return 0;

        int consolidated = 0;
        AgentEvent lastEvent = batch.get(batch.size() - 1);
        Map<String, List<AgentEvent>> fileEvents = new HashMap<>();

        for (AgentEvent event : batch) {
            if (event.getFilePath() != null && !event.getFilePath().isBlank()) {
                fileEvents.computeIfAbsent(event.getFilePath(), k -> new ArrayList<>()).add(event);
            }
        }

        for (Map.Entry<String, List<AgentEvent>> entry : fileEvents.entrySet()) {
            String filePath = entry.getKey();
            List<AgentEvent> events = entry.getValue();

            if (events.size() >= 2) {
                String memoryKey = "HOTSPOT:" + filePath.replaceAll("[^A-Za-z0-9_./-]", "_");
                String content = String.format("Hotspot File: %s modified %d times across recent agent sessions.", filePath, events.size());

                Set<String> evidence = events.stream()
                        .map(e -> "event:" + e.getId())
                        .collect(Collectors.toSet());

                Optional<Memory> existingOpt = memoryRepository.findByMemoryKey(memoryKey);
                if (existingOpt.isPresent()) {
                    Memory memory = existingOpt.get();
                    memory.setObservationCount((memory.getObservationCount() != null ? memory.getObservationCount() : 1) + events.size());
                    memory.getEvidenceSources().addAll(evidence);
                    memory.setEvidenceCount(memory.getEvidenceSources().size());
                    memory.setLastSeenAt(LocalDateTime.now());
                    memoryRepository.save(memory);
                    enqueueOutboxProjections(memory);
                    consolidated++;
                } else {
                    Memory memory = Memory.builder()
                            .memoryKey(memoryKey)
                            .content(content)
                            .type(MemoryType.PROCEDURAL)
                            .scope(MemoryScope.GLOBAL)
                            .status(MemoryStatus.OBSERVED)
                            .confidence(0.85)
                            .importance(0.75)
                            .observationCount(events.size())
                            .evidenceCount(evidence.size())
                            .evidenceSources(evidence)
                            .provenanceSource("AGENT_EXPERIENCE")
                            .tags(new HashSet<>(Set.of("hotspot", "code-activity", "agent-event")))
                            .firstSeenAt(LocalDateTime.now())
                            .lastSeenAt(LocalDateTime.now())
                            .build();

                    Memory saved = memoryRepository.save(memory);
                    enqueueOutboxProjections(saved);
                    consolidated++;
                }
            }
        }

        checkpoint.setLastProcessedAt(lastEvent.getCreatedAt());
        checkpoint.setLastProcessedId(lastEvent.getId());
        checkpoint.setProcessedCount(checkpoint.getProcessedCount() + batch.size());
        checkpoint.setLastRunStatus("SUCCESS");
        checkpointRepository.save(checkpoint);

        return consolidated;
    }

    private ConsolidationCheckpoint getOrCreateCheckpoint(String key) {
        return checkpointRepository.findByCheckpointKey(key)
                .orElseGet(() -> checkpointRepository.save(ConsolidationCheckpoint.builder()
                        .checkpointKey(key)
                        .processedCount(0L)
                        .lastRunStatus("INITIALIZED")
                        .build()));
    }

    private boolean hasStrongSemanticClusterMatches(String topic, String projectKey) {
        try {
            List<SearchResult> results = semanticSearchService.searchScoped(
                    topic, "technical_memory", projectKey.equals("GLOBAL") ? null : projectKey, null, 5
            );
            // Requires at least 2 supporting semantic memories with high similarity (> 0.88)
            long strongMatches = results.stream().filter(r -> r.getScore() > 0.88f).count();
            return strongMatches >= 2;
        } catch (Exception e) {
            return false;
        }
    }

    private double calculateDiversityConfidence(int evidenceCount, double baseConfidence) {
        return Math.min(0.98, baseConfidence + Math.log1p(evidenceCount) * 0.03);
    }

    private void trackTechEvidence(String tech, boolean matched, UUID sessionId, String agent, UUID repo,
                                   Map<String, Set<String>> agents, Map<String, Set<UUID>> repos, Map<String, Set<UUID>> sessions) {
        if (!matched) return;
        agents.computeIfAbsent(tech, k -> new HashSet<>()).add(agent);
        sessions.computeIfAbsent(tech, k -> new HashSet<>()).add(sessionId);
        if (repo != null) repos.computeIfAbsent(tech, k -> new HashSet<>()).add(repo);
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

    private String extractMainTopic(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase();
        if (lower.contains("redis")) return "Redis";
        if (lower.contains("jwt") || lower.contains("token")) return "JWT-Auth";
        if (lower.contains("postgres") || lower.contains("sql") || lower.contains("pool")) return "Database-Pooling";
        if (lower.contains("neo4j") || lower.contains("graph")) return "Neo4j-Graph";
        if (lower.contains("qdrant") || lower.contains("vector")) return "Vector-Search";
        if (lower.contains("in-memory") || lower.contains("blacklist")) return "Distributed-State";
        return null;
    }

    private boolean isContradiction(String c1, String c2) {
        if (c1 == null || c2 == null) return false;
        String s1 = c1.toLowerCase();
        String s2 = c2.toLowerCase();

        if ((s1.contains("use ") && s2.contains("do not use ")) ||
                (s1.contains("deprecated") && s2.contains("standard")) ||
                (s1.contains("replaced by") && s2.contains("primary")) ||
                (s1.contains("in-memory") && s2.contains("redis"))) {
            return true;
        }
        return false;
    }
}
