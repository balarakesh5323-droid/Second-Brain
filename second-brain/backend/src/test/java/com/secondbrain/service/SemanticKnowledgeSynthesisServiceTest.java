package com.secondbrain.service;

import com.secondbrain.common.dto.KnowledgeProposal;
import com.secondbrain.common.entity.Decision;
import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.AgentAttemptRepository;
import com.secondbrain.common.repository.DecisionRepository;
import com.secondbrain.common.repository.MemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticKnowledgeSynthesisServiceTest {

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private AgentAttemptRepository attemptRepository;

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private GraphService graphService;

    @Mock
    private LlmSynthesisEngine llmSynthesisEngine;

    @Mock
    private OutboxProjectionService outboxService;

    private SemanticKnowledgeSynthesisService synthesisService;

    @BeforeEach
    void setUp() {
        synthesisService = new SemanticKnowledgeSynthesisService(
                memoryRepository,
                decisionRepository,
                attemptRepository,
                semanticSearchService,
                graphService,
                llmSynthesisEngine,
                outboxService
        );
    }

    @Test
    @DisplayName("AI Knowledge Synthesis: Synthesizes architectural standard with evidence validation and supersedes older memory")
    void testSynthesizeAndPromoteArchitecturalKnowledge() {
        UUID projId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        UUID decId1 = UUID.randomUUID();
        UUID decId2 = UUID.randomUUID();
        UUID fakeDecId = UUID.randomUUID(); // Hallucinated ID by LLM

        Project project = Project.builder().name("CoreBanking").build();
        project.setId(projId);

        RepositoryEntity repo = RepositoryEntity.builder().name("auth-service").project(project).build();
        repo.setId(repoId);

        Decision d1 = Decision.builder().title("Redis Sliding Window Token Blacklist").build();
        d1.setId(decId1);
        d1.setCreatedAt(LocalDateTime.now().minusDays(1));

        Decision d2 = Decision.builder().title("Redis Distributed Cluster Tokens").build();
        d2.setId(decId2);
        d2.setCreatedAt(LocalDateTime.now().minusHours(2));

        // Mock DB checks for anti-hallucination
        when(decisionRepository.existsById(decId1)).thenReturn(true);
        when(decisionRepository.existsById(decId2)).thenReturn(true);
        when(decisionRepository.existsById(fakeDecId)).thenReturn(false); // Fake ID rejected

        // Mock LLM Proposal containing valid and fake evidence
        KnowledgeProposal proposal = KnowledgeProposal.builder()
                .memoryKey("ARCHITECTURAL_STANDARD:" + projId + ":REDIS")
                .knowledge("Redis Sliding Window Token Revocation is the standard strategy.")
                .type(MemoryType.ARCHITECTURAL)
                .status(MemoryStatus.ESTABLISHED)
                .confidence(0.94)
                .evidenceSources(Set.of("decision:" + decId1, "decision:" + decId2, "decision:" + fakeDecId))
                .supersedesMemoryKeys(Set.of("ARCHITECTURAL_STANDARD:" + projId + ":IN_MEMORY"))
                .projectKey(projId.toString())
                .reasoning("Consolidated across distributed authentication services.")
                .build();

        when(llmSynthesisEngine.synthesizeArchitecturalProposal(eq("Redis"), eq(projId.toString()), any(), any(), any()))
                .thenReturn(Optional.of(proposal));

        when(memoryRepository.findByMemoryKey("ARCHITECTURAL_STANDARD:" + projId + ":REDIS"))
                .thenReturn(Optional.empty());

        when(memoryRepository.save(any(Memory.class)))
                .thenAnswer(inv -> {
                    Memory m = inv.getArgument(0);
                    if (m.getId() == null) m.setId(UUID.randomUUID());
                    return m;
                });

        // Mock old memory that gets superseded
        Memory oldMemory = Memory.builder()
                .memoryKey("ARCHITECTURAL_STANDARD:" + projId + ":IN_MEMORY")
                .content("Use in-memory store")
                .status(MemoryStatus.CONFIRMED)
                .confidence(0.70)
                .build();
        oldMemory.setId(UUID.randomUUID());

        when(memoryRepository.findByMemoryKey("ARCHITECTURAL_STANDARD:" + projId + ":IN_MEMORY"))
                .thenReturn(Optional.of(oldMemory));

        // Execute synthesis
        Optional<Memory> result = synthesisService.synthesizeAndPromoteArchitecturalKnowledge(
                "Redis", projId.toString(), List.of(d1, d2), project, repo
        );

        assertThat(result).isPresent();
        Memory synthesized = result.get();
        assertThat(synthesized.getMemoryKey()).isEqualTo("ARCHITECTURAL_STANDARD:" + projId + ":REDIS");
        assertThat(synthesized.getStatus()).isEqualTo(MemoryStatus.ESTABLISHED);
        assertThat(synthesized.getConfidence()).isEqualTo(0.94);

        // Anti-hallucination verified: fake ID excluded, only real decisions retained
        assertThat(synthesized.getEvidenceSources()).containsExactlyInAnyOrder("decision:" + decId1, "decision:" + decId2);
        assertThat(synthesized.getEvidenceSources()).doesNotContain("decision:" + fakeDecId);

        // Superseding verified
        assertThat(oldMemory.getStatus()).isEqualTo(MemoryStatus.SUPERSEDED);
        assertThat(oldMemory.getSupersededBy()).isEqualTo(synthesized.getId());
    }
}
