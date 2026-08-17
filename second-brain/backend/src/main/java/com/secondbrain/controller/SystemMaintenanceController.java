package com.secondbrain.controller;

import com.secondbrain.service.BrainMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Slf4j
public class SystemMaintenanceController {

    private final BrainMaintenanceService maintenanceService;

    @PostMapping("/wipe")
    public ResponseEntity<Map<String, Object>> wipeWholeBrainPost() {
        log.warn("Received API request to wipe whole brain (POST)");
        Map<String, Object> result = maintenanceService.wipeWholeBrain();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/wipe")
    public ResponseEntity<Map<String, Object>> wipeWholeBrainDelete() {
        log.warn("Received API request to wipe whole brain (DELETE)");
        Map<String, Object> result = maintenanceService.wipeWholeBrain();
        return ResponseEntity.ok(result);
    }
}
