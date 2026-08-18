-- ====================================================================
-- Second Brain Database Schema & Integrity Constraints Migration (V1)
-- ====================================================================

-- 1. Ensure Memory Key Unique Constraint
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_memory_key'
    ) THEN
        ALTER TABLE memories ADD CONSTRAINT uk_memory_key UNIQUE (memory_key);
    END IF;
END $$;

-- 2. Composite Cursoring Indexes for Incremental Consolidation
CREATE INDEX IF NOT EXISTS idx_decisions_created_at_id ON decisions (created_at ASC, id ASC);
CREATE INDEX IF NOT EXISTS idx_agent_attempts_status_created_at_id ON agent_attempts (status, created_at ASC, id ASC);
CREATE INDEX IF NOT EXISTS idx_agent_sessions_status_created_at_id ON agent_sessions (status, created_at ASC, id ASC);
CREATE INDEX IF NOT EXISTS idx_agent_events_created_at_id ON agent_events (created_at ASC, id ASC);
CREATE INDEX IF NOT EXISTS idx_memories_memory_key ON memories (memory_key);
CREATE INDEX IF NOT EXISTS idx_consolidation_checkpoints_key ON consolidation_checkpoints (checkpoint_key);
