package com.starkindustries.security.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// Evento de sensor: representa una lectura capturada y procesada por el sistema
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

    // Tipo de sensor que generó el evento (TEMPERATURE, MOTION, ACCESS,...)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SensorType sensorType;

    // Identificador lógico del sensor origen
    @Column(nullable = false)
    private String sensorId;

    // Ubicación física o lógica del sensor
    @Column(nullable = false)
    private String location;

    // Valor medido por el sensor
    @Column(name = "sensor_value", nullable = false)
    private Double value;

    // Unidad del valor (°C, intentos, detecciones/min,...)
    private String unit;

    // Descripción adicional del evento
    private String description;

    // Indica si el evento ha sido clasificado como crítico
    @Column(nullable = false)
    private Boolean critical;

    // Momento en el que ocurrió la medición
    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Momento en el que el sistema terminó de procesar el evento
    @Column(nullable = false)
    private LocalDateTime processedAt;

    // Nombre del hilo que procesó el evento (útil para diagnóstico de concurrencia)
    private String processedBy;

    // Tiempo de procesamiento en milisegundos entre recepción y fin de procesamiento
    @Column(nullable = false)
    private Long processingTimeMs;
}
