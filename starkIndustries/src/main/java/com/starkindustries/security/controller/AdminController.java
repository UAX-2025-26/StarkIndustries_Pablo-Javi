package com.starkindustries.security.controller;

import com.starkindustries.security.service.AccessLogService;
import com.starkindustries.security.service.SensorSimulationService;
import com.starkindustries.security.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador administrativo - Solo accesible para ADMIN
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AccessLogService accessLogService;
    private final SensorSimulationService simulationService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Crea un nuevo usuario
     */
    @PostMapping("/users")
    public ResponseEntity<com.starkindustries.security.model.User> createUser(@RequestBody CreateUserRequest request) {
        String encoded = passwordEncoder.encode(request.password());
        com.starkindustries.security.model.User user = userService.createUser(
                request.username(),
                encoded,
                request.email(),
                request.fullName(),
                request.roles()
        );
        return ResponseEntity.ok(user);
    }

    /**
     * Obtiene todos los usuarios
     */
    @GetMapping("/users")
    public ResponseEntity<List<com.starkindustries.security.model.User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Obtiene intentos de acceso fallidos
     */
    @GetMapping("/access-logs/failed")
    public ResponseEntity<?> getFailedAttempts() {
        return ResponseEntity.ok(accessLogService.getFailedAttempts());
    }

    /**
     * Obtiene IPs sospechosas
     */
    @GetMapping("/security/suspicious-ips")
    public ResponseEntity<?> getSuspiciousIps(@RequestParam(defaultValue = "5") int threshold) {
        return ResponseEntity.ok(accessLogService.getSuspiciousIpAddresses(threshold));
    }

    /**
     * Controla la simulación de sensores
     */
    @PostMapping("/simulation/enable")
    public ResponseEntity<String> enableSimulation() {
        simulationService.enableSimulation();
        return ResponseEntity.ok("Simulación habilitada");
    }

    @PostMapping("/simulation/disable")
    public ResponseEntity<String> disableSimulation() {
        simulationService.disableSimulation();
        return ResponseEntity.ok("Simulación deshabilitada");
    }

    /**
     * Obtiene información del sistema
     */
    @GetMapping("/system/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        return ResponseEntity.ok(Map.of(
                "name", "Stark Industries Security System",
                "version", "1.0.0",
                "status", "operational",
                "activeThreads", Thread.activeCount(),
                "totalMemory", Runtime.getRuntime().totalMemory() / 1024 / 1024 + " MB",
                "freeMemory", Runtime.getRuntime().freeMemory() / 1024 / 1024 + " MB"
        ));
    }

    public record CreateUserRequest(
            String username,
            String password,
            String email,
            String fullName,
            List<String> roles
    ) {}
}
