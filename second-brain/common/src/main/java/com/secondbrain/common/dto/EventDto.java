package com.secondbrain.common.dto;

import com.secondbrain.common.enums.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDto {

    private UUID id;
    private UUID sessionId;
    private EventType eventType;
    private String description;
    private String filePath;
    private String details;
    private String status;
    private LocalDateTime createdAt;
}
