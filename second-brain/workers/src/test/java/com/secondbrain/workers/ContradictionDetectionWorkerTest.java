package com.secondbrain.workers;

import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.MemoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContradictionDetectionWorkerTest {

    @Mock
    private MemoryRepository memoryRepository;

    @InjectMocks
    private ContradictionDetectionWorker contradictionDetectionWorker;

    private Memory createMemory(String content, double confidence) {
        return Memory.builder()
            .content(content)
            .type(MemoryType.DECLARATIVE)
            .scope(MemoryScope.GLOBAL)
            .status(MemoryStatus.CONFIRMED)
            .confidence(confidence)
            .importance(0.5)
            .observationCount(3)
            .lastSeenAt(LocalDateTime.now())
            .tags(new HashSet<>())
            .build();
    }

    @Test
    @DisplayName("Detects contradictory memories and flags lower-confidence one")
    void detectsContradictions() {
        Memory m1 = createMemory("Use PostgreSQL for authentication data", 0.9);
        Memory m2 = createMemory("Avoid PostgreSQL for authentication data", 0.5);

        when(memoryRepository.findAll()).thenReturn(List.of(m1, m2));
        when(memoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contradictionDetectionWorker.detectContradictions();

        // Lower confidence memory should be flagged as superseded
        assertEquals(MemoryStatus.SUPERSEDED, m2.getStatus(),
            "Lower confidence contradictory memory should be superseded");
    }

    @Test
    @DisplayName("Does not flag non-contradictory memories")
    void doesNotFlagNonContradictory() {
        Memory m1 = createMemory("Use PostgreSQL for authentication data", 0.9);
        Memory m2 = createMemory("Use Redis for caching session data", 0.8);

        when(memoryRepository.findAll()).thenReturn(List.of(m1, m2));

        contradictionDetectionWorker.detectContradictions();

        assertEquals(MemoryStatus.CONFIRMED, m1.getStatus());
        assertEquals(MemoryStatus.CONFIRMED, m2.getStatus());
    }

    @Test
    @DisplayName("Skips archived and superseded memories")
    void skipsArchivedAndSuperseded() {
        Memory archived = createMemory("Use PostgreSQL for auth", 0.9);
        archived.setStatus(MemoryStatus.ARCHIVED);

        Memory superseded = createMemory("Avoid PostgreSQL for auth", 0.5);
        superseded.setStatus(MemoryStatus.SUPERSEDED);

        when(memoryRepository.findAll()).thenReturn(List.of(archived, superseded));

        contradictionDetectionWorker.detectContradictions();

        verify(memoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Handles null content gracefully")
    void handlesNullContent() {
        Memory m1 = createMemory(null, 0.9);
        Memory m2 = createMemory("Use PostgreSQL for auth", 0.8);

        when(memoryRepository.findAll()).thenReturn(List.of(m1, m2));

        contradictionDetectionWorker.detectContradictions();

        verify(memoryRepository, never()).save(any());
    }
}
