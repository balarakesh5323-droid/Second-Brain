package com.secondbrain.workers;

import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContradictionDetectionWorker {

    private final MemoryRepository memoryRepository;

    // Contradiction signal words that suggest opposing conclusions
    private static final Map<String, String> CONTRADICTION_PAIRS = Map.ofEntries(
        Map.entry("use", "avoid"),
        Map.entry("should", "should not"),
        Map.entry("must", "must not"),
        Map.entry("always", "never"),
        Map.entry("prefer", "do not prefer"),
        Map.entry("include", "exclude"),
        Map.entry("enable", "disable"),
        Map.entry("add", "remove"),
        Map.entry("yes", "no"),
        Map.entry("true", "false"),
        Map.entry("support", "drop"),
        Map.entry("migrate", "stay"),
        Map.entry("postgresql", "mysql")
    );

    @Scheduled(fixedDelay = 7200000)
    public void detectContradictions() {
        log.info("Running contradiction detection...");

        List<Memory> activeMemories = memoryRepository.findAll().stream()
            .filter(m -> m.getStatus() != MemoryStatus.ARCHIVED)
            .filter(m -> m.getStatus() != MemoryStatus.SUPERSEDED)
            .filter(m -> m.getContent() != null)
            .toList();

        // Group memories by type and scope for comparison
        Map<String, List<Memory>> groups = activeMemories.stream()
            .collect(Collectors.groupingBy(m ->
                (m.getScope() != null ? m.getScope().name() : "GLOBAL") + ":" +
                (m.getProject() != null ? m.getProject().getId().toString() : "none")));

        int contradictionsFound = 0;

        for (var entry : groups.entrySet()) {
            List<Memory> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                Memory m1 = group.get(i);
                for (int j = i + 1; j < group.size(); j++) {
                    Memory m2 = group.get(j);
                    if (hasContradictionSignal(m1.getContent(), m2.getContent())) {
                        contradictionsFound++;
                        log.warn("Potential contradiction detected between memories {} and {}: \n  A: {}\n  B: {}",
                            m1.getId(), m2.getId(), m1.getContent(), m2.getContent());

                        Memory older = pickMemoryToFlag(m1, m2);
                        Memory newer = (older == m1) ? m2 : m1;
                        if (older != null && older.getStatus() != MemoryStatus.SUPERSEDED) {
                            older.setStatus(MemoryStatus.SUPERSEDED);
                            older.setSupersededBy(newer.getId());
                            older.setSupersededAt(java.time.LocalDateTime.now());
                            older.setHistoricalContext("Historically recorded: \"" + older.getContent() + "\". Superseded by: \"" + newer.getContent() + "\"");
                            memoryRepository.save(older);

                            newer.setEvidenceCount((newer.getEvidenceCount() != null ? newer.getEvidenceCount() : 1) + 1);
                            newer.setHistoricalContext("Current active standard. Supersedes historical practice: \"" + older.getContent() + "\"");
                            memoryRepository.save(newer);

                            log.info("Resolved contradiction: Superseded memory {} with newer canonical memory {}", older.getId(), newer.getId());
                        }
                    }
                }
            }
        }

        log.info("Contradiction detection completed: {} potential contradictions found", contradictionsFound);
    }

    private boolean hasContradictionSignal(String content1, String content2) {
        if (content1 == null || content2 == null) return false;

        String lower1 = content1.toLowerCase();
        String lower2 = content2.toLowerCase();

        // Check if contents are too different in length (likely different topics)
        int maxLen = Math.max(lower1.length(), lower2.length());
        int minLen = Math.min(lower1.length(), lower2.length());
        if ((double) minLen / maxLen < 0.4) return false;

        // Check for token overlap (need some shared context to be contradictory)
        Set<String> tokens1 = Set.of(lower1.split("\\s+"));
        Set<String> tokens2 = Set.of(lower2.split("\\s+"));
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);
        double overlap = (double) intersection.size() / Math.max(tokens1.size(), tokens2.size());
        if (overlap < 0.3) return false;

        // Check for contradiction signal pairs
        for (var pair : CONTRADICTION_PAIRS.entrySet()) {
            boolean m1HasFirst = lower1.contains(pair.getKey());
            boolean m1HasSecond = lower1.contains(pair.getValue());
            boolean m2HasFirst = lower2.contains(pair.getKey());
            boolean m2HasSecond = lower2.contains(pair.getValue());

            // One says "use X" and other says "avoid X" (with shared context)
            if ((m1HasFirst && m2HasSecond) || (m1HasSecond && m2HasFirst)) {
                return true;
            }
        }

        return false;
    }

    private Memory pickMemoryToFlag(Memory m1, Memory m2) {
        // Flag the one with lower confidence or older lastSeenAt
        double conf1 = m1.getConfidence() != null ? m1.getConfidence() : 0.5;
        double conf2 = m2.getConfidence() != null ? m2.getConfidence() : 0.5;

        if (conf1 < conf2) return m1;
        if (conf2 < conf1) return m2;

        // If confidence is equal, flag the older one
        if (m1.getLastSeenAt() != null && m2.getLastSeenAt() != null) {
            return m1.getLastSeenAt().isBefore(m2.getLastSeenAt()) ? m1 : m2;
        }

        return m1;
    }
}
