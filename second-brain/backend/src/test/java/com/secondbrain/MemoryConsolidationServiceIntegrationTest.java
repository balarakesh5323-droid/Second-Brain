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
import java.util.HashSet;
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

    @Autowired
    private ConsolidationCheckpointRepository checkpointRepository;

    private Project testProject;
    private RepositoryEntity testRepo;
    private Agent testAgent1;
    private Agent testAgent2;

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

        testAgent1 = agentRepository.save(Agent.builder()
                .name("Claude Code")
                .type("CLAUDE_CODE")
                .build());

        testAgent2 = agentRepository.save(Agent.builder()
                .name("Codex")
                .type("CODEX")
                .build());
    }

    @Test
    @DisplayName("Autonomous Consolidation: Incremental synthesis with evidence links, deterministic memory keys, and checkpoints")
    void testAutonomousConsolidationFullCycle() {
        // 1. Seed 2 recurring Redis architectural decisions
        Decision d1 = decisionRepository.save(Decision.builder()
                .title("Redis Sliding Window Token Blacklist")
                .rationale("Distributed cluster support")
                .repository(testRepo)
                .project(testProject)
                .status("APPROVED")
                .build());

        Decision d2 = decisionRepository.save(Decision.builder()
                .title("Redis Distributed Session State")
                .rationale("Horizontal scaling")
                .repository(testRepo)
                .project(testProject)
                .status("APPROVED")
                .build());

        // 2. Seed a failed attempt with a lesson learned
        AgentAttempt failAttempt = attemptRepository.save(AgentAttempt.builder()
                .agentName("Claude Code")
                .taskDescription("Cluster invalidation test")
                .approach("In-memory blacklist")
                .status("FAILED")
                .errorMessage("Pod B accepted invalidated token")
                .lessonLearned("Distributed store with Redis TTL required")
                .repository(testRepo)
                .project(testProject)
                .build());

        // 3. Seed multi-agent sessions demonstrating consensus on Redis
        AgentSession s1 = sessionRepository.save(AgentSession.builder()
                .agent(testAgent1)
                .repository(testRepo)
                .project(testProject)
                .task("Implement Redis token revocation")
                .status(AgentSessionStatus.COMPLETED.name())
                .build());

        AgentSession s2 = sessionRepository.save(AgentSession.builder()
                .agent(testAgent2)
                .repository(testRepo)
                .project(testProject)
                .task("Validate Redis blacklist failover")
                .status(AgentSessionStatus.COMPLETED.name())
                .build());

        // 4. Seed an old memory that contradicts Redis
        Memory oldMemory = memoryRepository.save(Memory.builder()
                .memoryKey("ARCHITECTURAL_STANDARD:" + testProject.getId() + ":OLD_IN_MEMORY")
                .content("Use in-memory blacklist for simple single-pod deployments")
                .type(MemoryType.ARCHITECTURAL)
                .scope(MemoryScope.PROJECT)
                .project(testProject)
                .status(MemoryStatus.CONFIRMED)
                .confidence(0.70)
                .firstSeenAt(LocalDateTime.now().minusDays(60))
                .lastSeenAt(LocalDateTime.now().minusDays(50))
                .tags(new HashSet<>(Set.of("in-memory", "old")))
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

        // 1. Architectural standard for Redis with memoryKey and evidence links
        Memory archStandard = allMemories.stream()
                .filter(m -> m.getType() == MemoryType.ARCHITECTURAL && m.getContent().contains("Architectural Standard [Redis]"))
                .findFirst()
                .orElseThrow();
        assertThat(archStandard.getMemoryKey()).isEqualTo("ARCHITECTURAL_STANDARD:" + testProject.getId() + ":REDIS");
        assertThat(archStandard.getEvidenceSources()).contains("decision:" + d1.getId(), "decision:" + d2.getId());
        assertThat(archStandard.getProvenanceSource()).isEqualTo("MULTI_AGENT_CONSENSUS");

        // 2. Anti-pattern prevention rule
        Memory antiPattern = allMemories.stream()
                .filter(m -> m.getType() == MemoryType.PROCEDURAL && m.getContent().contains("Anti-Pattern Prevention [Redis]"))
                .findFirst()
                .orElseThrow();
        assertThat(antiPattern.getMemoryKey()).isEqualTo("ANTI_PATTERN:" + testProject.getId() + ":REDIS");
        assertThat(antiPattern.getEvidenceSources()).contains("attempt:" + failAttempt.getId());

        // 3. Multi-Agent Developer preference (Claude + Codex consensus)
        Memory preference = allMemories.stream()
                .filter(m -> m.getType() == MemoryType.PREFERENCE && m.getContent().contains("Developer Preference: Standardized on Redis"))
                .findFirst()
                .orElseThrow();
        assertThat(preference.getMemoryKey()).isEqualTo("DEVELOPER_PREFERENCE:REDIS");
        assertThat(preference.getProvenanceSource()).isEqualTo("MULTI_AGENT_CONSENSUS");
        assertThat(preference.getConfidence()).isGreaterThanOrEqualTo(0.80);

        // 4. Contradiction resolution: old in-memory rule superseded with links
        Memory updatedOld = memoryRepository.findById(oldMemory.getId()).orElseThrow();
        assertThat(updatedOld.getStatus()).isEqualTo(MemoryStatus.SUPERSEDED);

        // 5. Checkpoints updated
        assertThat(checkpointRepository.findByCheckpointKey("DECISION_CURSOR")).isPresent();
        assertThat(checkpointRepository.findByCheckpointKey("ATTEMPT_CURSOR")).isPresent();
        assertThat(checkpointRepository.findByCheckpointKey("SESSION_CURSOR")).isPresent();

        // 6. Second consolidation cycle: incremental and deduplicated (zero duplicates created)
        int initialCount = memoryRepository.findAll().size();
        Map<String, Object> secondReport = consolidationService.runConsolidationCycle();
        assertThat(secondReport.get("status")).isEqualTo("success");
        int finalCount = memoryRepository.findAll().size();
        assertThat(finalCount).isEqualTo(initialCount);
    }
}
