package com.starkindustries.security.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// Evento de sensor
@Entity
@Table(name = "sensor_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SensorType sensorType;

    @Column(nullable = false)
    private String sensorId;

    @Column(nullable = false)
    private String location;

    @Column(name = "sensor_value", nullable = false)
    private Double value;

    private String unit;

    private String description;

    @Column(nullable = false)
    private Boolean critical;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    private String processedBy;

    @Column(nullable = false)
    private Long processingTimeMs;
}
