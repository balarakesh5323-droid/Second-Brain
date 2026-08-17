package com.secondbrain.common.repository;

import com.secondbrain.common.entity.AgentHandoff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentHandoffRepository extends JpaRepository<AgentHandoff, UUID> {

    Optional<AgentHandoff> findBySessionId(UUID sessionId);

    Optional<AgentHandoff> findFirstByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    Optional<AgentHandoff> findFirstByOrderByCreatedAtDesc();

    java.util.List<AgentHandoff> findAllByOrderByCreatedAtDesc();
}
