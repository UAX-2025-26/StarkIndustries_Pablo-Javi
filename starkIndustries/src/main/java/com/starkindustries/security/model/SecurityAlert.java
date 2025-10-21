package com.starkindustries.security.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entidad que representa una alerta de seguridad crítica
 */
@Entity
@Table(name = "security_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertLevel level;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    private SensorType relatedSensorType;

    private String sensorId;

    private String location;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime acknowledgedAt;

    private String acknowledgedBy;

    @Column(nullable = false)
    private Boolean resolved;

    public enum AlertLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}

