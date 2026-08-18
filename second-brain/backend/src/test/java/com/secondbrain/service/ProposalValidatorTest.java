package com.secondbrain.service;

import com.secondbrain.common.dto.AgentProvenance;
import com.secondbrain.common.dto.KnowledgeProposal;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.AgentAttemptRepository;
import com.secondbrain.common.repository.AgentSessionRepository;
import com.secondbrain.common.repository.DecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposalValidatorTest {

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private AgentAttemptRepository attemptRepository;

    @Mock
    private AgentSessionRepository sessionRepository;

    private EvidenceConfidenceEngine confidenceEngine;
    private ContradictionClassifier contradictionClassifier;
    private ProposalValidator proposalValidator;

    @BeforeEach
    void setUp() {
        confidenceEngine = new EvidenceConfidenceEngine();
        contradictionClassifier = new ContradictionClassifier(new com.fasterxml.jackson.databind.ObjectMapper(), new org.springframework.boot.web.client.RestTemplateBuilder());
        proposalValidator = new ProposalValidator(
                decisionRepository,
                attemptRepository,
                sessionRepository,
                confidenceEngine,
                contradictionClassifier
        );
    }

    @Test
    @DisplayName("Validator: Strips fake evidence IDs and replaces LLM-reported confidence with empirical confidence")
    void testValidatorRejectsHallucinatedEvidenceAndCalculatesConfidence() {
        UUID validId1 = UUID.randomUUID();
        UUID validId2 = UUID.randomUUID();
        UUID hallucinatedId = UUID.randomUUID();

        when(decisionRepository.existsById(validId1)).thenReturn(true);
        when(decisionRepository.existsById(validId2)).thenReturn(true);
        when(decisionRepository.existsById(hallucinatedId)).thenReturn(false);

        KnowledgeProposal rawProposal = KnowledgeProposal.builder()
                .memoryKey("ARCHITECTURAL_STANDARD:PROJECT_1:REDIS")
                .knowledge("Standardized on Redis Streams for message queues.")
                .type(MemoryType.ARCHITECTURAL)
                .status(MemoryStatus.ESTABLISHED) // LLM claimed ESTABLISHED
                .confidence(0.99) // LLM claimed 0.99 confidence
                .evidenceSources(Set.of("decision:" + validId1, "decision:" + validId2, "decision:" + hallucinatedId))
                .provenances(List.of(
                        AgentProvenance.builder().agentName("Claude Code").repositoryName("auth-service").sessionId("s1").build(),
                        AgentProvenance.builder().agentName("Codex").repositoryName("event-service").sessionId("s2").build()
                ))
                .build();

        ProposalValidator.ValidationResult result = proposalValidator.validate(rawProposal, List.of());

        assertThat(result.isValid()).isTrue();
        KnowledgeProposal sanitized = result.getSanitizedProposal();

        // 1. Hallucinated ID stripped
        assertThat(sanitized.getEvidenceSources()).containsExactlyInAnyOrder("decision:" + validId1, "decision:" + validId2);
        assertThat(sanitized.getEvidenceSources()).doesNotContain("decision:" + hallucinatedId);

        // 2. LLM-claimed 0.99 confidence was replaced by calibrated empirical confidence
        assertThat(sanitized.getConfidence()).isLessThan(0.90);
        assertThat(sanitized.getConfidence()).isGreaterThanOrEqualTo(0.70);

        // 3. Status gated to CONFIRMED / ESTABLISHED based on multi-agent diversity
        assertThat(sanitized.getStatus()).isIn(MemoryStatus.CONFIRMED, MemoryStatus.ESTABLISHED);
    }

    @Test
    @DisplayName("Validator: Rejects proposals with zero valid evidence in database")
    void testValidatorRejectsUnsubstantiatedProposal() {
        UUID hallucinatedId = UUID.randomUUID();
        when(decisionRepository.existsById(hallucinatedId)).thenReturn(false);

        KnowledgeProposal hallucinatedProposal = KnowledgeProposal.builder()
                .memoryKey("ARCHITECTURAL_STANDARD:PROJECT_1:KAFKA")
                .knowledge("Standardized on Kafka.")
                .type(MemoryType.ARCHITECTURAL)
                .confidence(0.95)
                .evidenceSources(Set.of("decision:" + hallucinatedId))
                .build();

        ProposalValidator.ValidationResult result = proposalValidator.validate(hallucinatedProposal, List.of());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getRejectionReason()).contains("anti-hallucination guard");
    }
}
