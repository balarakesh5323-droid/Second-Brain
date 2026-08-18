package com.secondbrain.service;

import com.secondbrain.common.dto.AgentProvenance;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Algorithmic Confidence & Status Gating Engine.
 *
 * Implements an Evidence Independence Model:
 * Repeated events from the same session/agent receive diminishing weights.
 * True high confidence requires independent cross-agent, cross-session, or cross-repo validation.
 */
@Service
@Slf4j
public class EvidenceConfidenceEngine {

    @Data
    @Builder
    public static class ConfidenceAssessment {
        private double calibratedConfidence;
        private double effectiveIndependentEvidence;
        private MemoryStatus gatedStatus;
        private String provenanceSource;
        private String explanation;
    }

    public ConfidenceAssessment evaluate(
            int rawEvidenceCount,
            List<AgentProvenance> provenances,
            Set<String> evidenceSources,
            boolean isDeveloperExplicit) {
        return evaluate(rawEvidenceCount, provenances, evidenceSources, isDeveloperExplicit, MemoryType.ARCHITECTURAL);
    }

    /**
     * Calibrates empirical confidence based on evidence independence and memory type policies.
     */
    public ConfidenceAssessment evaluate(
            int rawEvidenceCount,
            List<AgentProvenance> provenances,
            Set<String> evidenceSources,
            boolean isDeveloperExplicit,
            MemoryType memoryType) {

        if (rawEvidenceCount <= 0 || (evidenceSources != null && evidenceSources.isEmpty())) {
            return ConfidenceAssessment.builder()
                    .calibratedConfidence(0.10)
                    .effectiveIndependentEvidence(0.0)
                    .gatedStatus(MemoryStatus.PROPOSED)
                    .provenanceSource("UNVERIFIED")
                    .explanation("No verifiable evidence linked.")
                    .build();
        }

        // 1. Group provenances by Session and Agent to detect clustering
        Map<String, Integer> eventsPerSession = new HashMap<>();
        Set<String> distinctAgents = new HashSet<>();
        Set<String> distinctRepos = new HashSet<>();

        if (provenances != null) {
            for (AgentProvenance p : provenances) {
                if (p.getAgentName() != null && !p.getAgentName().isBlank() && !p.getAgentName().equalsIgnoreCase("UNKNOWN")) {
                    distinctAgents.add(p.getAgentName());
                }
                if (p.getRepositoryName() != null && !p.getRepositoryName().isBlank()) {
                    distinctRepos.add(p.getRepositoryName());
                }
                // Robust session key: missing session ID uses distinct provenance identity rather than single default bucket
                String sessionKey = (p.getSessionId() != null && !p.getSessionId().isBlank())
                        ? p.getSessionId()
                        : "agent:" + (p.getAgentName() != null ? p.getAgentName() : "UNKNOWN")
                        + ":repo:" + (p.getRepositoryName() != null ? p.getRepositoryName() : "UNKNOWN")
                        + ":time:" + (p.getTimestamp() != null ? p.getTimestamp().toString() : "gen-" + UUID.randomUUID().toString());

                eventsPerSession.put(sessionKey, eventsPerSession.getOrDefault(sessionKey, 0) + 1);
            }
        }

        // 2. Calculate Effective Independent Evidence with Diminishing Returns for same-session bursts
        double effectiveEvidence = 0.0;
        if (eventsPerSession.isEmpty()) {
            effectiveEvidence = Math.min(rawEvidenceCount, 1.0 + Math.log1p(rawEvidenceCount) * 0.3);
        } else {
            for (Map.Entry<String, Integer> entry : eventsPerSession.entrySet()) {
                int sessionEvents = entry.getValue();
                // 1st event in session gives 1.0, subsequent events in same session give diminishing log weight
                double sessionWeight = 1.0 + Math.log1p(sessionEvents - 1) * 0.25;
                effectiveEvidence += sessionWeight;
            }
        }

        // Add Multi-Agent and Multi-Repo Independent Boosts
        if (distinctAgents.size() >= 2) {
            effectiveEvidence += (distinctAgents.size() - 1) * 0.80; // Significant boost for multi-agent agreement
        }
        if (distinctRepos.size() >= 2) {
            effectiveEvidence += (distinctRepos.size() - 1) * 0.50; // Boost for cross-repo generality
        }
        if (isDeveloperExplicit) {
            // User explicit input carries high weight for developer preferences, but tempered for architectural claims
            if (memoryType == MemoryType.PREFERENCE || memoryType == MemoryType.DECLARATIVE) {
                effectiveEvidence += 2.00;
            } else {
                effectiveEvidence += 0.80;
            }
        }

        // 3. Calibrate Empirical Confidence & Status Gating
        double calibratedConfidence;
        MemoryStatus status;

        if (effectiveEvidence < 1.5) {
            // Single observation or single-session burst: strictly PROPOSED
            calibratedConfidence = Math.min(0.60, 0.40 + (effectiveEvidence * 0.12));
            status = MemoryStatus.PROPOSED;
        } else if (effectiveEvidence < 3.0) {
            // 2 independent sessions or multi-event with validation: CONFIRMED
            calibratedConfidence = Math.min(0.80, 0.62 + ((effectiveEvidence - 1.5) * 0.10));
            status = MemoryStatus.CONFIRMED;
        } else {
            // 3+ independent evidence units (multi-agent or cross-repo): ESTABLISHED
            calibratedConfidence = Math.min(0.96, 0.82 + ((effectiveEvidence - 3.0) * 0.04));
            if (memoryType == MemoryType.PREFERENCE && isDeveloperExplicit) {
                status = MemoryStatus.ESTABLISHED;
            } else {
                status = (distinctAgents.size() >= 2 || distinctRepos.size() >= 2)
                        ? MemoryStatus.ESTABLISHED : MemoryStatus.CONFIRMED;
            }
        }

        String provenanceSource = isDeveloperExplicit ? "DEVELOPER_EXPLICIT"
                : (distinctAgents.size() >= 2 ? "MULTI_AGENT_CONSENSUS" : "AGENT_EXPERIENCE");

        String explanation = String.format("Raw count: %d, Effective independent evidence: %.2f, Distinct agents: %d, Distinct repos: %d, Type: %s, Gated status: %s",
                rawEvidenceCount, effectiveEvidence, distinctAgents.size(), distinctRepos.size(), memoryType, status);

        return ConfidenceAssessment.builder()
                .calibratedConfidence(calibratedConfidence)
                .effectiveIndependentEvidence(effectiveEvidence)
                .gatedStatus(status)
                .provenanceSource(provenanceSource)
                .explanation(explanation)
                .build();
    }
}
