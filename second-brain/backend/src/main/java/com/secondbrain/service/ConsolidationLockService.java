package com.secondbrain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.locks.ReentrantLock;

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
     * Keeps the dedicated PostgreSQL connection alive across the entire consolidation run
     * so that the session-level advisory lock remains active until releaseLock() is called.
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
                // If lock was not acquired, close connection immediately
                conn.close();
                log.info("🔒 PostgreSQL advisory lock already held by another pod/worker.");
                return false;
            } else {
                // In-memory test DB fallback (H2)
                conn.close();
                return localFallbackLock.tryLock();
            }
        } catch (Exception e) {
            log.warn("Failed acquiring advisory lock, using JVM lock fallback: {}", e.getMessage());
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
            return localFallbackLock.tryLock();
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
