package com.secondbrain.controller;

import com.secondbrain.common.dto.EventDto;
import com.secondbrain.common.entity.AgentEvent;
import com.secondbrain.common.enums.EventType;
import com.secondbrain.service.AgentEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final AgentEventService agentEventService;

    @PostMapping
    public ResponseEntity<AgentEvent> recordEvent(@RequestBody EventDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentEventService.recordEvent(dto));
    }

    @GetMapping
    public ResponseEntity<List<AgentEvent>> getRecent() {
        return ResponseEntity.ok(agentEventService.getRecent());
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<AgentEvent>> getBySession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(agentEventService.getBySession(sessionId));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<AgentEvent>> getByType(@PathVariable EventType type) {
        return ResponseEntity.ok(agentEventService.getByType(type));
    }
}
