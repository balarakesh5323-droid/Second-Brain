package com.secondbrain.workers;

import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryDecayWorker {

    private final MemoryRepository memoryRepository;

    private static final int DECAY_THRESHOLD_DAYS = 90;
    private static final int ARCHIVE_THRESHOLD_DAYS = 365;
    private static final double DECAY_CONFIDENCE_REDUCTION = 0.1;

    @Scheduled(fixedDelay = 86400000)
    public void applyMemoryDecay() {
        log.info("Running memory decay worker...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime decayThreshold = now.minusDays(DECAY_THRESHOLD_DAYS);
        LocalDateTime archiveThreshold = now.minusDays(ARCHIVE_THRESHOLD_DAYS);

        // Step 1: Find memories not accessed in > 90 days and reduce confidence
        List<Memory> staleMemories = memoryRepository.findAll().stream()
            .filter(m -> m.getLastSeenAt() != null && m.getLastSeenAt().isBefore(decayThreshold))
            .filter(m -> m.getStatus() != MemoryStatus.ARCHIVED)
            .filter(m -> m.getStatus() != MemoryStatus.SUPERSEDED)
            .toList();

        int decayed = 0;
        for (Memory memory : staleMemories) {
            double currentConfidence = memory.getConfidence() != null ? memory.getConfidence() : 0.5;
            double newConfidence = Math.max(0.1, currentConfidence - DECAY_CONFIDENCE_REDUCTION);
            memory.setConfidence(newConfidence);

            // Mark as deprecated if confidence drops below threshold
            if (newConfidence < 0.3 && memory.getStatus() != MemoryStatus.DEPRECATED) {
                memory.setStatus(MemoryStatus.DEPRECATED);
                log.debug("Marked memory as deprecated: {} (confidence: {})", memory.getId(), String.format("%.2f", newConfidence));
            }

            memoryRepository.save(memory);
            decayed++;
        }

        // Step 2: Archive very old memories (> 365 days, low confidence, low observation count)
        List<Memory> candidates = memoryRepository.findAll().stream()
            .filter(m -> m.getLastSeenAt() != null && m.getLastSeenAt().isBefore(archiveThreshold))
            .filter(m -> m.getStatus() == MemoryStatus.DEPRECATED)
            .filter(m -> m.getObservationCount() != null && m.getObservationCount() <= 2)
            .filter(m -> m.getConfidence() != null && m.getConfidence() < 0.2)
            .toList();

        int archived = 0;
        for (Memory memory : candidates) {
            memory.setStatus(MemoryStatus.ARCHIVED);
            memoryRepository.save(memory);
            archived++;
        }

        log.info("Memory decay completed: {} memories decayed, {} archived", decayed, archived);
    }
}
