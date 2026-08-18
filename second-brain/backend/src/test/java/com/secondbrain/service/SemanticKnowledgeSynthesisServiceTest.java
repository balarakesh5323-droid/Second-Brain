package com.secondbrain.service;

import com.secondbrain.common.dto.AgentProvenance;
import com.secondbrain.common.dto.KnowledgeProposal;
import com.secondbrain.common.entity.Decision;
import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.AgentAttemptRepository;
import com.secondbrain.common.repository.AgentSessionRepository;
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
import java.util.Map;
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
    private AgentSessionRepository sessionRepository;

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private GraphService graphService;

    @Mock
    private LlmSynthesisEngine llmSynthesisEngine;

    @Mock
    private OutboxProjectionService outboxService;

    private ProposalValidator proposalValidator;
    private SemanticKnowledgeSynthesisService synthesisService;

    @BeforeEach
    void setUp() {
        EvidenceConfidenceEngine confidenceEngine = new EvidenceConfidenceEngine();
        ContradictionClassifier contradictionClassifier = new ContradictionClassifier();
        proposalValidator = new ProposalValidator(
                decisionRepository,
                attemptRepository,
                sessionRepository,
                confidenceEngine,
                contradictionClassifier
        );

        synthesisService = new SemanticKnowledgeSynthesisService(
                memoryRepository,
                semanticSearchService,
                graphService,
                llmSynthesisEngine,
                proposalValidator,
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
                .confidence(0.99) // LLM claimed 0.99
                .evidenceSources(Set.of("decision:" + decId1, "decision:" + decId2, "decision:" + fakeDecId))
                .provenances(List.of(
                        AgentProvenance.builder().agentName("Claude Code").repositoryName("auth-service").sessionId("s1").build(),
                        AgentProvenance.builder().agentName("Codex").repositoryName("payment-service").sessionId("s2").build()
                ))
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
                .content("Use in-memory store for session tokens")
                .status(MemoryStatus.CONFIRMED)
                .confidence(0.70)
                .build();
        oldMemory.setId(UUID.randomUUID());

        when(memoryRepository.findByMemoryKey("ARCHITECTURAL_STANDARD:" + projId + ":IN_MEMORY"))
                .thenReturn(Optional.of(oldMemory));
        when(memoryRepository.findById(oldMemory.getId()))
                .thenReturn(Optional.of(oldMemory));
        when(semanticSearchService.searchScoped(eq("Redis"), eq("technical_memory"), eq(projId.toString()), any(), eq(10)))
                .thenReturn(List.of(com.secondbrain.common.dto.SearchResult.builder()
                        .payload(Map.of("id", oldMemory.getId().toString()))
                        .build()));

        // Execute synthesis
        Optional<Memory> result = synthesisService.synthesizeAndPromoteArchitecturalKnowledge(
                "Redis", projId.toString(), List.of(d1, d2), project, repo
        );

        assertThat(result).isPresent();
        Memory synthesized = result.get();
        assertThat(synthesized.getMemoryKey()).isEqualTo("ARCHITECTURAL_STANDARD:" + projId + ":REDIS");
        assertThat(synthesized.getConfidence()).isBetween(0.70, 0.95); // Empirically calibrated from 2 observations + diversity bonus

        // Anti-hallucination verified: fake ID excluded, only real decisions retained
        assertThat(synthesized.getEvidenceSources()).containsExactlyInAnyOrder("decision:" + decId1, "decision:" + decId2);
        assertThat(synthesized.getEvidenceSources()).doesNotContain("decision:" + fakeDecId);

        // Superseding verified
        assertThat(oldMemory.getStatus()).isEqualTo(MemoryStatus.SUPERSEDED);
        assertThat(oldMemory.getSupersededBy()).isEqualTo(synthesized.getId());
    }
}
