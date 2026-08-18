package com.secondbrain;

import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.*;
import com.secondbrain.common.repository.*;
import com.secondbrain.service.MemoryConsolidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class MemoryConsolidationServiceIntegrationTest {

    @Autowired
    private MemoryConsolidationService consolidationService;

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private AgentAttemptRepository attemptRepository;

    @Autowired
    private AgentSessionRepository sessionRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RepositoryEntityRepository repositoryRepository;

    @Autowired
    private AgentOutboxRepository outboxRepository;

    private Project testProject;
    private RepositoryEntity testRepo;
    private Agent testAgent;

    @BeforeEach
    void setUp() {
        testProject = projectRepository.save(Project.builder()
                .name("CoreBanking")
                .description("Distributed Core Banking")
                .path("/workspace/CoreBanking")
                .status("active")
                .build());

        testRepo = repositoryRepository.save(RepositoryEntity.builder()
                .name("auth-service")
                .path("/workspace/auth-service")
                .url("https://github.com/org/auth-service")
                .project(testProject)
                .build());

        testAgent = agentRepository.save(Agent.builder()
                .name("Claude Code")
                .type("CLAUDE_CODE")
                .build());
    }

    @Test
    @DisplayName("Autonomous Consolidation: Synthesizes architectural standards, failure anti-patterns, and developer preferences")
    void testAutonomousConsolidationFullCycle() {
        // 1. Seed 2 recurring Redis architectural decisions
        decisionRepository.save(Decision.builder()
                .title("Redis Sliding Window Token Blacklist")
                .rationale("Distributed cluster support")
                .repository(testRepo)
                .project(testProject)
                .status("APPROVED")
                .build());

        decisionRepository.save(Decision.builder()
                .title("Redis Distributed Session State")
                .rationale("Horizontal scaling")
                .repository(testRepo)
                .project(testProject)
                .status("APPROVED")
                .build());

        // 2. Seed a failed attempt with a lesson learned
        attemptRepository.save(AgentAttempt.builder()
                .agentName("Claude Code")
                .taskDescription("Cluster invalidation test")
                .approach("In-memory blacklist")
                .status("FAILED")
                .errorMessage("Pod B accepted invalidated token")
                .lessonLearned("Distributed store with Redis TTL required")
                .repository(testRepo)
                .project(testProject)
                .build());

        // 3. Seed an active session with Redis task
        sessionRepository.save(AgentSession.builder()
                .agent(testAgent)
                .repository(testRepo)
                .project(testProject)
                .task("Implement Redis token revocation")
                .status(AgentSessionStatus.COMPLETED.name())
                .build());

        // 4. Seed an old memory that contradicts Redis
        Memory oldMemory = memoryRepository.save(Memory.builder()
                .content("Use in-memory blacklist for simple single-pod deployments")
                .type(MemoryType.ARCHITECTURAL)
                .scope(MemoryScope.PROJECT)
                .project(testProject)
                .status(MemoryStatus.CONFIRMED)
                .confidence(0.70)
                .firstSeenAt(LocalDateTime.now().minusDays(60))
                .lastSeenAt(LocalDateTime.now().minusDays(50))
                .build());

        // Run full autonomous consolidation cycle
        Map<String, Object> report = consolidationService.runConsolidationCycle();

        assertThat(report).isNotNull();
        assertThat(report.get("status")).isEqualTo("success");
        assertThat((Integer) report.get("decisionsSynthesized")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) report.get("antiPatternsLearned")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) report.get("preferencesLearned")).isGreaterThanOrEqualTo(1);

        // Verify synthesized memories in DB
        List<Memory> allMemories = memoryRepository.findAll();

        // 1. Architectural standard for Redis
        assertThat(allMemories.stream().anyMatch(m ->
                m.getType() == MemoryType.ARCHITECTURAL &&
                m.getContent().contains("Architectural Standard [Redis]")
        )).isTrue();

        // 2. Anti-pattern prevention rule
        assertThat(allMemories.stream().anyMatch(m ->
                m.getType() == MemoryType.PROCEDURAL &&
                m.getContent().contains("Anti-Pattern Prevention [Redis]")
        )).isTrue();

        // 3. Developer preference
        assertThat(allMemories.stream().anyMatch(m ->
                m.getType() == MemoryType.PREFERENCE &&
                m.getContent().contains("Developer Preference: Standardized on Redis")
        )).isTrue();

        // 4. Contradiction resolution: old in-memory rule superseded
        Memory updatedOld = memoryRepository.findById(oldMemory.getId()).orElseThrow();
        assertThat(updatedOld.getStatus()).isEqualTo(MemoryStatus.SUPERSEDED);

        // 5. Outbox projections verified
        long outboxCount = outboxRepository.count();
        assertThat(outboxCount).isGreaterThan(0);
    }
}
