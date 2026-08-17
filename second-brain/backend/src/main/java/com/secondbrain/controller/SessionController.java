package com.secondbrain.controller;

import com.secondbrain.common.entity.AgentSession;
import com.secondbrain.service.AgentSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final AgentSessionService agentSessionService;

    @PostMapping("/start")
    public ResponseEntity<AgentSession> startSession(
            @RequestParam UUID agentId,
            @RequestParam String task,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) UUID projectId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agentSessionService.startSession(agentId, task, repositoryId, projectId));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<AgentSession> endSession(
            @PathVariable UUID id,
            @RequestParam(required = false) String summary) {
        return ResponseEntity.ok(agentSessionService.endSession(id, summary));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentSession> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(agentSessionService.getById(id));
    }

    @GetMapping("/agent/{agentId}")
    public ResponseEntity<List<AgentSession>> getByAgent(@PathVariable UUID agentId) {
        return ResponseEntity.ok(agentSessionService.getByAgent(agentId));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<AgentSession>> getRecent() {
        return ResponseEntity.ok(agentSessionService.getRecent());
    }
}
