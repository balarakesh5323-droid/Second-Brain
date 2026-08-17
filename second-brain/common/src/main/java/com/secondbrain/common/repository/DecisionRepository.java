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

    List<Decision> findByStatus(String status);

    List<Decision> findTop10ByOrderByCreatedAtDesc();
}
