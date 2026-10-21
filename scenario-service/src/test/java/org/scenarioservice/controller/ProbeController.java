package org.scenarioservice.controller;

import org.scenarioservice.service.ProbeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * TEST-ONLY controller, kept after T8 for what the real endpoints can't show on demand (review point A, deviation 5):
 * a database unique violation, lock timeout or deadlock reaching the advice (P17, D14 item 12), and a transactional
 * use case whose commit can be seen in the log, to prove the logging aspect wraps the transaction (T4).
 */
@RestController
@RequestMapping("/api/projects/{projectId}/probe")
class ProbeController {

    private final ProbeService probeService;

    ProbeController(ProbeService probeService) {
        this.probeService = probeService;
    }

    @PostMapping("/transactions")
    ResponseEntity<Map<String, Object>> transactional(@PathVariable UUID projectId) {
        return ResponseEntity.ok(Map.of("transactionActive", probeService.transactional()));
    }

    @GetMapping("/failures/{kind}")
    ResponseEntity<Void> fail(@PathVariable UUID projectId, @PathVariable String kind) {
        probeService.fail(kind);
        return ResponseEntity.noContent().build();
    }
}
