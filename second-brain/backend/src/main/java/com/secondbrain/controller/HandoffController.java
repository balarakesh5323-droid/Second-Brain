package com.secondbrain.controller;

import com.secondbrain.common.entity.AgentHandoff;
import com.secondbrain.service.AgentHandoffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/handoffs")
@RequiredArgsConstructor
public class HandoffController {

    private final AgentHandoffService agentHandoffService;

    @PostMapping
    public ResponseEntity<AgentHandoff> createHandoff(@RequestBody AgentHandoff handoff) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentHandoffService.createHandoff(handoff));
    }

    @GetMapping
    public ResponseEntity<java.util.List<AgentHandoff>> getAllHandoffs() {
        return ResponseEntity.ok(agentHandoffService.getAll());
    }

    @GetMapping("/repository/{repositoryId}/latest")
    public ResponseEntity<AgentHandoff> getLatestForRepository(@PathVariable UUID repositoryId) {
        AgentHandoff handoff = agentHandoffService.getLatestForRepository(repositoryId);
        if (handoff == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(handoff);
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<AgentHandoff> getBySession(@PathVariable UUID sessionId) {
        AgentHandoff handoff = agentHandoffService.getBySession(sessionId);
        if (handoff == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(handoff);
    }
}
