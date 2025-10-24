package com.starkindustries.security.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// Registro de accesos
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

    private String username;

    @Column(nullable = false)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessType accessType;

    @Column(nullable = false)
    private Boolean successful;

    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private String userAgent;

    private String location;

    public enum AccessType {
        LOGIN, LOGOUT, API_ACCESS, SENSOR_ACCESS, ADMIN_PANEL
    }
}
