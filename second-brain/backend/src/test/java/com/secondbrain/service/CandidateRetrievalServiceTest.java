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

    @BeforeEach
    void setUp() {
        retrievalService = new CandidateRetrievalService(
                decisionRepository,
                attemptRepository,
                semanticSearchService,
                graphService
        );
    }

    @Test
    @DisplayName("Decisions: Combines recent PostgreSQL decisions with semantic Qdrant hits without duplicates")
    void testGetDecisionCandidatesHybrid() {
        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        UUID recentDecId = UUID.randomUUID();
        Decision recentDecision = Decision.builder()
                .title("PostgreSQL Connection Pooling")
                .build();
        recentDecision.setId(recentDecId);
        recentDecision.setCreatedAt(LocalDateTime.now());

        UUID semanticDecId = UUID.randomUUID();
        Decision historicalSemanticDecision = Decision.builder()
                .title("Historical Redis Blacklist Decision from 6 Months Ago")
                .build();
        historicalSemanticDecision.setId(semanticDecId);
        historicalSemanticDecision.setCreatedAt(LocalDateTime.now().minusMonths(6));

        // 1. Mock recent queries
        when(decisionRepository.findByRepositoryIdOrderByCreatedAtDesc(eq(repoId), any(Pageable.class)))
                .thenReturn(List.of(recentDecision));
        when(decisionRepository.findByProjectIdOrderByCreatedAtDesc(eq(projId), any(Pageable.class)))
                .thenReturn(List.of());

        // 2. Mock semantic query
        SearchResult semanticMatch = SearchResult.builder()
                .id(semanticDecId.toString())
                .score(0.95f)
                .payload(Map.of("decisionId", semanticDecId.toString(), "title", "Historical Redis Blacklist"))
                .build();

        when(semanticSearchService.searchScoped(eq("Redis Token Revocation"), eq("technical_memory"), eq(projId.toString()), eq(repoId.toString()), anyInt()))
                .thenReturn(List.of(semanticMatch));

        when(decisionRepository.findById(semanticDecId))
                .thenReturn(Optional.of(historicalSemanticDecision));

        // Execute
        List<Decision> candidates = retrievalService.getDecisionCandidates("Redis Token Revocation", repoId, projId);

        assertThat(candidates).hasSize(2);
        assertThat(candidates).extracting(Decision::getId).containsExactly(recentDecId, semanticDecId);
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
    @DisplayName("Symbols: Resolves repository symbols and falls back to project sibling libraries")
    void testGetSymbolCandidates() {
        UUID repoId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        SearchResult repoSymbol = SearchResult.builder()
                .id("sym-1")
                .score(0.88f)
                .content("validateToken")
                .build();

        SearchResult siblingSymbol = SearchResult.builder()
                .id("sym-2")
                .score(0.91f)
                .content("TokenCodec")
                .build();

        when(semanticSearchService.searchScoped(eq("validateToken"), eq("symbol_knowledge"), eq(projId.toString()), eq(repoId.toString()), anyInt()))
                .thenReturn(List.of(repoSymbol));
        when(semanticSearchService.searchScoped(eq("validateToken"), eq("symbol_knowledge"), eq(projId.toString()), isNull(), anyInt()))
                .thenReturn(List.of(siblingSymbol));

        List<SearchResult> symbols = retrievalService.getSymbolCandidates("validateToken", repoId, projId);

        assertThat(symbols).hasSize(2);
        assertThat(symbols).extracting(SearchResult::getId).containsExactly("sym-1", "sym-2");
    }
}
