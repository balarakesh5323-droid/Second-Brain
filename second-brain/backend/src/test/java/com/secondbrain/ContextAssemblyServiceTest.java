package com.secondbrain;

import com.secondbrain.common.dto.ContextResponse;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.*;
import com.secondbrain.common.repository.*;
import com.secondbrain.service.ContextAssemblyService;
import com.secondbrain.test.TestDataFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ContextAssemblyServiceTest {

    @Autowired
    private ContextAssemblyService contextAssemblyService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private TaskRepository taskRepository;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        Project project = projectRepository.save(TestDataFactory.createProject("TestProject"));
        projectId = project.getId();

        // Seed memories
        Memory m1 = TestDataFactory.createMemory(
            "Use PostgreSQL for authentication data", MemoryType.DECLARATIVE, MemoryScope.PROJECT);
        m1.setProject(project);
        m1.setConfidence(0.9);
        m1.setImportance(0.8);
        m1.setObservationCount(10);
        memoryRepository.save(m1);

        Memory m2 = TestDataFactory.createMemory(
            "Redis is used for session caching", MemoryType.DECLARATIVE, MemoryScope.PROJECT);
        m2.setProject(project);
        m2.setConfidence(0.85);
        m2.setImportance(0.7);
        m2.setObservationCount(5);
        memoryRepository.save(m2);

        Memory m3 = TestDataFactory.createMemory(
            "Docker Compose manages all services", MemoryType.SEMANTIC, MemoryScope.GLOBAL);
        m3.setProject(project);
        m3.setConfidence(0.7);
        m3.setObservationCount(3);
        memoryRepository.save(m3);

        // Seed a decision
        Decision d = TestDataFactory.createDecision(
            "Use Stripe for payments", "Chose Stripe for payment processing");
        d.setProject(project);
        decisionRepository.save(d);

        // Seed an open task
        Task t = TestDataFactory.createTask(
            "Implement webhook handler", "Handle Stripe webhook events");
        t.setProject(project);
        taskRepository.save(t);
    }

    @Test
    @DisplayName("assembleContext returns structured response with all sections")
    void assembleContext_returnsStructuredResponse() {
        ContextResponse response = contextAssemblyService.assembleContext(
            "What database does the project use?", projectId.toString(), null);

        assertNotNull(response);
        assertEquals("TestProject", response.getProject());
        assertNotNull(response.getRelevantContext());
        assertNotNull(response.getDecisions());
        assertNotNull(response.getOpenTasks());
        assertNotNull(response.getRecentChanges());
        assertNotNull(response.getArchitecture());
        assertNotNull(response.getKnownProblems());
        assertNotNull(response.getSources());
    }

    @Test
    @DisplayName("assembleContext finds relevant memories by keyword")
    void assembleContext_findsRelevantMemories() {
        ContextResponse response = contextAssemblyService.assembleContext(
            "PostgreSQL authentication", projectId.toString(), null);

        assertFalse(response.getRelevantContext().isEmpty(),
            "Should find memories mentioning PostgreSQL");

        boolean foundPostgres = response.getRelevantContext().stream()
            .anyMatch(c -> c.getContent() != null && c.getContent().contains("PostgreSQL"));
        assertTrue(foundPostgres, "Should contain the PostgreSQL memory");
    }

    @Test
    @DisplayName("assembleContext returns decisions for project")
    void assembleContext_returnsDecisions() {
        ContextResponse response = contextAssemblyService.assembleContext(
            "payment", projectId.toString(), null);

        assertFalse(response.getDecisions().isEmpty(),
            "Should return project decisions");
        assertEquals("Use Stripe for payments", response.getDecisions().get(0).getTitle());
    }

    @Test
    @DisplayName("assembleContext returns open tasks")
    void assembleContext_returnsOpenTasks() {
        ContextResponse response = contextAssemblyService.assembleContext(
            "webhook", projectId.toString(), null);

        assertFalse(response.getOpenTasks().isEmpty(),
            "Should return open tasks");
        assertEquals("Implement webhook handler", response.getOpenTasks().get(0).getTitle());
    }

    @Test
    @DisplayName("assembleContext works without project scope")
    void assembleContext_worksWithoutProjectScope() {
        ContextResponse response = contextAssemblyService.assembleContext(
            "Redis caching", null, null);

        assertNotNull(response);
        assertNotNull(response.getRelevantContext());
        assertNotNull(response.getSources());
    }

    @Test
    @DisplayName("assembleContext includes source attribution")
    void assembleContext_includesSources() {
        ContextResponse response = contextAssemblyService.assembleContext(
            "test query", null, null);

        assertFalse(response.getSources().isEmpty(),
            "Should include source attribution");
        assertTrue(response.getSources().contains("semantic_search"));
        assertTrue(response.getSources().contains("decisions"));
    }
}
