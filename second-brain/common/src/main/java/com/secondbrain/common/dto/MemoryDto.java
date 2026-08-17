package com.secondbrain.common.dto;

import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryDto {

    private UUID id;
    private String content;
    private MemoryType type;
    private MemoryScope scope;
    private MemoryStatus status;
    private Double confidence;
    private Double importance;
    private Integer observationCount;
    private UUID projectId;
    private UUID repositoryId;
    private Set<String> tags;
    private String sourceType;
    private UUID sourceId;
    private String sourceUrl;
    private String sourceFile;
    private Integer lineStart;
    private Integer lineEnd;
    private String sourceCommit;
    private String sourceAgent;
    private UUID sourceSession;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
