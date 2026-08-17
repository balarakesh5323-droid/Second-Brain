package com.secondbrain.workers;

import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryScope;
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
public class KnowledgeEvolutionWorker {

    private final MemoryRepository memoryRepository;

    private static final int FREQUENT_USE_THRESHOLD = 5;
    private static final int RECENT_DAYS = 30;

    @Scheduled(fixedDelay = 86400000)
    public void evolveKnowledge() {
        log.info("Running knowledge evolution worker...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime recentThreshold = now.minusDays(RECENT_DAYS);
        List<Memory> allMemories = memoryRepository.findAll();

        int promoted = 0;
        int demoted = 0;

        for (Memory memory : allMemories) {
            if (memory.getStatus() == MemoryStatus.ARCHIVED ||
                memory.getStatus() == MemoryStatus.SUPERSEDED) {
                continue;
            }

            MemoryStatus originalStatus = memory.getStatus();
            MemoryStatus newStatus = calculateEvolvedStatus(memory, recentThreshold);

            if (newStatus != null && newStatus != originalStatus) {
                memory.setStatus(newStatus);
                memoryRepository.save(memory);

                if (isNewlyPromoted(originalStatus, newStatus)) {
                    promoted++;
                    log.debug("Promoted memory {} from {} to {}", memory.getId(), originalStatus, newStatus);
                } else if (isNewlyDemoted(originalStatus, newStatus)) {
                    demoted++;
                    log.debug("Demoted memory {} from {} to {}", memory.getId(), originalStatus, newStatus);
                }
            }

            // Boost confidence for frequently observed memories
            if (memory.getObservationCount() != null && memory.getObservationCount() >= FREQUENT_USE_THRESHOLD) {
                double currentConf = memory.getConfidence() != null ? memory.getConfidence() : 0.5;
                double boostedConf = Math.min(1.0, currentConf + 0.05);
                if (boostedConf != currentConf) {
                    memory.setConfidence(boostedConf);
                    memoryRepository.save(memory);
                }
            }
        }

        log.info("Knowledge evolution completed: {} promoted, {} demoted", promoted, demoted);
    }

    private MemoryStatus calculateEvolvedStatus(Memory memory, LocalDateTime recentThreshold) {
        int observations = memory.getObservationCount() != null ? memory.getObservationCount() : 0;
        boolean recentlyAccessed = memory.getLastSeenAt() != null && memory.getLastSeenAt().isAfter(recentThreshold);

        return switch (memory.getStatus()) {
            case NEW -> {
                if (observations >= 3) yield MemoryStatus.OBSERVED;
                yield null;
            }
            case OBSERVED -> {
                if (observations >= FREQUENT_USE_THRESHOLD && recentlyAccessed)
                    yield MemoryStatus.CONFIRMED;
                yield null;
            }
            case CONFIRMED -> {
                if (observations >= FREQUENT_USE_THRESHOLD * 2 && recentlyAccessed)
                    yield MemoryStatus.FREQUENTLY_USED;
                yield null;
            }
            case FREQUENTLY_USED -> {
                if (observations >= FREQUENT_USE_THRESHOLD * 3)
                    yield MemoryStatus.STABLE;
                // Demote if not recently used
                if (!recentlyAccessed && observations < FREQUENT_USE_THRESHOLD)
                    yield MemoryStatus.CONFIRMED;
                yield null;
            }
            case STABLE -> {
                if (!recentlyAccessed && observations < FREQUENT_USE_THRESHOLD)
                    yield MemoryStatus.FREQUENTLY_USED;
                yield null;
            }
            default -> null;
        };
    }

    private boolean isNewlyPromoted(MemoryStatus from, MemoryStatus to) {
        int fromRank = statusRank(from);
        int toRank = statusRank(to);
        return toRank > fromRank;
    }

    private boolean isNewlyDemoted(MemoryStatus from, MemoryStatus to) {
        int fromRank = statusRank(from);
        int toRank = statusRank(to);
        return toRank < fromRank;
    }

    private int statusRank(MemoryStatus status) {
        return switch (status) {
            case NEW -> 0;
            case OBSERVED -> 1;
            case CONFIRMED -> 2;
            case FREQUENTLY_USED -> 3;
            case STABLE -> 4;
            case DEPRECATED -> -1;
            case SUPERSEDED -> -2;
            case ARCHIVED -> -3;
        };
    }
}
