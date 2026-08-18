package com.secondbrain.service;

import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.AgentAttempt;
import com.secondbrain.common.entity.Decision;
import com.secondbrain.common.repository.AgentAttemptRepository;
import com.secondbrain.common.repository.DecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateRetrievalServiceTest {

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private AgentAttemptRepository attemptRepository;

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private GraphService graphService;

    private CandidateRetrievalService retrievalService;
    private RelevanceScoringService scoringService;

    @BeforeEach
    void setUp() {
        retrievalService = new CandidateRetrievalService(
                decisionRepository,
                attemptRepository,
                semanticSearchService,
                graphService
        );
        scoringService = new RelevanceScoringService();
    }

    @Test
    @DisplayName("Benchmark: A highly relevant decision from 6 months ago beats an unrelated decision from yesterday")
    void testHistoricalRelevantDecisionBeatsRecentUnrelatedDecision() {
        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        // Yesterday's unrelated decision
        UUID yesterdayId = UUID.randomUUID();
        Decision yesterdayDecision = Decision.builder()
                .title("Update navbar button CSS styling and hover glow")
                .rationale("Aesthetic modernization")
                .build();
        yesterdayDecision.setId(yesterdayId);
        yesterdayDecision.setCreatedAt(LocalDateTime.now().minusDays(1));

        // 6-Month-old highly relevant decision
        UUID historicalId = UUID.randomUUID();
        Decision historicalDecision = Decision.builder()
                .title("Redis Sliding Window Token Revocation Architecture")
                .rationale("Distributed token blacklist with Redis TTL for horizontal pod scale")
                .build();
        historicalDecision.setId(historicalId);
        historicalDecision.setCreatedAt(LocalDateTime.now().minusMonths(6));

        // 1. Mock recent queries (returns yesterday's decision)
        when(decisionRepository.findByRepositoryIdOrderByCreatedAtDesc(eq(repoId), any(Pageable.class)))
                .thenReturn(List.of(yesterdayDecision));
        when(decisionRepository.findByProjectIdOrderByCreatedAtDesc(eq(projId), any(Pageable.class)))
                .thenReturn(List.of());

        // 2. Mock semantic query (returns 6-month-old historical decision)
        SearchResult semanticMatch = SearchResult.builder()
                .id(historicalId.toString())
                .score(0.96f)
                .payload(Map.of("decisionId", historicalId.toString(), "title", "Redis Sliding Window Token Revocation"))
                .build();

        when(semanticSearchService.searchScoped(eq("Redis Token Revocation"), eq("technical_memory"), eq(projId.toString()), eq(repoId.toString()), anyInt()))
                .thenReturn(List.of(semanticMatch));

        when(decisionRepository.findById(historicalId))
                .thenReturn(Optional.of(historicalDecision));

        // Retrieve candidates
        List<Decision> candidates = retrievalService.getDecisionCandidates("Redis Token Revocation", repoId, projId);
        assertThat(candidates).hasSize(2);

        // Score through unified RelevanceScoringService
        Set<String> taskTokens = scoringService.extractTokens("Redis Token Revocation");
        var scoredYesterday = scoringService.scoreCandidate(
                yesterdayDecision, yesterdayDecision.getTitle() + " " + yesterdayDecision.getRationale(),
                repoId, projId, repoId, projId, "auth-repo", yesterdayDecision.getCreatedAt(), taskTokens
        );
        var scoredHistorical = scoringService.scoreCandidate(
                historicalDecision, historicalDecision.getTitle() + " " + historicalDecision.getRationale(),
                repoId, projId, repoId, projId, "auth-repo", historicalDecision.getCreatedAt(), taskTokens
        );

        List<RelevanceScoringService.ScoredCandidate<Decision>> ranked = scoringService.rankAndFilter(
                List.of(scoredYesterday, scoredHistorical), RelevanceScoringService.DEFAULT_MIN_RELEVANCE, 5
        );

        // Historical relevant decision MUST rank #1 with high relevance
        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).getItem().getId()).isEqualTo(historicalId);
        assertThat(ranked.get(0).getRelevance()).isGreaterThan(0.60);
        assertThat(ranked.get(0).getReason()).containsIgnoringCase("redis");

        // Yesterday's unrelated decision must either be filtered out or have low relevance
        assertThat(scoredYesterday.getRelevance()).isLessThan(0.30);
    }

    @Test
    @DisplayName("Failures: Combines recent failed attempts and filters out successful runs")
    void testGetFailureCandidatesHybrid() {
        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        UUID failId = UUID.randomUUID();
        AgentAttempt failedAttempt = AgentAttempt.builder()
                .approach("In-memory blacklist")
                .status("FAILED")
                .build();
        failedAttempt.setId(failId);

        UUID successId = UUID.randomUUID();
        AgentAttempt successAttempt = AgentAttempt.builder()
                .approach("Redis TTL tokens")
                .status("COMPLETED")
                .build();
        successAttempt.setId(successId);

        when(attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(eq(repoId), any(Pageable.class)))
                .thenReturn(List.of(failedAttempt, successAttempt));
        when(attemptRepository.findByProjectIdOrderByCreatedAtDesc(eq(projId), any(Pageable.class)))
                .thenReturn(List.of());

        List<AgentAttempt> candidates = retrievalService.getFailureCandidates("Cluster test", repoId, projId);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getId()).isEqualTo(failId);
    }

    @Test
    @DisplayName("Quality Fallback: Triggers project-wide symbol search when repo symbols have low score")
    void testQualityBasedSymbolFallback() {
        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        // Low quality repo symbol (0.42 < 0.65 threshold)
        SearchResult weakRepoSymbol = SearchResult.builder()
                .id("sym-weak")
                .score(0.42f)
                .content("unrelatedHelper")
                .build();

        // High quality sibling repo symbol (0.91)
        SearchResult strongSiblingSymbol = SearchResult.builder()
                .id("sym-strong")
                .score(0.91f)
                .content("validateToken")
                .build();

        when(semanticSearchService.searchScoped(eq("validateToken"), eq("symbol_knowledge"), eq(projId.toString()), eq(repoId.toString()), anyInt()))
                .thenReturn(List.of(weakRepoSymbol));
        when(semanticSearchService.searchScoped(eq("validateToken"), eq("symbol_knowledge"), eq(projId.toString()), isNull(), anyInt()))
                .thenReturn(List.of(strongSiblingSymbol));

        List<SearchResult> symbols = retrievalService.getSymbolCandidates("validateToken", repoId, projId);

        assertThat(symbols).hasSize(2);
        assertThat(symbols).extracting(SearchResult::getId).containsExactly("sym-weak", "sym-strong");
    }
}
