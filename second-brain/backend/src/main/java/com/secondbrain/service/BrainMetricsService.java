package com.secondbrain.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrainMetricsService {

    private final MeterRegistry registry;

    private Counter handoffsCounter;
    private Counter memoryConflictsCounter;
    private Counter memoryDedupCounter;
    private Counter embeddingFailuresCounter;
    private Timer retrievalTimer;
    private Timer searchTimer;

    @PostConstruct
    public void init() {
        handoffsCounter = Counter.builder("brain_handoff_total")
                .description("Total number of agent handoffs created")
                .register(registry);

        memoryConflictsCounter = Counter.builder("brain_memory_conflict_total")
                .description("Total number of memory conflicts/contradictions detected")
                .register(registry);

        memoryDedupCounter = Counter.builder("brain_memory_dedup_total")
                .description("Total number of deduplicated memories")
                .register(registry);

        embeddingFailuresCounter = Counter.builder("brain_embedding_failures_total")
                .description("Total number of embedding service failures")
                .register(registry);

        retrievalTimer = Timer.builder("brain_retrieval_latency")
                .description("Latency for full context assembly & retrieval")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        searchTimer = Timer.builder("brain_search_latency")
                .description("Latency for semantic search queries")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        log.info("Initialized Second Brain Micrometer & Prometheus metrics");
    }

    public void recordMcpRequest(String tool, String status) {
        Counter.builder("brain_mcp_requests_total")
                .tag("tool", tool != null ? tool : "unknown")
                .tag("status", status != null ? status : "success")
                .description("Total MCP tool calls handled")
                .register(registry)
                .increment();
    }

    public void recordMemoryIngestion(String type, String scope) {
        Counter.builder("brain_memory_ingestion_total")
                .tag("type", type != null ? type : "unknown")
                .tag("scope", scope != null ? scope : "unknown")
                .description("Total memories ingested")
                .register(registry)
                .increment();
    }

    public void recordAgentAttempt(String agent, String status) {
        Counter.builder("brain_agent_attempts_total")
                .tag("agent", agent != null ? agent : "unknown")
                .tag("status", status != null ? status : "unknown")
                .description("Total agent engineering attempts recorded")
                .register(registry)
                .increment();
    }

    public void recordAgentEvent(String agent, String action) {
        Counter.builder("brain_agent_events_total")
                .tag("agent", agent != null ? agent : "unknown")
                .tag("action", action != null ? action : "unknown")
                .description("Total agent events recorded")
                .register(registry)
                .increment();
    }

    public void recordRetrievalTime(long durationMs) {
        retrievalTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordSearchTime(long durationMs) {
        searchTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordHandoff() {
        handoffsCounter.increment();
    }

    public void recordMemoryConflict() {
        memoryConflictsCounter.increment();
    }

    public void recordMemoryDedup() {
        memoryDedupCounter.increment();
    }

    public void recordEmbeddingFailure() {
        embeddingFailuresCounter.increment();
    }
}
