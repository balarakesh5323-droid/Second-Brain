package com.secondbrain.controller;

import com.secondbrain.service.BrainDoctorService;
import com.secondbrain.service.BrainDoctorService.DoctorReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final BrainDoctorService brainDoctorService;

    @GetMapping("/doctor")
    public ResponseEntity<DoctorReport> runDiagnostics() {
        DoctorReport report = brainDoctorService.runDiagnostics();
        return ResponseEntity.ok(report);
    }
}
