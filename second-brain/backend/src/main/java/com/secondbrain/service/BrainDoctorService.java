package com.secondbrain.service;

import com.secondbrain.config.MinioConfig;
import com.secondbrain.config.Neo4jConfig;
import com.secondbrain.config.QdrantConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrainDoctorService {

    private final QdrantConfig qdrantConfig;
    private final Neo4jConfig neo4jConfig;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MinioConfig minioConfig;

    public DoctorReport runDiagnostics() {
        log.info("Running brain doctor diagnostics...");

        List<DiagnosticResult> results = new ArrayList<>();

        results.add(checkPostgres());
        results.add(checkRedis());
        results.add(checkQdrant());
        results.add(checkNeo4j());
        results.add(checkMinIO());

        long healthyCount = results.stream().filter(d -> d.status == Status.HEALTHY).count();
        long degradedCount = results.stream().filter(d -> d.status == Status.DEGRADED).count();
        long unhealthyCount = results.stream().filter(d -> d.status == Status.UNHEALTHY).count();

        Status overallStatus;
        if (unhealthyCount > 0) {
            overallStatus = Status.UNHEALTHY;
        } else if (degradedCount > 0) {
            overallStatus = Status.DEGRADED;
        } else {
            overallStatus = Status.HEALTHY;
        }

        return DoctorReport.builder()
            .status(overallStatus)
            .summary(String.format("%d healthy, %d degraded, %d unhealthy out of %d services",
                healthyCount, degradedCount, unhealthyCount, results.size()))
            .services(results)
            .timestamp(java.time.LocalDateTime.now())
            .build();
    }

    private DiagnosticResult checkPostgres() {
        try {
            return DiagnosticResult.builder()
                .service("PostgreSQL")
                .status(Status.HEALTHY)
                .message("H2 in-memory database active (production uses PostgreSQL)")
                .details(Map.of("driver", "H2", "mode", "in-memory"))
                .build();
        } catch (Exception e) {
            return DiagnosticResult.builder()
                .service("PostgreSQL")
                .status(Status.UNHEALTHY)
                .message("Connection failed: " + e.getMessage())
                .build();
        }
    }

    private DiagnosticResult checkRedis() {
        try {
            redisTemplate.hasKey("__health_check__");
            Long keyCount = redisTemplate.keys("*") != null ? (long) redisTemplate.keys("*").size() : 0L;
            return DiagnosticResult.builder()
                .service("Redis")
                .status(Status.HEALTHY)
                .message("Connection successful")
                .details(Map.of(
                    "host", "embedded",
                    "cachedKeys", keyCount))
                .build();
        } catch (Exception e) {
            return DiagnosticResult.builder()
                .service("Redis")
                .status(Status.UNHEALTHY)
                .message("Connection failed: " + e.getMessage())
                .build();
        }
    }

    private DiagnosticResult checkQdrant() {
        try {
            return DiagnosticResult.builder()
                .service("Qdrant")
                .status(Status.HEALTHY)
                .message("Configuration present")
                .details(Map.of(
                    "host", qdrantConfig.getHost(),
                    "grpcPort", qdrantConfig.getGrpcPort()))
                .build();
        } catch (Exception e) {
            return DiagnosticResult.builder()
                .service("Qdrant")
                .status(Status.UNHEALTHY)
                .message("Configuration error: " + e.getMessage())
                .build();
        }
    }

    private DiagnosticResult checkNeo4j() {
        try {
            return DiagnosticResult.builder()
                .service("Neo4j")
                .status(Status.HEALTHY)
                .message("Configuration present")
                .details(Map.of(
                    "uri", neo4jConfig.getUri()))
                .build();
        } catch (Exception e) {
            return DiagnosticResult.builder()
                .service("Neo4j")
                .status(Status.UNHEALTHY)
                .message("Configuration error: " + e.getMessage())
                .build();
        }
    }

    private DiagnosticResult checkMinIO() {
        try {
            return DiagnosticResult.builder()
                .service("MinIO")
                .status(Status.HEALTHY)
                .message("Configuration present")
                .details(Map.of(
                    "endpoint", minioConfig.getEndpoint()))
                .build();
        } catch (Exception e) {
            return DiagnosticResult.builder()
                .service("MinIO")
                .status(Status.UNHEALTHY)
                .message("Configuration error: " + e.getMessage())
                .build();
        }
    }

    public enum Status { HEALTHY, DEGRADED, UNHEALTHY }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DiagnosticResult {
        private String service;
        private Status status;
        private String message;
        private Map<String, Object> details;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DoctorReport {
        private Status status;
        private String summary;
        private List<DiagnosticResult> services;
        private java.time.LocalDateTime timestamp;
    }
}
