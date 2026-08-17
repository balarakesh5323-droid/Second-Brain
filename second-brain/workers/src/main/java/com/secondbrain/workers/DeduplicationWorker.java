package com.secondbrain.workers;

import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeduplicationWorker {

    private final MemoryRepository memoryRepository;

    private static final double SIMILARITY_THRESHOLD = 0.85;

    @Scheduled(fixedDelay = 3600000)
    public void runDeduplication() {
        log.info("Running deduplication worker...");

        List<Memory> allMemories = memoryRepository.findAll().stream()
            .filter(m -> m.getStatus() != MemoryStatus.ARCHIVED)
            .filter(m -> m.getStatus() != MemoryStatus.SUPERSEDED)
            .toList();

        // Group memories by type and scope for comparison
        Map<String, List<Memory>> groups = allMemories.stream()
            .collect(Collectors.groupingBy(m ->
                (m.getType() != null ? m.getType().name() : "UNKNOWN") + ":" +
                (m.getScope() != null ? m.getScope().name() : "UNKNOWN")));

        int merged = 0;
        Set<UUID> processed = new HashSet<>();

        for (var entry : groups.entrySet()) {
            List<Memory> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                Memory m1 = group.get(i);
                if (processed.contains(m1.getId())) continue;

                List<Memory> duplicates = new ArrayList<>();
                for (int j = i + 1; j < group.size(); j++) {
                    Memory m2 = group.get(j);
                    if (processed.contains(m2.getId())) continue;

                    if (isSimilar(m1.getContent(), m2.getContent())) {
                        duplicates.add(m2);
                    }
                }

                if (!duplicates.isEmpty()) {
                    mergeMemories(m1, duplicates);
                    duplicates.forEach(d -> processed.add(d.getId()));
                    processed.add(m1.getId());
                    merged += duplicates.size();
                }
            }
        }

        log.info("Deduplication completed: {} duplicates merged", merged);
    }

    private boolean isSimilar(String content1, String content2) {
        if (content1 == null || content2 == null) return false;
        if (content1.equals(content2)) return true;

        // Normalized Levenshtein similarity
        String c1 = content1.toLowerCase().trim();
        String c2 = content2.toLowerCase().trim();

        if (c1.equals(c2)) return true;

        // Quick length check
        int maxLen = Math.max(c1.length(), c2.length());
        if (maxLen == 0) return true;
        int lenDiff = Math.abs(c1.length() - c2.length());
        if ((double) lenDiff / maxLen > 0.3) return false;

        // Token-based Jaccard similarity for efficiency
        Set<String> tokens1 = Set.of(c1.split("\\s+"));
        Set<String> tokens2 = Set.of(c2.split("\\s+"));

        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);

        if (union.isEmpty()) return true;
        double jaccard = (double) intersection.size() / union.size();
        return jaccard >= SIMILARITY_THRESHOLD;
    }

    private void mergeMemories(Memory primary, List<Memory> duplicates) {
        // Merge: keep primary, boost its observation count, update lastSeenAt
        int totalObservations = primary.getObservationCount() != null ? primary.getObservationCount() : 1;
        LocalDateTime latestSeen = primary.getLastSeenAt();

        for (Memory dup : duplicates) {
            totalObservations += (dup.getObservationCount() != null ? dup.getObservationCount() : 1);
            if (dup.getLastSeenAt() != null &&
                (latestSeen == null || dup.getLastSeenAt().isAfter(latestSeen))) {
                latestSeen = dup.getLastSeenAt();
            }
            // Merge tags
            if (dup.getTags() != null && primary.getTags() != null) {
                primary.getTags().addAll(dup.getTags());
            }
            // Mark duplicate as superseded
            dup.setStatus(MemoryStatus.SUPERSEDED);
            memoryRepository.save(dup);
        }

        // Update primary
        primary.setObservationCount(totalObservations);
        primary.setLastSeenAt(latestSeen);
        primary.setStatus(MemoryStatus.OBSERVED);
        memoryRepository.save(primary);

        log.debug("Merged {} duplicates into memory {}", duplicates.size(), primary.getId());
    }
}
