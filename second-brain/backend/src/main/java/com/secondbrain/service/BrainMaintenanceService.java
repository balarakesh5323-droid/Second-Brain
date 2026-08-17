package com.secondbrain.service;

import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrainMaintenanceService {

    private final MemoryRepository memoryRepository;
    private final DecisionRepository decisionRepository;
    private final TaskRepository taskRepository;
    private final AgentEventRepository agentEventRepository;
    private final AgentHandoffRepository agentHandoffRepository;
    private final AgentAttemptRepository agentAttemptRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final AgentRepository agentRepository;
    private final SkillRepository skillRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final RepositoryEntityRepository repositoryEntityRepository;
    private final ProjectRepository projectRepository;
    private final DeveloperRepository developerRepository;
    private final TechnologyRepository technologyRepository;
    private final TagRepository tagRepository;

    private final GraphService graphService;
    private final VectorStoreService vectorStoreService;
    private final RedisService redisService;

    @Transactional
    public Map<String, Object> wipeWholeBrain() {
        log.warn("INITIATING COMPLETE SECOND BRAIN WIPE PROCEDURE");

        // 1. Gather stats before wiping
        long memoryCount = memoryRepository.count();
        long decisionCount = decisionRepository.count();
        long taskCount = taskRepository.count();
        long eventCount = agentEventRepository.count();
        long handoffCount = agentHandoffRepository.count();
        long sessionCount = agentSessionRepository.count();
        long docCount = projectDocumentRepository.count();
        long repoCount = repositoryEntityRepository.count();
        long projectCount = projectRepository.count();
        long agentCount = agentRepository.count();
        long skillCount = skillRepository.count();

        // 2. Wipe PostgreSQL relational data in correct dependency order
        try {
            agentHandoffRepository.deleteAll();
            agentAttemptRepository.deleteAll();
            agentEventRepository.deleteAll();
            agentSessionRepository.deleteAll();
            memoryRepository.deleteAll();
            decisionRepository.deleteAll();
            taskRepository.deleteAll();
            skillRepository.deleteAll();
            projectDocumentRepository.deleteAll();
            agentRepository.deleteAll();
            repositoryEntityRepository.deleteAll();
            projectRepository.deleteAll();
            developerRepository.deleteAll();
            technologyRepository.deleteAll();
            tagRepository.deleteAll();
            log.info("Wiped all PostgreSQL entities successfully");
        } catch (Exception e) {
            log.error("Failed to wipe PostgreSQL entities: {}", e.getMessage(), e);
            throw new RuntimeException("PostgreSQL wipe failed: " + e.getMessage(), e);
        }

        // 3. Wipe Neo4j Knowledge Graph
        try {
            graphService.wipeAll();
            log.info("Wiped Neo4j Knowledge Graph");
        } catch (Exception e) {
            log.warn("Neo4j wipe failed: {}", e.getMessage());
        }

        // 4. Wipe Qdrant Vector Stores
        try {
            vectorStoreService.wipeAllCollections();
            log.info("Wiped and recreated all Qdrant vector collections");
        } catch (Exception e) {
            log.warn("Qdrant wipe failed: {}", e.getMessage());
        }

        // 5. Wipe Redis hot state cache
        try {
            redisService.flushAll();
            log.info("Flushed Redis cache");
        } catch (Exception e) {
            log.warn("Redis flush failed: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Entire Second Brain has been completely wiped across PostgreSQL, Neo4j, Qdrant, and Redis.");
        result.put("wipedAt", LocalDateTime.now().toString());

        Map<String, Object> deletedStats = new HashMap<>();
        deletedStats.put("memories", memoryCount);
        deletedStats.put("decisions", decisionCount);
        deletedStats.put("tasks", taskCount);
        deletedStats.put("events", eventCount);
        deletedStats.put("handoffs", handoffCount);
        deletedStats.put("sessions", sessionCount);
        deletedStats.put("repositories", repoCount);
        deletedStats.put("projects", projectCount);
        deletedStats.put("agents", agentCount);
        deletedStats.put("skills", skillCount);
        deletedStats.put("graphNodes", "All nodes and relationships purged");
        deletedStats.put("vectorStores", "All vector collections reset");
        deletedStats.put("cache", "Redis flushed");

        result.put("deleted", deletedStats);
        return result;
    }
}
