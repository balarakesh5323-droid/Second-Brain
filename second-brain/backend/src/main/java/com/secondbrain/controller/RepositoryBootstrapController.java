package com.secondbrain.controller;

import com.secondbrain.service.RepositoryBootstrapService;
import com.secondbrain.service.RepositoryBootstrapService.BootstrapResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/repositories")
@RequiredArgsConstructor
public class RepositoryBootstrapController {

    private final RepositoryBootstrapService bootstrapService;

    @PostMapping("/bootstrap")
    public ResponseEntity<BootstrapResult> bootstrap(
            @RequestParam String path) {
        BootstrapResult result = bootstrapService.bootstrap(path);
        return ResponseEntity.ok(result);
    }
}
