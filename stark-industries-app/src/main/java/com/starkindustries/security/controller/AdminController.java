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

// Controlador de administración: gestión de usuarios, logs y configuración del sistema
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AccessLogService accessLogService;
    private final SensorSimulationService simulationService;
    private final PasswordEncoder passwordEncoder;

    // Crea un nuevo usuario a partir de los datos recibidos en el cuerpo de la petición
    @PostMapping("/users")
    public ResponseEntity<com.starkindustries.security.model.User> createUser(@RequestBody CreateUserRequest request) {
        // Siempre se codifica la contraseña antes de persistirla
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

    // Devuelve todos los usuarios registrados en el sistema
    @GetMapping("/users")
    public ResponseEntity<List<com.starkindustries.security.model.User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // Lista de intentos de acceso fallidos registrados
    @GetMapping("/access-logs/failed")
    public ResponseEntity<?> getFailedAttempts() {
        return ResponseEntity.ok(accessLogService.getFailedAttempts());
    }

    // Devuelve IPs sospechosas (con muchos fallos) según un umbral configurable
    @GetMapping("/security/suspicious-ips")
    public ResponseEntity<?> getSuspiciousIps(@RequestParam(defaultValue = "5") int threshold) {
        return ResponseEntity.ok(accessLogService.getSuspiciousIpAddresses(threshold));
    }

    // Activa la simulación automática de eventos de sensores
    @PostMapping("/simulation/enable")
    public ResponseEntity<String> enableSimulation() {
        simulationService.enableSimulation();
        return ResponseEntity.ok("Simulación habilitada");
    }

    // Desactiva la simulación automática de eventos de sensores
    @PostMapping("/simulation/disable")
    public ResponseEntity<String> disableSimulation() {
        simulationService.disableSimulation();
        return ResponseEntity.ok("Simulación deshabilitada");
    }

    // Información básica del sistema para diagnóstico rápido
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

    // DTO para la creación de usuarios desde la API de administración
    public record CreateUserRequest(
            String username,
            String password,
            String email,
            String fullName,
            List<String> roles
    ) {}
}
