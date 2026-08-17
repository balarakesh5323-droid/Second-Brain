package com.secondbrain.service;

import com.secondbrain.common.entity.AgentEvent;
import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.AgentEventRepository;
import com.secondbrain.common.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryConsolidationService {

    private final MemoryRepository memoryRepository;
    private final AgentEventRepository agentEventRepository;

    /**
     * Autonomous consolidation cycle: runs daily at 2:00 AM, or on-demand via API.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public Map<String, Object> runConsolidationCycle() {
        log.info("Starting autonomous Second Brain memory consolidation cycle...");
        long startTime = System.currentTimeMillis();

        int resolvedContradictions = resolveContradictions();
        int compoundedCount = compoundConfidence();
        int decayedCount = decayStaleMemories();
        int consolidatedEvents = consolidateEpisodicEvents();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Consolidation cycle finished in {}ms: {} contradictions resolved, {} compounded, {} decayed, {} events consolidated",
            elapsed, resolvedContradictions, compoundedCount, decayedCount, consolidatedEvents);

        Map<String, Object> report = new HashMap<>();
        report.put("status", "success");
        report.put("elapsedMs", elapsed);
        report.put("contradictionsResolved", resolvedContradictions);
        report.put("memoriesCompounded", compoundedCount);
        report.put("memoriesDecayed", decayedCount);
        report.put("eventsConsolidated", consolidatedEvents);
        report.put("timestamp", LocalDateTime.now().toString());
        return report;
    }

    @Transactional
    public int resolveContradictions() {
        List<Memory> activeMemories = memoryRepository.findAll();
        int resolved = 0;

        for (int i = 0; i < activeMemories.size(); i++) {
            Memory m1 = activeMemories.get(i);
            if (m1.getStatus() == MemoryStatus.SUPERSEDED || m1.getStatus() == MemoryStatus.DEPRECATED) continue;

            for (int j = i + 1; j < activeMemories.size(); j++) {
                Memory m2 = activeMemories.get(j);
                if (m2.getStatus() == MemoryStatus.SUPERSEDED || m2.getStatus() == MemoryStatus.DEPRECATED) continue;

                // Check for contradiction keywords
                if (isContradiction(m1.getContent(), m2.getContent())) {
                    // Newer or higher observation count wins
                    if (m1.getLastSeenAt() != null && m2.getLastSeenAt() != null && m2.getLastSeenAt().isAfter(m1.getLastSeenAt())) {
                        m1.setStatus(MemoryStatus.SUPERSEDED);
                        memoryRepository.save(m1);
                        resolved++;
                    } else if (m2.getLastSeenAt() != null && m1.getLastSeenAt() != null && m1.getLastSeenAt().isAfter(m2.getLastSeenAt())) {
                        m2.setStatus(MemoryStatus.SUPERSEDED);
                        memoryRepository.save(m2);
                        resolved++;
                    }
                }
            }
        }
        return resolved;
    }

    @Transactional
    public int compoundConfidence() {
        List<Memory> memories = memoryRepository.findAll();
        int compounded = 0;

        for (Memory m : memories) {
            if (m.getStatus() == MemoryStatus.CONFIRMED || m.getStatus() == MemoryStatus.OBSERVED || m.getStatus() == MemoryStatus.NEW) {
                if (m.getObservationCount() != null && m.getObservationCount() >= 5) {
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

    @Transactional
    public int consolidateEpisodicEvents() {
        List<AgentEvent> recentEvents = agentEventRepository.findTop20ByOrderByCreatedAtDesc();
        if (recentEvents.isEmpty()) return 0;

        int consolidated = 0;
        Map<String, Integer> fileEditCounts = new HashMap<>();

        for (AgentEvent event : recentEvents) {
            if (event.getFilePath() != null && !event.getFilePath().isBlank()) {
                fileEditCounts.put(event.getFilePath(), fileEditCounts.getOrDefault(event.getFilePath(), 0) + 1);
            }
        }

        // Create procedural architectural insights for hot files
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
                        .tags(Set.of("hotspot", "code-activity", "agent-event"))
                        .firstSeenAt(LocalDateTime.now())
                        .lastSeenAt(LocalDateTime.now())
                        .build();
                    memoryRepository.save(memory);
                    consolidated++;
                }
            }
        }
        return consolidated;
    }

    private boolean isContradiction(String c1, String c2) {
        if (c1 == null || c2 == null) return false;
        String s1 = c1.toLowerCase();
        String s2 = c2.toLowerCase();

        // Pattern matching for opposing statements
        if ((s1.contains("use ") && s2.contains("do not use ")) ||
            (s1.contains("deprecated") && s2.contains("standard")) ||
            (s1.contains("replaced by") && s2.contains("primary"))) {
            return true;
        }
        return false;
    }
}
