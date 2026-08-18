package com.secondbrain;

import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.AgentSessionStatus;
import com.secondbrain.common.repository.*;
import com.secondbrain.service.ContextPackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class ContextPackServiceIntegrationTest {

    @Autowired
    private ContextPackService contextPackService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RepositoryEntityRepository repositoryRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentSessionRepository sessionRepository;

    @Autowired
    private AgentHandoffRepository handoffRepository;

    @Autowired
    private AgentAttemptRepository attemptRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    private RepositoryEntity testRepo;
    private Project testProject;
    private Agent testAgent;

    @BeforeEach
    void setUp() {
        testProject = projectRepository.save(Project.builder()
                .name("CoreBanking")
                .description("Distributed banking platform")
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
    @DisplayName("Context Pack: Assembles complete 1-shot context pack with handoffs, failures, and warnings")
    @SuppressWarnings("unchecked")
    void testAssembleContextPackComplete() {
        // 1. Seed Active Session
        AgentSession session = sessionRepository.save(AgentSession.builder()
                .agent(testAgent)
                .repository(testRepo)
                .project(testProject)
                .task("JWT Redis Migration")
                .status(AgentSessionStatus.IN_PROGRESS.name())
                .build());

        // 2. Seed Decision
        decisionRepository.save(Decision.builder()
                .title("Redis Sliding Window Blacklist")
                .rationale("Distributed cluster support")
                .repository(testRepo)
                .project(testProject)
                .status("APPROVED")
                .build());

        // 3. Seed Failed Attempt
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

        // 4. Seed Handoff
        handoffRepository.save(AgentHandoff.builder()
                .agent(testAgent)
                .session(session)
                .repository(testRepo)
                .project(testProject)
                .task("JWT Redis Token Rotation")
                .completedItems("RedisConfig, JwtFilter")
                .inProgressItems("Integration tests")
                .nextSteps("Run multi-instance cluster test")
                .build());

        // 5. Seed Sibling Repository in same Project
        RepositoryEntity siblingRepo = repositoryRepository.save(RepositoryEntity.builder()
                .name("web-gateway")
                .path("/workspace/web-gateway")
                .url("https://github.com/org/web-gateway")
                .project(testProject)
                .build());

        // 6. Seed Sibling Repo Decision (Cross-repo intelligence)
        decisionRepository.save(Decision.builder()
                .title("Gateway Redis Token Validation")
                .rationale("Shared token blacklist in reverse proxy")
                .repository(siblingRepo)
                .project(testProject)
                .status("APPROVED")
                .build());

        // Assemble 1-Shot Context Pack
        Map<String, Object> pack = contextPackService.assembleContextPack(
                "Verify Redis token blacklist under multi-instance load",
                testRepo.getId().toString(),
                testProject.getId().toString()
        );

        assertThat(pack).isNotNull();
        assertThat(pack.get("task")).isEqualTo("Verify Redis token blacklist under multi-instance load");

        // Verify Repository Status
        Map<String, Object> repoInfo = (Map<String, Object>) pack.get("repository");
        assertThat(repoInfo.get("name")).isEqualTo("auth-service");

        // Verify Sibling Repositories Awareness
        List<Map<String, Object>> siblingRepos = (List<Map<String, Object>>) pack.get("siblingRepositories");
        assertThat(siblingRepos).isNotEmpty();
        assertThat(siblingRepos.get(0).get("name")).isEqualTo("web-gateway");

        // Verify Latest Handoff
        Map<String, Object> handoff = (Map<String, Object>) pack.get("latestHandoff");
        assertThat(handoff).isNotNull();
        assertThat(handoff.get("nextSteps")).isEqualTo("Run multi-instance cluster test");

        // Verify Decisions (Primary Repo + Sibling Repo)
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) pack.get("relevantDecisions");
        assertThat(decisions).isNotEmpty();
        assertThat(decisions.stream().anyMatch(d -> d.get("title").equals("Redis Sliding Window Blacklist") && d.get("scope").equals("REPOSITORY"))).isTrue();
        assertThat(decisions.stream().anyMatch(d -> d.get("title").equals("Gateway Redis Token Validation") && d.get("scope").equals("PROJECT_SIBLING"))).isTrue();

        // Verify Failures
        List<Map<String, Object>> failures = (List<Map<String, Object>>) pack.get("relevantFailures");
        assertThat(failures).isNotEmpty();
        assertThat(failures.get(0).get("approach")).isEqualTo("In-memory blacklist");
        assertThat(failures.get(0).get("lessonLearned")).isEqualTo("Distributed store with Redis TTL required");
        assertThat(failures.get(0).get("relevance")).isNotNull();
        assertThat((Double) failures.get(0).get("relevance")).isGreaterThan(0.60);
        assertThat((String) failures.get(0).get("reason")).containsIgnoringCase("blacklist");

        // Verify Automated Warnings
        List<String> warnings = (List<String>) pack.get("warnings");
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.stream().anyMatch(w -> w.contains("In-memory blacklist"))).isTrue();

        // Verify Recommended Next Actions
        List<String> recommendations = (List<String>) pack.get("recommendedNextActions");
        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0)).contains("Run multi-instance cluster test");
    }
}
