package com.secondbrain.common.repository;

import com.secondbrain.common.entity.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {

    List<AgentSession> findByAgentId(UUID agentId);

    List<AgentSession> findByAgentIdOrderByStartedAtDesc(UUID agentId);

    List<AgentSession> findByAgentIdAndProjectIdAndRepositoryIdOrderByStartedAtDesc(UUID agentId, UUID projectId, UUID repositoryId);

    List<AgentSession> findByAgentIdAndProjectIdOrderByStartedAtDesc(UUID agentId, UUID projectId);

    List<AgentSession> findByRepositoryId(UUID repositoryId);

    List<AgentSession> findByProjectId(UUID projectId);

    List<AgentSession> findTop10ByOrderByStartedAtDesc();
}
