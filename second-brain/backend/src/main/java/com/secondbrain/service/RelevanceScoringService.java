package com.secondbrain.service;

import com.secondbrain.common.dto.SearchResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RelevanceScoringService {

    public static final double DEFAULT_MIN_RELEVANCE = 0.35;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't",
            "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't", "have",
            "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
            "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into",
            "is", "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my",
            "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our",
            "ours", "ourselves", "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's",
            "should", "shouldn't", "so", "some", "such", "than", "that", "that's", "the", "their", "theirs",
            "them", "themselves", "then", "there", "there's", "these", "they", "they'd", "they'll", "they're",
            "they've", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasn't",
            "we", "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's", "when", "when's",
            "where", "where's", "which", "while", "who", "who's", "whom", "why", "why's", "with", "won't",
            "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself",
            "yourselves", "null", "true", "false", "void", "return", "class", "public", "private", "protected"
    );

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
    private static final Pattern DELIMITER_PATTERN = Pattern.compile("[\\s_\\-\\./\\\\#@:\\[\\](){}<>,;\"'!?=+|*&^%$~`]+");

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoredCandidate<T> {
        private T item;
        private double relevance;
        private String scope; // "REPOSITORY", "PROJECT_SIBLING", "GLOBAL"
        private String repositoryName;
        private String reason;
        private List<String> matchedKeywords;
        private LocalDateTime createdAt;
    }

    /**
     * Extracts tokenized keywords supporting camelCase, snake_case, kebab-case, and dot notation.
     */
    public Set<String> extractTokens(String text) {
        if (text == null || text.isBlank()) return Set.of();

        Set<String> tokens = new LinkedHashSet<>();
        String[] rawWords = DELIMITER_PATTERN.split(text.trim());

        for (String raw : rawWords) {
            if (raw.isBlank()) continue;

            // Also split camelCase tokens (e.g., JwtAuthenticationFilter -> Jwt, Authentication, Filter)
            String[] camelParts = CAMEL_CASE_PATTERN.split(raw);
            for (String part : camelParts) {
                String clean = part.toLowerCase(Locale.ROOT).trim();
                if (clean.length() >= 2 && !STOP_WORDS.contains(clean)) {
                    tokens.add(clean);
                }
            }

            String wholeClean = raw.toLowerCase(Locale.ROOT).trim();
            if (wholeClean.length() >= 2 && !STOP_WORDS.contains(wholeClean)) {
                tokens.add(wholeClean);
            }
        }
        return tokens;
    }

    /**
     * Evaluates multi-signal relevance scoring across lexical overlap, scope hierarchy, and recency decay.
     */
    public <T> ScoredCandidate<T> scoreCandidate(
            T item,
            String candidateText,
            UUID targetRepoId,
            UUID targetProjectId,
            UUID candidateRepoId,
            UUID candidateProjectId,
            String candidateRepoName,
            LocalDateTime createdAt,
            Set<String> taskTokens) {

        // 1. Determine Scope & Scope Factor
        String scope;
        double scopeFactor;
        if (targetRepoId != null && targetRepoId.equals(candidateRepoId)) {
            scope = "REPOSITORY";
            scopeFactor = 1.0;
        } else if (targetProjectId != null && targetProjectId.equals(candidateProjectId)) {
            scope = "PROJECT_SIBLING";
            scopeFactor = 0.82;
        } else {
            scope = "GLOBAL";
            scopeFactor = 0.60;
        }

        // 2. Determine Recency Factor
        double recencyFactor = 1.0;
        if (createdAt != null) {
            long daysOld = Math.max(0, ChronoUnit.DAYS.between(createdAt, LocalDateTime.now()));
            recencyFactor = Math.max(0.65, Math.exp(-0.006 * daysOld));
        }

        // 3. Exact Token Word-Boundary Matching (avoiding substring false positives like "api" in "capital")
        Set<String> candidateTokens = extractTokens(candidateText);
        List<String> matchedKeywords = new ArrayList<>();
        for (String taskTok : taskTokens) {
            if (candidateTokens.contains(taskTok)) {
                matchedKeywords.add(taskTok);
            }
        }

        // 4. Compute Normalized Composite Relevance Score
        double relevance;
        String reason;

        if (!matchedKeywords.isEmpty()) {
            double overlapRatio = (double) matchedKeywords.size() / Math.max(1, taskTokens.size());
            double termBonus = Math.min(0.12, matchedKeywords.size() * 0.04);
            double rawScore = 0.38 + (0.50 * overlapRatio) + termBonus;
            relevance = Math.min(0.98, rawScore * scopeFactor * recencyFactor);
            relevance = Math.round(relevance * 100.0) / 100.0;

            if ("REPOSITORY".equals(scope)) {
                reason = "Matches task keywords [" + String.join(", ", matchedKeywords) + "] in target repository";
            } else if ("PROJECT_SIBLING".equals(scope)) {
                String repoLabel = candidateRepoName != null ? candidateRepoName : "sibling repo";
                reason = "Cross-repo match on [" + String.join(", ", matchedKeywords) + "] from '" + repoLabel + "' in project";
            } else {
                reason = "Global match on [" + String.join(", ", matchedKeywords) + "]";
            }
        } else {
            // No lexical match: background baseline capped low (0.10 - 0.25)
            double rawScore = 0.20 * scopeFactor * recencyFactor;
            relevance = Math.round(rawScore * 100.0) / 100.0;
            reason = "Recent record with no direct task keyword match";
        }

        return ScoredCandidate.<T>builder()
                .item(item)
                .relevance(relevance)
                .scope(scope)
                .repositoryName(candidateRepoName)
                .reason(reason)
                .matchedKeywords(matchedKeywords)
                .createdAt(createdAt)
                .build();
    }

    /**
     * Scores code symbols by combining semantic vector similarity with lexical matching and scope.
     */
    public ScoredCandidate<SearchResult> scoreSymbolCandidate(
            SearchResult result,
            UUID targetRepoId,
            UUID targetProjectId,
            String candidateRepoName,
            Set<String> taskTokens) {

        String symbolText = (result.getContent() != null ? result.getContent() : "");
        if (result.getPayload() != null) {
            symbolText += " " + result.getPayload().getOrDefault("name", "") + " "
                    + result.getPayload().getOrDefault("signature", "") + " "
                    + result.getPayload().getOrDefault("file", "");
        }

        String repoIdStr = result.getPayload() != null ? (String) result.getPayload().get("repositoryId") : null;
        String projIdStr = result.getPayload() != null ? (String) result.getPayload().get("projectId") : null;
        UUID candidateRepoId = repoIdStr != null ? parseUUID(repoIdStr) : null;
        UUID candidateProjectId = projIdStr != null ? parseUUID(projIdStr) : null;

        String scope;
        double scopeFactor;
        if (targetRepoId != null && targetRepoId.equals(candidateRepoId)) {
            scope = "REPOSITORY";
            scopeFactor = 1.0;
        } else if (targetProjectId != null && targetProjectId.equals(candidateProjectId)) {
            scope = "PROJECT_SIBLING";
            scopeFactor = 0.82;
        } else {
            scope = "GLOBAL";
            scopeFactor = 0.60;
        }

        Set<String> candidateTokens = extractTokens(symbolText);
        List<String> matchedKeywords = new ArrayList<>();
        for (String tok : taskTokens) {
            if (candidateTokens.contains(tok)) {
                matchedKeywords.add(tok);
            }
        }

        double vectorScore = Math.max(0.0, Math.min(1.0, result.getScore()));
        double lexicalOverlap = (double) matchedKeywords.size() / Math.max(1, taskTokens.size());

        double rawScore = (0.55 * vectorScore) + (0.35 * lexicalOverlap) + Math.min(0.10, matchedKeywords.size() * 0.03);
        double relevance = Math.min(0.99, rawScore * scopeFactor);
        relevance = Math.round(relevance * 100.0) / 100.0;

        String reason;
        if (!matchedKeywords.isEmpty()) {
            reason = "Semantic vector match + keyword match on [" + String.join(", ", matchedKeywords) + "] in " + scope.toLowerCase();
        } else {
            reason = "Semantic vector match for task query in repository symbols";
        }

        return ScoredCandidate.<SearchResult>builder()
                .item(result)
                .relevance(relevance)
                .scope(scope)
                .repositoryName(candidateRepoName)
                .reason(reason)
                .matchedKeywords(matchedKeywords)
                .createdAt(null)
                .build();
    }

    private UUID parseUUID(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ranks candidates by relevance descending, applies threshold filtering, and returns top-K.
     */
    public <T> List<ScoredCandidate<T>> rankAndFilter(
            List<ScoredCandidate<T>> candidates,
            double minThreshold,
            int maxLimit) {

        if (candidates == null || candidates.isEmpty()) return List.of();

        return candidates.stream()
                .filter(c -> c.getRelevance() >= minThreshold)
                .sorted((a, b) -> {
                    int cmp = Double.compare(b.getRelevance(), a.getRelevance());
                    if (cmp != 0) return cmp;
                    if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    }
                    return 0;
                })
                .limit(maxLimit)
                .collect(Collectors.toList());
    }
}
