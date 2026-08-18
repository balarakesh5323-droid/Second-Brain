package com.secondbrain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RelevanceScoringServiceTest {

    private RelevanceScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new RelevanceScoringService();
    }

    @Test
    @DisplayName("Tokenization: Correctly splits camelCase, snake_case, and filters stop words")
    void testExtractTokens() {
        Set<String> tokens = scoringService.extractTokens("Implement JwtAuthenticationFilter and redis_token_service in Auth-Service");
        assertThat(tokens).contains("jwt", "authentication", "filter", "jwtauthenticationfilter", "redis", "token", "service", "auth");
        assertThat(tokens).doesNotContain("and", "in", "the", "a");
    }

    @Test
    @DisplayName("Lexical Matching: Prevents false substring matches (e.g. 'api' does not match 'capital')")
    void testNoFalseSubstringMatches() {
        Set<String> taskTokens = scoringService.extractTokens("Design API endpoint");
        assertThat(taskTokens).contains("api", "endpoint", "design");

        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        // Text with "capital" and "rapid", but NOT the token "api"
        var candidate = scoringService.scoreCandidate(
                "item-1",
                "Rapid capital allocation mechanism",
                repoId, projId, repoId, projId, "repo-1",
                LocalDateTime.now(), taskTokens
        );

        assertThat(candidate.getMatchedKeywords()).isEmpty();
        assertThat(candidate.getRelevance()).isLessThan(0.30);
        assertThat(candidate.getReason()).contains("no direct task keyword match");
    }

    @Test
    @DisplayName("Scope Hierarchy: Exact repository scores higher than project sibling and global")
    void testScopeHierarchy() {
        Set<String> taskTokens = scoringService.extractTokens("Redis Token Revocation");

        UUID targetRepo = UUID.randomUUID();
        UUID targetProj = UUID.randomUUID();
        UUID siblingRepo = UUID.randomUUID();
        UUID otherProj = UUID.randomUUID();

        // 1. Target Repo candidate
        var repoCandidate = scoringService.scoreCandidate(
                "d1", "Redis Token Revocation implementation",
                targetRepo, targetProj, targetRepo, targetProj, "auth-repo",
                LocalDateTime.now(), taskTokens
        );

        // 2. Sibling Repo candidate (same project, different repo)
        var siblingCandidate = scoringService.scoreCandidate(
                "d2", "Redis Token Revocation implementation",
                targetRepo, targetProj, siblingRepo, targetProj, "gateway-repo",
                LocalDateTime.now(), taskTokens
        );

        // 3. Global / Other Project candidate
        var globalCandidate = scoringService.scoreCandidate(
                "d3", "Redis Token Revocation implementation",
                targetRepo, targetProj, UUID.randomUUID(), otherProj, "other-repo",
                LocalDateTime.now(), taskTokens
        );

        assertThat(repoCandidate.getScope()).isEqualTo("REPOSITORY");
        assertThat(siblingCandidate.getScope()).isEqualTo("PROJECT_SIBLING");
        assertThat(globalCandidate.getScope()).isEqualTo("GLOBAL");

        assertThat(repoCandidate.getRelevance()).isGreaterThan(siblingCandidate.getRelevance());
        assertThat(siblingCandidate.getRelevance()).isGreaterThan(globalCandidate.getRelevance());
        assertThat(siblingCandidate.getReason()).contains("gateway-repo");
    }

    @Test
    @DisplayName("Threshold Filtering: Filters out low-relevance noise below MIN_RELEVANCE")
    void testRankAndFilterThreshold() {
        Set<String> taskTokens = scoringService.extractTokens("Redis Token Revocation");
        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        var highMatch = scoringService.scoreCandidate(
                "high", "Redis Token Revocation with Sliding Window",
                repoId, projId, repoId, projId, "repo",
                LocalDateTime.now(), taskTokens
        );

        var noMatch = scoringService.scoreCandidate(
                "low", "Update UI CSS styling for navigation buttons",
                repoId, projId, repoId, projId, "repo",
                LocalDateTime.now(), taskTokens
        );

        List<RelevanceScoringService.ScoredCandidate<String>> filtered = scoringService.rankAndFilter(
                List.of(highMatch, noMatch), RelevanceScoringService.DEFAULT_MIN_RELEVANCE, 5
        );

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getItem()).isEqualTo("high");
    }

    @Test
    @DisplayName("Symbol Scoring: Combines vector similarity with lexical matching and scope normalization")
    void testScoreSymbolCandidate() {
        Set<String> taskTokens = scoringService.extractTokens("Validate Redis Token");
        UUID targetRepo = UUID.randomUUID();
        UUID targetProj = UUID.randomUUID();

        com.secondbrain.common.dto.SearchResult sr = com.secondbrain.common.dto.SearchResult.builder()
                .id("sym-1")
                .score(0.92f)
                .content("public boolean validateToken(String token)")
                .payload(java.util.Map.of(
                        "name", "validateToken",
                        "signature", "public boolean validateToken(String token)",
                        "file", "JwtTokenValidator.java",
                        "repositoryId", targetRepo.toString(),
                        "projectId", targetProj.toString()
                ))
                .build();

        var scored = scoringService.scoreSymbolCandidate(
                sr, targetRepo, targetProj, "auth-service", taskTokens
        );

        assertThat(scored.getScope()).isEqualTo("REPOSITORY");
        assertThat(scored.getRelevance()).isGreaterThan(0.70);
        assertThat(scored.getReason()).contains("token");
    }
}
