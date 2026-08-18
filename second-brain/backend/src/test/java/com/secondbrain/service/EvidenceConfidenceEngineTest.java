package com.secondbrain.service;

import com.secondbrain.common.dto.AgentProvenance;
import com.secondbrain.common.enums.MemoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceConfidenceEngineTest {

    private EvidenceConfidenceEngine confidenceEngine;

    @BeforeEach
    void setUp() {
        confidenceEngine = new EvidenceConfidenceEngine();
    }

    @Test
    @DisplayName("Single observation: Strictly PROPOSED with conservative confidence")
    void testSingleObservationConservativeConfidence() {
        List<AgentProvenance> provenances = List.of(
                AgentProvenance.builder().agentName("Claude Code").repositoryName("auth-service").sessionId("s1").build()
        );
        Set<String> evidence = Set.of("decision:1");

        EvidenceConfidenceEngine.ConfidenceAssessment assessment = confidenceEngine.evaluate(
                1, provenances, evidence, false
        );

        assertThat(assessment.getGatedStatus()).isEqualTo(MemoryStatus.PROPOSED);
        assertThat(assessment.getCalibratedConfidence()).isLessThanOrEqualTo(0.60);
        assertThat(assessment.getProvenanceSource()).isEqualTo("AGENT_EXPERIENCE");
    }

    @Test
    @DisplayName("Multi-agent consensus: 3 decisions from Claude Code and Codex promotes to ESTABLISHED with high confidence")
    void testMultiAgentConsensusConfidence() {
        List<AgentProvenance> provenances = List.of(
                AgentProvenance.builder().agentName("Claude Code").repositoryName("auth-service").sessionId("s1").build(),
                AgentProvenance.builder().agentName("Codex").repositoryName("payment-service").sessionId("s2").build(),
                AgentProvenance.builder().agentName("Claude Code").repositoryName("auth-service").sessionId("s3").build()
        );
        Set<String> evidence = Set.of("decision:1", "decision:2", "decision:3");

        EvidenceConfidenceEngine.ConfidenceAssessment assessment = confidenceEngine.evaluate(
                3, provenances, evidence, false
        );

        assertThat(assessment.getGatedStatus()).isEqualTo(MemoryStatus.ESTABLISHED);
        assertThat(assessment.getCalibratedConfidence()).isGreaterThanOrEqualTo(0.90);
        assertThat(assessment.getProvenanceSource()).isEqualTo("MULTI_AGENT_CONSENSUS");
    }

    @Test
    @DisplayName("Zero evidence: Returns unverified low confidence")
    void testZeroEvidenceAssessment() {
        EvidenceConfidenceEngine.ConfidenceAssessment assessment = confidenceEngine.evaluate(
                0, List.of(), Set.of(), false
        );

        assertThat(assessment.getGatedStatus()).isEqualTo(MemoryStatus.PROPOSED);
        assertThat(assessment.getCalibratedConfidence()).isEqualTo(0.10);
    }
}
