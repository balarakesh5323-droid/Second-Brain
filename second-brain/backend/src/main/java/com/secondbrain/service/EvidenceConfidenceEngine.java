package com.secondbrain.service;

import com.secondbrain.common.dto.AgentProvenance;
import com.secondbrain.common.enums.MemoryStatus;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Algorithmic Confidence & Status Gating Engine.
 *
 * Invariant: LLM-generated self-reported confidence is NOT trusted.
 * Confidence and lifecycle status are strictly derived from empirical evidence
 * count, multi-agent diversity, session diversity, and cross-repo validation.
 */
@Service
@Slf4j
public class EvidenceConfidenceEngine {

    @Data
    @Builder
    public static class ConfidenceAssessment {
        private double calibratedConfidence;
        private MemoryStatus gatedStatus;
        private String provenanceSource;
        private String explanation;
    }

    /**
     * Calibrates true empirical confidence and enforces lifecycle status gates.
     */
    public ConfidenceAssessment evaluate(
            int evidenceCount,
            List<AgentProvenance> provenances,
            Set<String> evidenceSources,
            boolean isDeveloperExplicit) {

        if (evidenceCount <= 0 || (evidenceSources != null && evidenceSources.isEmpty())) {
            return ConfidenceAssessment.builder()
                    .calibratedConfidence(0.10)
                    .gatedStatus(MemoryStatus.PROPOSED)
                    .provenanceSource("UNVERIFIED")
                    .explanation("No verifiable evidence linked.")
                    .build();
        }

        // Distinct agent and repository diversity
        Set<String> distinctAgents = (provenances != null) ? provenances.stream()
                .filter(p -> p.getAgentName() != null && !p.getAgentName().isBlank())
                .map(AgentProvenance::getAgentName)
                .collect(Collectors.toSet()) : Set.of();

        Set<String> distinctRepos = (provenances != null) ? provenances.stream()
                .filter(p -> p.getRepositoryName() != null && !p.getRepositoryName().isBlank())
                .map(AgentProvenance::getRepositoryName)
                .collect(Collectors.toSet()) : Set.of();

        Set<String> distinctSessions = (provenances != null) ? provenances.stream()
                .filter(p -> p.getSessionId() != null && !p.getSessionId().isBlank())
                .map(AgentProvenance::getSessionId)
                .collect(Collectors.toSet()) : Set.of();

        double baseConfidence;
        MemoryStatus status;

        if (evidenceCount == 1) {
            baseConfidence = 0.50;
            status = MemoryStatus.PROPOSED;
        } else if (evidenceCount == 2) {
            baseConfidence = 0.70;
            status = MemoryStatus.CONFIRMED;
        } else {
            baseConfidence = 0.82;
            status = (distinctAgents.size() >= 2 || distinctRepos.size() >= 2) ? MemoryStatus.ESTABLISHED : MemoryStatus.CONFIRMED;
        }

        // Apply diversity multipliers
        double agentBonus = Math.max(0, (distinctAgents.size() - 1) * 0.08);
        double repoBonus = Math.max(0, (distinctRepos.size() - 1) * 0.05);
        double sessionBonus = Math.max(0, (distinctSessions.size() - 1) * 0.03);
        double developerBonus = isDeveloperExplicit ? 0.10 : 0.0;

        double finalConfidence = Math.min(0.98, baseConfidence + agentBonus + repoBonus + sessionBonus + developerBonus);

        // Strict Status Gating Guardrails:
        // 1. Single observation can NEVER be ESTABLISHED
        if (evidenceCount < 3 && status == MemoryStatus.ESTABLISHED) {
            status = MemoryStatus.CONFIRMED;
        }
        if (evidenceCount == 1) {
            status = MemoryStatus.PROPOSED;
            finalConfidence = Math.min(0.60, finalConfidence);
        }

        String provenanceSource = isDeveloperExplicit ? "DEVELOPER_EXPLICIT"
                : (distinctAgents.size() >= 2 ? "MULTI_AGENT_CONSENSUS" : "AGENT_EXPERIENCE");

        String explanation = String.format("Evidence count: %d, Distinct agents: %d (%s), Distinct repos: %d, Gated status: %s",
                evidenceCount, distinctAgents.size(), String.join(", ", distinctAgents), distinctRepos.size(), status);

        return ConfidenceAssessment.builder()
                .calibratedConfidence(finalConfidence)
                .gatedStatus(status)
                .provenanceSource(provenanceSource)
                .explanation(explanation)
                .build();
    }
}
