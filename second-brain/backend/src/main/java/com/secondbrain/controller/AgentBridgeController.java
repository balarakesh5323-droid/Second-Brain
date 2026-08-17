package com.secondbrain.controller;

import com.secondbrain.common.entity.AgentAttempt;
import com.secondbrain.service.AgentBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bridge")
@RequiredArgsConstructor
public class AgentBridgeController {

    private final AgentBridgeService bridgeService;

    @PostMapping("/activity")
    public ResponseEntity<Map<String, Object>> ingestActivity(@RequestBody AgentBridgeService.ActivityPayload payload) {
        return ResponseEntity.ok(bridgeService.ingestActivity(payload));
    }

    @PostMapping("/attempts")
    public ResponseEntity<AgentAttempt> recordAttempt(@RequestBody AgentBridgeService.AgentAttemptDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bridgeService.recordAttempt(dto));
    }

    @GetMapping("/attempts")
    public ResponseEntity<List<AgentAttempt>> getAllAttempts() {
        return ResponseEntity.ok(bridgeService.getAllAttempts());
    }

    @GetMapping("/attempts/repository/{repositoryId}")
    public ResponseEntity<List<AgentAttempt>> getAttemptsByRepository(@PathVariable UUID repositoryId) {
        return ResponseEntity.ok(bridgeService.getAttemptsByRepository(repositoryId));
    }

    @GetMapping("/continuity")
    public ResponseEntity<Map<String, Object>> getContinuityState(
            @RequestParam(value = "repo", required = false) String repo) {
        return ResponseEntity.ok(bridgeService.getContinuityState(repo));
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> recordFullSession(@RequestBody AgentBridgeService.FullSessionPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bridgeService.recordFullSession(payload));
    }

    @PostMapping("/session/start")
    public ResponseEntity<Map<String, Object>> startSession(@RequestBody AgentBridgeService.StartSessionPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bridgeService.startSession(payload));
    }

    @PostMapping("/session/{sessionId}/event")
    public ResponseEntity<Map<String, Object>> appendSessionEvent(
            @PathVariable UUID sessionId,
            @RequestBody AgentBridgeService.SessionEventPayload payload) {
        return ResponseEntity.ok(bridgeService.appendSessionEvent(sessionId, payload));
    }

    @PostMapping("/session/{sessionId}/complete")
    public ResponseEntity<Map<String, Object>> completeSession(
            @PathVariable UUID sessionId,
            @RequestBody AgentBridgeService.CompleteSessionPayload payload) {
        return ResponseEntity.ok(bridgeService.completeSession(sessionId, payload));
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<Map<String, Object>>> getAgentTimeline(
            @RequestParam(value = "repo", required = false) String repo,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(bridgeService.getAgentTimeline(repo, limit));
    }

    @GetMapping("/workspace-state")
    public ResponseEntity<Map<String, Object>> getWorkspaceState(
            @RequestParam(value = "project", required = false) String project,
            @RequestParam(value = "repo", required = false) String repo) {
        return ResponseEntity.ok(bridgeService.getWorkspaceState(project, repo));
    }
}
