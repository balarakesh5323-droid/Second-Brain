package com.secondbrain.common.repository;

import com.secondbrain.common.entity.ConsolidationCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsolidationCheckpointRepository extends JpaRepository<ConsolidationCheckpoint, UUID> {
    Optional<ConsolidationCheckpoint> findByCheckpointKey(String checkpointKey);
}
