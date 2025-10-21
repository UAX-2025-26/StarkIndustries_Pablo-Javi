package com.starkindustries.security.sensor;

import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.model.SensorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Bean gestionado por Spring para sensor de movimiento
 * Detecta presencia y movimiento en áreas monitoreadas
 */
@Component("motionSensor")
@Slf4j
public class MotionSensor implements Sensor {

    @Value("${security.sensor.motion.threshold:5}")
    private int threshold;

    private final Random random = new Random();
    private final String[] locations = {
        "Entrada Principal", "Laboratorio", "Bóveda",
        "Sala de Servidores", "Oficina Ejecutiva", "Pasillo Norte"
    };

    @Override
    public SensorEvent processEvent(SensorEvent event) {
        long startTime = System.currentTimeMillis();

        log.debug("Procesando evento de movimiento en: {}", event.getLocation());

        // Simular procesamiento (análisis de patrones, correlación, etc.)
        try {
            Thread.sleep(random.nextInt(100) + 50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        event.setProcessedAt(LocalDateTime.now());
        event.setProcessedBy(Thread.currentThread().getName());
        event.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        event.setCritical(requiresAlert(event.getValue()));

        log.info("Evento procesado - Movimiento detectado: {} unidades en {}",
                 event.getValue(), event.getLocation());

        return event;
    }

    @Override
    public boolean requiresAlert(Double value) {
        return value >= threshold;
    }

    @Override
    public String getSensorType() {
        return SensorType.MOTION.name();
    }

    @Override
    public SensorEvent simulateEvent() {
        return SensorEvent.builder()
                .sensorType(SensorType.MOTION)
                .sensorId("MOTION-" + UUID.randomUUID().toString().substring(0, 8))
                .location(locations[random.nextInt(locations.length)])
                .value((double) random.nextInt(15))
                .unit("detecciones/min")
                .description("Movimiento detectado por sensor infrarrojo")
                .timestamp(LocalDateTime.now())
                .critical(false)
                .build();
    }
}

