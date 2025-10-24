package com.starkindustries.security.controller;

import com.starkindustries.security.model.SecurityAlert;
import com.starkindustries.security.repository.SecurityAlertRepository;
import com.starkindustries.security.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Endpoints de alertas
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final SecurityAlertRepository alertRepository;

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<SecurityAlert>> getActiveAlerts() {
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    @GetMapping("/prioritized")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<SecurityAlert>> getAlertsPrioritized() {
        return ResponseEntity.ok(alertService.getAlertsPrioritized());
    }

    @GetMapping("/level/{level}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<SecurityAlert>> getAlertsByLevel(
            @PathVariable SecurityAlert.AlertLevel level
    ) {
        return ResponseEntity.ok(alertService.getActiveAlerts().stream()
                .filter(alert -> alert.getLevel() == level)
                .toList());
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<SecurityAlert> acknowledgeAlert(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                alertService.acknowledgeAlert(id, authentication.getName())
        );
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<SecurityAlert> resolveAlert(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                alertService.resolveAlert(id, authentication.getName())
        );
    }

    // Diagnóstico rápido del estado de alertas
    @GetMapping("/diagnostics")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();

        long totalAlerts = alertRepository.count();
        diagnostics.put("totalAlertsInDatabase", totalAlerts);

        List<SecurityAlert> activeAlerts = alertRepository.findByResolvedFalse();
        diagnostics.put("activeAlertsCount", activeAlerts.size());
        diagnostics.put("activeAlerts", activeAlerts);

        Map<String, Long> alertsByLevel = new HashMap<>();
        for (SecurityAlert.AlertLevel level : SecurityAlert.AlertLevel.values()) {
            long count = alertRepository.countUnresolvedByLevel(level);
            alertsByLevel.put(level.name(), count);
        }
        diagnostics.put("alertsByLevel", alertsByLevel);

        return ResponseEntity.ok(diagnostics);
    }
}
