package com.secondbrain.common.repository;

import com.secondbrain.common.entity.AgentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentAttemptRepository extends JpaRepository<AgentAttempt, UUID> {

    @Query("SELECT a FROM AgentAttempt a WHERE a.repository.id = :repoId ORDER BY a.createdAt DESC")
    List<AgentAttempt> findByRepositoryIdOrderByCreatedAtDesc(@Param("repoId") UUID repoId);

    @Query("SELECT a FROM AgentAttempt a WHERE a.project.id = :projectId ORDER BY a.createdAt DESC")
    List<AgentAttempt> findByProjectIdOrderByCreatedAtDesc(@Param("projectId") UUID projectId);

    @Query("SELECT a FROM AgentAttempt a WHERE a.session.id = :sessionId ORDER BY a.createdAt DESC")
    List<AgentAttempt> findBySessionIdOrderByCreatedAtDesc(@Param("sessionId") UUID sessionId);

    @Query("SELECT a FROM AgentAttempt a WHERE a.agentName = :agentName ORDER BY a.createdAt DESC")
    List<AgentAttempt> findByAgentNameOrderByCreatedAtDesc(@Param("agentName") String agentName);

    @Query("SELECT a FROM AgentAttempt a ORDER BY a.createdAt DESC")
    List<AgentAttempt> findAllOrderByCreatedAtDesc();
}
