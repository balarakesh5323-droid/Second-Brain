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

    @org.springframework.data.jpa.repository.Query("SELECT d FROM Decision d LEFT JOIN FETCH d.project LEFT JOIN FETCH d.repository LEFT JOIN FETCH d.agent LEFT JOIN FETCH d.session WHERE (:after IS NULL OR d.createdAt > :after OR (d.createdAt = :after AND d.id > :lastId)) ORDER BY d.createdAt ASC, d.id ASC")
    List<Decision> findIncremental(@org.springframework.data.repository.query.Param("after") java.time.LocalDateTime after,
                                   @org.springframework.data.repository.query.Param("lastId") UUID lastId,
                                   org.springframework.data.domain.Pageable pageable);
}
