package com.starkindustries.security.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// Alerta de seguridad generada a partir de un evento crítico de sensor
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

    // Nivel de severidad de la alerta
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertLevel level;

    // Título corto de la alerta (usado en UI y notificaciones)
    @Column(nullable = false)
    private String title;

    // Mensaje descriptivo con más contexto
    @Column(length = 1000)
    private String message;

    // Tipo de sensor relacionado (opcional, puede ser null)
    @Enumerated(EnumType.STRING)
    private SensorType relatedSensorType;

    // Identificador del sensor que originó la alerta (si aplica)
    private String sensorId;

    // Ubicación donde se ha detectado el incidente
    private String location;

    // Fecha/hora en que se creó la alerta
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Momento en que alguien reconoció la alerta en el dashboard
    private LocalDateTime acknowledgedAt;

    // Usuario que reconoció la alerta
    private String acknowledgedBy;

    // Indica si la alerta se considera resuelta
    @Column(nullable = false)
    private Boolean resolved;

    // Niveles posibles de criticidad
    public enum AlertLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
