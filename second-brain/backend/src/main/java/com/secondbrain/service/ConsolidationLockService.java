package com.secondbrain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsolidationLockService {

    public static final long CONSOLIDATION_LOCK_ID = 482910482910L;
    private final DataSource dataSource;
    private final ReentrantLock localFallbackLock = new ReentrantLock();

    /**
     * Attempts to acquire distributed lock for consolidation.
     * Uses PostgreSQL session-level advisory lock when running against PostgreSQL,
     * with local ReentrantLock fallback for in-memory test databases.
     */
    public boolean tryAcquireLock() {
        try (Connection conn = dataSource.getConnection()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (dbProduct.contains("postgres")) {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                Boolean acquired = jdbc.queryForObject(
                        "SELECT pg_try_advisory_lock(?)", Boolean.class, CONSOLIDATION_LOCK_ID
                );
                return Boolean.TRUE.equals(acquired);
            }
        } catch (Exception e) {
            log.debug("Advisory lock check failed, using JVM lock fallback: {}", e.getMessage());
        }
        return localFallbackLock.tryLock();
    }

    /**
     * Releases distributed consolidation lock.
     */
    public void releaseLock() {
        try (Connection conn = dataSource.getConnection()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (dbProduct.contains("postgres")) {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.queryForObject(
                        "SELECT pg_advisory_unlock(?)", Boolean.class, CONSOLIDATION_LOCK_ID
                );
                return;
            }
        } catch (Exception e) {
            log.debug("Advisory lock release failed, unlocking JVM fallback: {}", e.getMessage());
        }
        if (localFallbackLock.isHeldByCurrentThread()) {
            localFallbackLock.unlock();
        }
    }
}
