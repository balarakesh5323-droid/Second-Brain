package com.secondbrain.service;

import com.secondbrain.common.dto.KnowledgeProposal;
import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.repository.AgentAttemptRepository;
import com.secondbrain.common.repository.AgentSessionRepository;
import com.secondbrain.common.repository.DecisionRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProposalValidator {

    private final DecisionRepository decisionRepository;
    private final AgentAttemptRepository attemptRepository;
    private final AgentSessionRepository sessionRepository;
    private final EvidenceConfidenceEngine confidenceEngine;
    private final ContradictionClassifier contradictionClassifier;

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private KnowledgeProposal sanitizedProposal;
        private EvidenceConfidenceEngine.ConfidenceAssessment assessment;
        private String rejectionReason;
    }

    /**
     * Validates an LLM or semantic knowledge proposal against hard database evidence,
     * strips hallucinated IDs, calculates calibrated empirical confidence, and gates status.
     */
    public ValidationResult validate(KnowledgeProposal proposal, List<Memory> existingMemories) {
        if (proposal == null || proposal.getKnowledge() == null || proposal.getKnowledge().isBlank()) {
            return ValidationResult.builder().valid(false).rejectionReason("Empty knowledge payload").build();
        }

        // 1. Hard Evidence Grounding & Anti-Hallucination
        Set<String> verifiedEvidence = new HashSet<>();
        if (proposal.getEvidenceSources() != null) {
            for (String ev : proposal.getEvidenceSources()) {
                if (ev == null) continue;
                try {
                    if (ev.startsWith("decision:")) {
                        UUID id = UUID.fromString(ev.substring("decision:".length()));
                        if (decisionRepository.existsById(id)) verifiedEvidence.add(ev);
                    } else if (ev.startsWith("attempt:")) {
                        UUID id = UUID.fromString(ev.substring("attempt:".length()));
                        if (attemptRepository.existsById(id)) verifiedEvidence.add(ev);
                    } else if (ev.startsWith("session:")) {
                        UUID id = UUID.fromString(ev.substring("session:".length()));
                        if (sessionRepository.existsById(id)) verifiedEvidence.add(ev);
                    }
                } catch (Exception ignored) {}
            }
        }

        if (verifiedEvidence.isEmpty()) {
            log.warn("⚠️ Rejecting proposal [{}]: zero verifiable evidence IDs found in database.", proposal.getMemoryKey());
            return ValidationResult.builder()
                    .valid(false)
                    .rejectionReason("No verifiable evidence IDs in database (anti-hallucination guard)")
                    .build();
        }

        // 2. Empirical Confidence Assessment & Status Gating (Do NOT trust LLM confidence)
        EvidenceConfidenceEngine.ConfidenceAssessment assessment = confidenceEngine.evaluate(
                verifiedEvidence.size(),
                proposal.getProvenances(),
                verifiedEvidence,
                false
        );

        // 3. Contradiction Validation: Verify superseding claims
        Set<String> validContradictions = contradictionClassifier.findContradictoryMemoryKeys(
                proposal.getKnowledge(), existingMemories
        );

        // Create sanitized proposal
        KnowledgeProposal sanitized = KnowledgeProposal.builder()
                .memoryKey(proposal.getMemoryKey())
                .knowledge(proposal.getKnowledge())
                .type(proposal.getType())
                .status(assessment.getGatedStatus())
                .confidence(assessment.getCalibratedConfidence())
                .reasoning(proposal.getReasoning())
                .projectKey(proposal.getProjectKey())
                .evidenceSources(verifiedEvidence)
                .supersedesMemoryKeys(validContradictions)
                .suggestedTags(proposal.getSuggestedTags())
                .provenances(proposal.getProvenances())
                .build();

        return ValidationResult.builder()
                .valid(true)
                .sanitizedProposal(sanitized)
                .assessment(assessment)
                .build();
    }
}
