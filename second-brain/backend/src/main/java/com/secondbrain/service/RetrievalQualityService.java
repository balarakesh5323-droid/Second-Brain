package com.secondbrain.service;

import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalQualityService {

    private final MemoryRepository memoryRepository;
    private final SemanticSearchService semanticSearchService;

    public QualityReport evaluate(String projectId) {
        log.info("Evaluating retrieval quality...");

        List<EvaluationCase> testCases = getTestDataset();
        List<EvaluationResult> results = new ArrayList<>();

        for (EvaluationCase testCase : testCases) {
            results.add(evaluateCase(testCase));
        }

        double avgPrecision = results.stream()
            .mapToDouble(r -> r.precision)
            .average().orElse(0.0);

        double avgRecall = results.stream()
            .mapToDouble(r -> r.recall)
            .average().orElse(0.0);

        double f1 = (avgPrecision + avgRecall) > 0
            ? 2 * (avgPrecision * avgRecall) / (avgPrecision + avgRecall)
            : 0.0;

        return QualityReport.builder()
            .averagePrecision(Math.round(avgPrecision * 1000.0) / 1000.0)
            .averageRecall(Math.round(avgRecall * 1000.0) / 1000.0)
            .f1Score(Math.round(f1 * 1000.0) / 1000.0)
            .totalCases(testCases.size())
            .results(results)
            .build();
    }

    private EvaluationResult evaluateCase(EvaluationCase testCase) {
        List<Memory> retrievedMemories = searchMemories(testCase.query);
        Set<String> retrievedContents = retrievedMemories.stream()
            .map(m -> m.getContent() != null ? m.getContent().toLowerCase() : "")
            .collect(Collectors.toSet());

        // Precision: how many retrieved items are relevant
        long relevantRetrieved = retrievedContents.stream()
            .filter(content -> testCase.expectedKeywords.stream()
                .anyMatch(kw -> content.contains(kw.toLowerCase())))
            .count();

        double precision = retrievedContents.isEmpty() ? 0.0
            : (double) relevantRetrieved / retrievedContents.size();

        // Recall: how many expected keywords were found in retrieved items
        long keywordsFound = testCase.expectedKeywords.stream()
            .filter(kw -> retrievedContents.stream()
                .anyMatch(content -> content.contains(kw.toLowerCase())))
            .count();

        double recall = testCase.expectedKeywords.isEmpty() ? 0.0
            : (double) keywordsFound / testCase.expectedKeywords.size();

        return EvaluationResult.builder()
            .query(testCase.query)
            .expectedKeywords(testCase.expectedKeywords)
            .retrievedCount(retrievedMemories.size())
            .relevantRetrieved((int) relevantRetrieved)
            .keywordsFound((int) keywordsFound)
            .precision(Math.round(precision * 1000.0) / 1000.0)
            .recall(Math.round(recall * 1000.0) / 1000.0)
            .passed(precision >= 0.3 && recall >= 0.5)
            .build();
    }

    private List<Memory> searchMemories(String query) {
        try {
            return memoryRepository.findByContentContainingIgnoreCase(query).stream()
                .filter(m -> m.getStatus() != MemoryStatus.ARCHIVED)
                .filter(m -> m.getStatus() != MemoryStatus.SUPERSEDED)
                .limit(10)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Search failed for query '{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<EvaluationCase> getTestDataset() {
        return List.of(
            new EvaluationCase(
                "What database does auth-service use?",
                List.of("postgresql", "database", "auth", "postgres"),
                "DECLARATIVE", "Expected: PostgreSQL for authentication"),
            new EvaluationCase(
                "Why was Redis introduced?",
                List.of("redis", "cache", "caching", "session"),
                "DECLARATIVE", "Expected: Redis for caching/sessions"),
            new EvaluationCase(
                "What is the current OAuth implementation?",
                List.of("oauth", "token", "authentication", "jwt"),
                "DECLARATIVE", "Expected: OAuth/JWT implementation details"),
            new EvaluationCase(
                "What did the previous agent leave unfinished?",
                List.of("handoff", "task", "in-progress", "blocked"),
                "EPISODIC", "Expected: Handoff/in-progress items"),
            new EvaluationCase(
                "How is Docker configured for this project?",
                List.of("docker", "compose", "container", "service"),
                "SEMANTIC", "Expected: Docker configuration"),
            new EvaluationCase(
                "What testing framework is used?",
                List.of("junit", "test", "mockito", "spring"),
                "SEMANTIC", "Expected: Testing framework details"),
            new EvaluationCase(
                "What are the recent deployment changes?",
                List.of("deploy", "ci", "cd", "pipeline", "release"),
                "EPISODIC", "Expected: Deployment/CI-CD info"),
            new EvaluationCase(
                "What is the API authentication mechanism?",
                List.of("api", "auth", "token", "security", "oauth"),
                "DECLARATIVE", "Expected: API authentication details")
        );
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EvaluationCase {
        private String query;
        private List<String> expectedKeywords;
        private String memoryType;
        private String description;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EvaluationResult {
        private String query;
        private List<String> expectedKeywords;
        private int retrievedCount;
        private int relevantRetrieved;
        private int keywordsFound;
        private double precision;
        private double recall;
        private boolean passed;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QualityReport {
        private double averagePrecision;
        private double averageRecall;
        private double f1Score;
        private int totalCases;
        private List<EvaluationResult> results;
    }
}
