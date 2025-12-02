package com.starkindustries.security.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// Registro de accesos (login, logout, llamadas a API, etc.)
@Entity
@Table(name = "access_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario asociado al evento de acceso (puede ser null si no autenticado)
    private String username;

    // IP de origen de la petición
    @Column(nullable = false)
    private String ipAddress;

    // Tipo de acceso (LOGIN, LOGOUT, etc.)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessType accessType;

    // Indica si la operación fue exitosa
    @Column(nullable = false)
    private Boolean successful;

    // Razón del fallo en caso de acceso no exitoso
    private String failureReason;

    // Momento en que se registró el acceso
    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Información adicional del cliente (navegador, agente HTTP, etc.)
    private String userAgent;

    // Ubicación geográfica aproximada (si se resolviera a partir de la IP)
    private String location;

    // Tipos soportados de evento de acceso
    public enum AccessType {
        LOGIN, LOGOUT, API_ACCESS, SENSOR_ACCESS, ADMIN_PANEL
    }
}
