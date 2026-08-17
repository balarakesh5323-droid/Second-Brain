package com.secondbrain.workers;

import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.MemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryDecayWorkerTest {

    @Mock
    private MemoryRepository memoryRepository;

    @InjectMocks
    private MemoryDecayWorker memoryDecayWorker;

    private Memory createMemory(MemoryStatus status, int observationCount,
                                 double confidence, LocalDateTime lastSeenAt) {
        return Memory.builder()
            .content("Test memory content")
            .type(MemoryType.DECLARATIVE)
            .scope(MemoryScope.GLOBAL)
            .status(status)
            .confidence(confidence)
            .importance(0.5)
            .observationCount(observationCount)
            .lastSeenAt(lastSeenAt)
            .tags(new HashSet<>())
            .build();
    }

    @Test
    @DisplayName("Decays old memories that haven't been accessed recently")
    void decaysOldMemories() {
        // Memory last seen 100 days ago (above 90-day threshold)
        Memory oldMemory = createMemory(
            MemoryStatus.CONFIRMED, 5, 0.8,
            LocalDateTime.now().minusDays(100));

        when(memoryRepository.findAll()).thenReturn(List.of(oldMemory));
        when(memoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        memoryDecayWorker.applyMemoryDecay();

        // Confidence should be reduced by 0.1
        assertEquals(0.7, oldMemory.getConfidence(), 0.01,
            "Confidence should be reduced for old memories");
        verify(memoryRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("Archives very old deprecated memories with low observation count")
    void archivesVeryOldDeprecatedMemories() {
        // Deprecated memory last seen 400 days ago with low observation count
        Memory deprecatedMemory = createMemory(
            MemoryStatus.DEPRECATED, 1, 0.15,
            LocalDateTime.now().minusDays(400));

        when(memoryRepository.findAll()).thenReturn(List.of(deprecatedMemory));
        when(memoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        memoryDecayWorker.applyMemoryDecay();

        assertEquals(MemoryStatus.ARCHIVED, deprecatedMemory.getStatus(),
            "Very old deprecated memories should be archived");
    }

    @Test
    @DisplayName("Does not decay recently accessed memories")
    void doesNotDecayRecentMemories() {
        // Memory last seen 5 days ago (below threshold)
        Memory recentMemory = createMemory(
            MemoryStatus.CONFIRMED, 5, 0.8,
            LocalDateTime.now().minusDays(5));

        when(memoryRepository.findAll()).thenReturn(List.of(recentMemory));

        memoryDecayWorker.applyMemoryDecay();

        assertEquals(0.8, recentMemory.getConfidence(), 0.01,
            "Recent memories should not have confidence reduced");
    }

    @Test
    @DisplayName("Skips archived and superseded memories")
    void skipsArchivedAndSupersededMemories() {
        Memory archived = createMemory(
            MemoryStatus.ARCHIVED, 1, 0.5,
            LocalDateTime.now().minusDays(200));
        Memory superseded = createMemory(
            MemoryStatus.SUPERSEDED, 1, 0.5,
            LocalDateTime.now().minusDays(200));

        when(memoryRepository.findAll()).thenReturn(List.of(archived, superseded));

        memoryDecayWorker.applyMemoryDecay();

        verify(memoryRepository, never()).save(any());
    }
}
