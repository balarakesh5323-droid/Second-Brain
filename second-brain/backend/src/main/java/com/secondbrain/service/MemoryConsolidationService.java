package com.secondbrain.service;

import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.*;
import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryConsolidationService {

    private final MemoryRepository memoryRepository;
    private final AgentEventRepository agentEventRepository;
    private final DecisionRepository decisionRepository;
    private final AgentAttemptRepository attemptRepository;
    private final AgentSessionRepository sessionRepository;
    private final OutboxProjectionService outboxService;

    /**
     * Autonomous consolidation cycle: runs daily at 2:00 AM, or on-demand via API / MCP.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public Map<String, Object> runConsolidationCycle() {
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
    }

    /**
     * 1. Architectural Decision Learning:
     * Discovers recurring decision patterns across repositories/sessions and synthesizes durable architectural knowledge.
     */
    @Transactional
    public int consolidateArchitecturalDecisions() {
        List<Decision> decisions = decisionRepository.findAll();
        if (decisions.isEmpty()) return 0;

        int synthesized = 0;
        Map<String, List<Decision>> topicClusters = new HashMap<>();

        for (Decision d : decisions) {
            String title = d.getTitle() != null ? d.getTitle() : "";
            String topic = extractMainTopic(title);
            if (topic != null) {
                topicClusters.computeIfAbsent(topic, k -> new ArrayList<>()).add(d);
            }
        }

        for (Map.Entry<String, List<Decision>> entry : topicClusters.entrySet()) {
            String topic = entry.getKey();
            List<Decision> cluster = entry.getValue();

            // When a pattern is repeated across 2 or more decisions, synthesize long-term architectural rule
            if (cluster.size() >= 2) {
                String memoryContent = String.format(
                        "Architectural Standard [%s]: Established across %d decisions. Pattern: %s",
                        topic, cluster.size(), cluster.get(0).getTitle()
                );

                boolean alreadyExists = memoryRepository.findAll().stream()
                        .anyMatch(m -> m.getContent() != null && m.getContent().contains("Architectural Standard [" + topic + "]"));

                if (!alreadyExists) {
                    Decision sample = cluster.get(0);
                    Memory memory = Memory.builder()
                            .content(memoryContent)
                            .type(MemoryType.ARCHITECTURAL)
                            .scope(sample.getProject() != null ? MemoryScope.PROJECT : MemoryScope.GLOBAL)
                            .project(sample.getProject())
                            .repository(sample.getRepository())
                            .status(MemoryStatus.CONFIRMED)
                            .confidence(0.92)
                            .importance(0.88)
                            .observationCount(cluster.size())
                            .tags(new HashSet<>(Set.of(topic.toLowerCase(), "architectural-standard", "consolidated")))
                            .firstSeenAt(LocalDateTime.now().minusDays(7))
                            .lastSeenAt(LocalDateTime.now())
                            .build();

                    Memory saved = memoryRepository.save(memory);
                    enqueueOutboxProjections(saved);
                    synthesized++;
                }
            }
        }
        return synthesized;
    }

    /**
     * 2. Failure & Anti-Pattern Learning:
     * Derives concrete prevention rules from repeated failed attempts and lessons learned.
     */
    @Transactional
    public int consolidateFailureAntiPatterns() {
        List<AgentAttempt> failures = new ArrayList<>(attemptRepository.findByStatus("FAILED"));
        failures.addAll(attemptRepository.findByStatus("FAILURE"));
        if (failures.isEmpty()) return 0;

        int learned = 0;
        Map<String, List<AgentAttempt>> failureClusters = new HashMap<>();

        for (AgentAttempt fa : failures) {
            String lesson = fa.getLessonLearned();
            if (lesson != null && !lesson.isBlank()) {
                String key = extractMainTopic(lesson);
                if (key != null) {
                    failureClusters.computeIfAbsent(key, k -> new ArrayList<>()).add(fa);
                }
            }
        }

        for (Map.Entry<String, List<AgentAttempt>> entry : failureClusters.entrySet()) {
            String topic = entry.getKey();
            List<AgentAttempt> cluster = entry.getValue();

            AgentAttempt sample = cluster.get(0);
            String content = String.format(
                    "Anti-Pattern Prevention [%s]: Observed in approach '%s'. Lesson Learned: %s",
                    topic, sample.getApproach() != null ? sample.getApproach() : "Trial", sample.getLessonLearned()
            );

            boolean alreadyExists = memoryRepository.findAll().stream()
                    .anyMatch(m -> m.getContent() != null && m.getContent().contains("Anti-Pattern Prevention [" + topic + "]"));

            if (!alreadyExists) {
                Memory memory = Memory.builder()
                        .content(content)
                        .type(MemoryType.PROCEDURAL)
                        .scope(sample.getProject() != null ? MemoryScope.PROJECT : MemoryScope.GLOBAL)
                        .project(sample.getProject())
                        .repository(sample.getRepository())
                        .status(MemoryStatus.CONFIRMED)
                        .confidence(0.95)
                        .importance(0.92)
                        .observationCount(cluster.size())
                        .tags(new HashSet<>(Set.of(topic.toLowerCase(), "anti-pattern", "failure-prevention", "learned-rule")))
                        .firstSeenAt(LocalDateTime.now().minusDays(3))
                        .lastSeenAt(LocalDateTime.now())
                        .build();

                Memory saved = memoryRepository.save(memory);
                enqueueOutboxProjections(saved);
                learned++;
            }
        }
        return learned;
    }

    /**
     * 3. Developer Preferences & Conventions:
     * Derives conventions from successful sessions, testing idioms, and framework choices.
     */
    @Transactional
    public int consolidateDeveloperPreferences() {
        List<AgentSession> sessions = sessionRepository.findAll();
        if (sessions.isEmpty()) return 0;

        int learned = 0;
        Set<String> observedTechnologies = new HashSet<>();

        for (AgentSession s : sessions) {
            if (s.getTask() != null) {
                String task = s.getTask().toLowerCase();
                if (task.contains("redis")) observedTechnologies.add("Redis");
                if (task.contains("jwt") || task.contains("oauth")) observedTechnologies.add("JWT/OAuth2");
                if (task.contains("neo4j") || task.contains("graph")) observedTechnologies.add("Neo4j Graph");
                if (task.contains("qdrant") || task.contains("vector")) observedTechnologies.add("Qdrant Vector Store");
            }
        }

        for (String tech : observedTechnologies) {
            String content = String.format("Developer Preference: Standardized on %s for platform architecture across agent sessions.", tech);
            boolean exists = memoryRepository.findAll().stream()
                    .anyMatch(m -> m.getContent() != null && m.getContent().contains("Developer Preference: Standardized on " + tech));

            if (!exists) {
                Memory memory = Memory.builder()
                        .content(content)
                        .type(MemoryType.PREFERENCE)
                        .scope(MemoryScope.GLOBAL)
                        .status(MemoryStatus.CONFIRMED)
                        .confidence(0.90)
                        .importance(0.80)
                        .observationCount(sessions.size())
                        .tags(new HashSet<>(Set.of(tech.toLowerCase().replaceAll("[^a-z0-9]", "-"), "developer-preference", "convention")))
                        .firstSeenAt(LocalDateTime.now())
                        .lastSeenAt(LocalDateTime.now())
                        .build();

                Memory saved = memoryRepository.save(memory);
                enqueueOutboxProjections(saved);
                learned++;
            }
        }
        return learned;
    }

    /**
     * 4. Contradiction Resolution & Superseding:
     * Detects when newer memories/decisions supersede older ones and updates statuses.
     */
    @Transactional
    public int resolveContradictionsAndSupersede() {
        List<Memory> activeMemories = memoryRepository.findAll();
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
                        m1.setConfidence(Math.max(0.1, (m1.getConfidence() != null ? m1.getConfidence() : 0.5) * 0.5));
                        memoryRepository.save(m1);
                        enqueueOutboxProjections(m1);
                        resolved++;
                    } else if (m2.getLastSeenAt() != null && m1.getLastSeenAt() != null && m1.getLastSeenAt().isAfter(m2.getLastSeenAt())) {
                        m2.setStatus(MemoryStatus.SUPERSEDED);
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
    @Transactional
    public int compoundConfidence() {
        List<Memory> memories = memoryRepository.findAll();
        int compounded = 0;

        for (Memory m : memories) {
            if (m.getStatus() == MemoryStatus.CONFIRMED || m.getStatus() == MemoryStatus.OBSERVED || m.getStatus() == MemoryStatus.NEW) {
                if (m.getObservationCount() != null && m.getObservationCount() >= 3) {
                    double currentConf = m.getConfidence() != null ? m.getConfidence() : 0.5;
                    double newConf = Math.min(0.99, currentConf + 0.05);
                    m.setConfidence(newConf);
                    m.setStatus(MemoryStatus.CONFIRMED);
                    memoryRepository.save(m);
                    compounded++;
                }
            }
        }
        return compounded;
    }

    /**
     * 6. Memory Decay for Stale Unreinforced Items
     */
    @Transactional
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
     * 7. Hotspot Code Activity Consolidation
     */
    @Transactional
    public int consolidateHotspots() {
        List<AgentEvent> recentEvents = agentEventRepository.findTop20ByOrderByCreatedAtDesc();
        if (recentEvents.isEmpty()) return 0;

        int consolidated = 0;
        Map<String, Integer> fileEditCounts = new HashMap<>();

        for (AgentEvent event : recentEvents) {
            if (event.getFilePath() != null && !event.getFilePath().isBlank()) {
                fileEditCounts.put(event.getFilePath(), fileEditCounts.getOrDefault(event.getFilePath(), 0) + 1);
            }
        }

        for (Map.Entry<String, Integer> entry : fileEditCounts.entrySet()) {
            if (entry.getValue() >= 3) {
                String content = String.format("Hotspot File: %s was modified %d times across recent agent sessions.", entry.getKey(), entry.getValue());
                boolean exists = memoryRepository.findAll().stream()
                        .anyMatch(m -> m.getContent() != null && m.getContent().contains(entry.getKey()));

                if (!exists) {
                    Memory memory = Memory.builder()
                            .content(content)
                            .type(MemoryType.PROCEDURAL)
                            .scope(MemoryScope.GLOBAL)
                            .status(MemoryStatus.OBSERVED)
                            .confidence(0.8)
                            .importance(0.7)
                            .observationCount(entry.getValue())
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
        return consolidated;
    }

    private void enqueueOutboxProjections(Memory memory) {
        if (memory == null || memory.getId() == null) return;
        try {
            outboxService.enqueue(
                    null, null, OutboxTarget.QDRANT, "MEMORY",
                    memory.getId().toString(),
                    Map.of(
                            "id", memory.getId().toString(),
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
