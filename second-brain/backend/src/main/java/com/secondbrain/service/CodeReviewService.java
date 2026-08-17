package com.secondbrain.service;

import com.secondbrain.common.entity.AgentAttempt;
import com.secondbrain.common.repository.AgentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeReviewService {

    private final AgentAttemptRepository attemptRepository;
    private final ImpactAnalysisService impactAnalysisService;

    public Map<String, Object> reviewChanges(String diff, UUID projectId, UUID repositoryId) {
        Map<String, Object> result = new HashMap<>();

        if (diff == null || diff.isBlank()) {
            result.put("summary", "No diff content provided for code review.");
            result.put("verdict", "PASS");
            return result;
        }

        // 1. Check for past failure regressions
        List<AgentAttempt> pastFailures = (repositoryId != null)
                ? attemptRepository.findByStatusAndRepositoryId("FAILURE", repositoryId)
                : attemptRepository.findByStatus("FAILURE");

        List<Map<String, Object>> potentialRegressions = new ArrayList<>();
        String lowerDiff = diff.toLowerCase();

        for (AgentAttempt failure : pastFailures) {
            if (failure.getApproach() != null) {
                String[] words = failure.getApproach().toLowerCase().split("\\s+");
                int matchCount = 0;
                for (String w : words) {
                    if (w.length() > 3 && lowerDiff.contains(w)) {
                        matchCount++;
                    }
                }
                if (words.length > 0 && ((double) matchCount / words.length) > 0.4) {
                    potentialRegressions.add(Map.of(
                            "attemptId", failure.getId().toString(),
                            "failedApproach", failure.getApproach(),
                            "errorMessage", failure.getErrorMessage() != null ? failure.getErrorMessage() : "N/A",
                            "lessonLearned", failure.getLessonLearned() != null ? failure.getLessonLearned() : "N/A"
                    ));
                }
            }
        }

        // 2. Run Impact Analysis
        Map<String, Object> impact = impactAnalysisService.analyzeImpact("working-tree.diff", diff, projectId);

        // 3. Test Coverage & Documentation Check
        boolean hasTests = lowerDiff.contains("test") || lowerDiff.contains("assert") || lowerDiff.contains("expect(");
        boolean hasLargeChanges = diff.lines().count() > 30;

        List<String> suggestions = new ArrayList<>();
        if (hasLargeChanges && !hasTests) {
            suggestions.add("No unit or integration tests detected for this change set. Consider adding test coverage.");
        }
        if (!potentialRegressions.isEmpty()) {
            suggestions.add("Potential regression detected against past failed engineering trials! Check previous lessons learned.");
        }

        String verdict = "APPROVE";
        if (!potentialRegressions.isEmpty() || "HIGH".equals(impact.get("risk"))) {
            verdict = "REQUEST_CHANGES";
        } else if ("MEDIUM".equals(impact.get("risk")) || (!hasTests && hasLargeChanges)) {
            verdict = "COMMENT";
        }

        // 4. Generate structured Markdown summary
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 🤖 Second Brain AI Code Review (Verdict: %s)\n\n", verdict));
        sb.append(String.format("- **Risk Assessment:** `%s`\n", impact.get("risk")));
        sb.append(String.format("- **Downstream Call Sites Affected:** %s\n", impact.get("affectedCallSiteCount")));
        sb.append(String.format("- **Past Failure Regressions Flagged:** %d\n\n", potentialRegressions.size()));

        if (!potentialRegressions.isEmpty()) {
            sb.append("#### ⚠️ Past Trial Regressions\n");
            for (Map<String, Object> reg : potentialRegressions) {
                sb.append(String.format("- **Failed Approach:** %s\n  - **Past Error:** `%s`\n  - **Lesson:** _%s_\n",
                        reg.get("failedApproach"), reg.get("errorMessage"), reg.get("lessonLearned")));
            }
            sb.append("\n");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) impact.get("decisionConflicts");
        if (conflicts != null && !conflicts.isEmpty()) {
            sb.append("#### 🚨 Architectural Decision Conflicts\n");
            for (Map<String, Object> c : conflicts) {
                sb.append(String.format("- **Decision:** %s\n  - **Conflict:** %s\n", c.get("decisionTitle"), c.get("conflictReason")));
            }
            sb.append("\n");
        }

        if (!suggestions.isEmpty()) {
            sb.append("#### 💡 Recommendations\n");
            for (String s : suggestions) {
                sb.append(String.format("- %s\n", s));
            }
        }

        result.put("verdict", verdict);
        result.put("impact", impact);
        result.put("potentialRegressions", potentialRegressions);
        result.put("suggestions", suggestions);
        result.put("markdownReport", sb.toString());

        return result;
    }
}
