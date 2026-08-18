package com.secondbrain.common.repository;

import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    List<Memory> findByType(MemoryType type);

    List<Memory> findByScope(MemoryScope scope);

    List<Memory> findByStatus(MemoryStatus status);

    List<Memory> findByProjectId(UUID projectId);

    List<Memory> findByRepositoryId(UUID repositoryId);

    List<Memory> findByProjectIdAndType(UUID projectId, MemoryType type);

    List<Memory> findByContentContainingIgnoreCase(String content);

    List<Memory> findTop10ByOrderByObservationCountDesc();

    List<Memory> findTop10ByOrderByLastSeenAtDesc();

    java.util.Optional<Memory> findByMemoryKey(String memoryKey);

    boolean existsByMemoryKey(String memoryKey);

    List<Memory> findByProjectIdAndStatusInOrderByConfidenceDesc(UUID projectId, List<MemoryStatus> statuses, org.springframework.data.domain.Pageable pageable);

    List<Memory> findByStatusInOrderByConfidenceDesc(List<MemoryStatus> statuses, org.springframework.data.domain.Pageable pageable);

    List<Memory> findByRepositoryIdAndStatusInOrderByConfidenceDesc(UUID repositoryId, List<MemoryStatus> statuses, org.springframework.data.domain.Pageable pageable);
}
