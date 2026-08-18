package com.secondbrain.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "consolidation_checkpoints")
public class ConsolidationCheckpoint extends BaseEntity {

    @Column(name = "checkpoint_key", nullable = false, unique = true, length = 64)
    private String checkpointKey;

    private LocalDateTime lastProcessedAt;

    @Column(columnDefinition = "uuid")
    private UUID lastProcessedId;

    @Builder.Default
    private Long processedCount = 0L;

    @Column(length = 32)
    private String lastRunStatus;
}
