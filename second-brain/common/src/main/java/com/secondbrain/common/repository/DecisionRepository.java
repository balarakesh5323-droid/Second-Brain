package com.secondbrain.common.repository;

import com.secondbrain.common.entity.Decision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    List<Decision> findByProjectId(UUID projectId);

    List<Decision> findByRepositoryId(UUID repositoryId);

    List<Decision> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    List<Decision> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId, org.springframework.data.domain.Pageable pageable);

    List<Decision> findByProjectIdOrderByCreatedAtDesc(UUID projectId, org.springframework.data.domain.Pageable pageable);

    List<Decision> findByStatus(String status);

    List<Decision> findTop10ByOrderByCreatedAtDesc();

    List<Decision> findByCreatedAtAfterOrderByCreatedAtAsc(java.time.LocalDateTime after, org.springframework.data.domain.Pageable pageable);

    List<Decision> findAllByOrderByCreatedAtAsc(org.springframework.data.domain.Pageable pageable);
}
