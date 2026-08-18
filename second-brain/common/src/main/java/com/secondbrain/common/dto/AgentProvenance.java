package com.secondbrain.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProvenance implements Serializable {
    private String agentName;
    private String agentType;
    private String sessionId;
    private String repositoryName;
    private String actionType;
    private LocalDateTime timestamp;
}
