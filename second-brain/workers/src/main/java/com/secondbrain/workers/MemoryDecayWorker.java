package com.secondbrain.workers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MemoryDecayWorker {

    @Scheduled(fixedDelay = 86400000) // Every day
    public void applyMemoryDecay() {
        log.info("Running memory decay...");
        // TODO: Implement memory decay
        // 1. Find memories not accessed in > 90 days
        // 2. Reduce confidence score
        // 3. Mark old unused memories as deprecated
        // 4. Archive very old memories
        log.info("Memory decay completed");
    }
}
