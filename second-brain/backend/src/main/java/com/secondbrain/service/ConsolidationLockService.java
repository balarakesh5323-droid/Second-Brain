package com.secondbrain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Distributed Advisory Lock Service for Second Brain Consolidation.
 *
 * Invariant: tryAcquireLock() and releaseLock() must be invoked on the same thread
 * due to ThreadLocal connection lifecycle binding for PostgreSQL session-level advisory locks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsolidationLockService {

    public static final long CONSOLIDATION_LOCK_ID = 482910482910L;
    private final DataSource dataSource;
    private final ReentrantLock localFallbackLock = new ReentrantLock();

    // Holds the dedicated database connection across the entire consolidation lifecycle
    private final ThreadLocal<Connection> activeConnectionHolder = new ThreadLocal<>();

    /**
     * Attempts to acquire distributed lock for consolidation.
     * In production (PostgreSQL): Session-level advisory lock with fail-closed behavior on errors.
     * In test (H2): JVM ReentrantLock fallback.
     */
    public boolean tryAcquireLock() {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();

            if (dbProduct.contains("postgres")) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                    ps.setLong(1, CONSOLIDATION_LOCK_ID);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getBoolean(1)) {
                            // Lock acquired: preserve connection across consolidation cycle
                            activeConnectionHolder.set(conn);
                            log.info("🔒 PostgreSQL advisory lock acquired (id: {}). Connection held active.", CONSOLIDATION_LOCK_ID);
                            return true;
                        }
                    }
                }
                // Lock was not acquired: close connection immediately and return false
                conn.close();
                log.info("🔒 PostgreSQL advisory lock already held by another pod/worker. Skipping.");
                return false;
            } else {
                // Non-PostgreSQL environment (e.g. H2 in testing)
                conn.close();
                return localFallbackLock.tryLock();
            }
        } catch (Exception e) {
            log.error("❌ Failed attempting to acquire distributed advisory lock: {}", e.getMessage(), e);
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
            // Fail-closed in PostgreSQL environment to prevent split-brain execution
            return false;
        }
    }

    /**
     * Releases distributed consolidation lock and closes the held database connection.
     */
    public void releaseLock() {
        Connection conn = activeConnectionHolder.get();
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                        ps.setLong(1, CONSOLIDATION_LOCK_ID);
                        ps.execute();
                    }
                }
            } catch (Exception e) {
                log.warn("Error releasing PostgreSQL advisory lock: {}", e.getMessage());
            } finally {
                try {
                    conn.close();
                } catch (Exception ignored) {}
                activeConnectionHolder.remove();
                log.info("🔓 PostgreSQL advisory lock released and connection closed.");
            }
        }

        if (localFallbackLock.isHeldByCurrentThread()) {
            localFallbackLock.unlock();
        }
    }
}
