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
class DeduplicationWorkerTest {

    @Mock
    private MemoryRepository memoryRepository;

    @InjectMocks
    private DeduplicationWorker deduplicationWorker;

    private Memory createMemory(String content, MemoryType type, MemoryScope scope, int observations) {
        return Memory.builder()
            .content(content)
            .type(type)
            .scope(scope)
            .status(MemoryStatus.CONFIRMED)
            .confidence(0.8)
            .importance(0.5)
            .observationCount(observations)
            .lastSeenAt(LocalDateTime.now())
            .tags(new HashSet<>(Set.of("test")))
            .build();
    }

    @Test
    @DisplayName("Merges nearly identical memories and supersedes duplicates")
    void mergesDuplicateMemories() {
        Memory m1 = createMemory(
            "Use PostgreSQL for persistent data storage", MemoryType.DECLARATIVE, MemoryScope.GLOBAL, 5);
        Memory m2 = createMemory(
            "Use PostgreSQL for persistent data storage", MemoryType.DECLARATIVE, MemoryScope.GLOBAL, 3);

        when(memoryRepository.findAll()).thenReturn(List.of(m1, m2));
        when(memoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        deduplicationWorker.runDeduplication();

        // m2 should be superseded
        assertEquals(MemoryStatus.SUPERSEDED, m2.getStatus(),
            "Duplicate should be marked as superseded");

        // m1 should have merged observation count
        assertEquals(8, m1.getObservationCount(),
            "Primary memory should have merged observation count");
    }

    @Test
    @DisplayName("Does not merge memories with very different content")
    void doesNotMergeDifferentMemories() {
        Memory m1 = createMemory(
            "Use PostgreSQL for persistent data", MemoryType.DECLARATIVE, MemoryScope.GLOBAL, 5);
        Memory m2 = createMemory(
            "Redis is great for caching session data", MemoryType.DECLARATIVE, MemoryScope.GLOBAL, 3);

        when(memoryRepository.findAll()).thenReturn(List.of(m1, m2));

        deduplicationWorker.runDeduplication();

        // Neither should be superseded
        assertEquals(MemoryStatus.CONFIRMED, m1.getStatus());
        assertEquals(MemoryStatus.CONFIRMED, m2.getStatus());
    }

    @Test
    @DisplayName("Skips archived and superseded memories during dedup")
    void skipsArchivedAndSuperseded() {
        Memory archived = createMemory(
            "Old archived memory", MemoryType.DECLARATIVE, MemoryScope.GLOBAL, 1);
        archived.setStatus(MemoryStatus.ARCHIVED);

        Memory superseded = createMemory(
            "Already superseded memory", MemoryType.DECLARATIVE, MemoryScope.GLOBAL, 1);
        superseded.setStatus(MemoryStatus.SUPERSEDED);

        when(memoryRepository.findAll()).thenReturn(List.of(archived, superseded));

        deduplicationWorker.runDeduplication();

        verify(memoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Merges tags from duplicates into primary memory")
    void mergesTagsFromDuplicates() {
        Memory m1 = createMemory(
            "Use Docker for containerization", MemoryType.SEMANTIC, MemoryScope.GLOBAL, 2);
        m1.setTags(new HashSet<>(Set.of("docker", "deployment")));

        Memory m2 = createMemory(
            "Use Docker for containerization", MemoryType.SEMANTIC, MemoryScope.GLOBAL, 1);
        m2.setTags(new HashSet<>(Set.of("docker", "devops")));

        when(memoryRepository.findAll()).thenReturn(List.of(m1, m2));
        when(memoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        deduplicationWorker.runDeduplication();

        assertTrue(m1.getTags().contains("devops"),
            "Tags from duplicate should be merged into primary");
    }
}
