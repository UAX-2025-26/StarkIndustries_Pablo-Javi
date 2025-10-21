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

/**
 * Controlador REST para gestión de alertas de seguridad
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final SecurityAlertRepository alertRepository;

    /**
     * Obtiene todas las alertas activas (no resueltas)
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<SecurityAlert>> getActiveAlerts() {
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    /**
     * Obtiene alertas priorizadas por nivel de severidad
     */
    @GetMapping("/prioritized")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<SecurityAlert>> getAlertsPrioritized() {
        return ResponseEntity.ok(alertService.getAlertsPrioritized());
    }

    /**
     * Obtiene alertas por nivel
     */
    @GetMapping("/level/{level}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<SecurityAlert>> getAlertsByLevel(
            @PathVariable SecurityAlert.AlertLevel level
    ) {
        return ResponseEntity.ok(alertService.getActiveAlerts().stream()
                .filter(alert -> alert.getLevel() == level)
                .toList());
    }

    /**
     * Reconoce una alerta
     */
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

    /**
     * Resuelve una alerta
     */
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

    /**
     * Endpoint de diagnóstico para verificar el estado de las alertas
     */
    @GetMapping("/diagnostics")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();

        // Total de alertas
        long totalAlerts = alertRepository.count();
        diagnostics.put("totalAlertsInDatabase", totalAlerts);

        // Alertas activas
        List<SecurityAlert> activeAlerts = alertRepository.findByResolvedFalse();
        diagnostics.put("activeAlertsCount", activeAlerts.size());
        diagnostics.put("activeAlerts", activeAlerts);

        // Alertas por nivel
        Map<String, Long> alertsByLevel = new HashMap<>();
        for (SecurityAlert.AlertLevel level : SecurityAlert.AlertLevel.values()) {
            long count = alertRepository.countUnresolvedByLevel(level);
            alertsByLevel.put(level.name(), count);
        }
        diagnostics.put("alertsByLevel", alertsByLevel);

        return ResponseEntity.ok(diagnostics);
    }
}
