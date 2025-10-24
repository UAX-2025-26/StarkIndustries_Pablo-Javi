package com.starkindustries.security.sensor;

import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.model.SensorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

// Sensor de movimiento
@Component("motionSensor")
@Slf4j
public class MotionSensor implements Sensor {

    @Value("${security.sensor.motion.threshold:5}")
    private int threshold;

    @Value("${security.sensor.motion.threshold.high:9}")
    private int highThreshold;

    private final Random random = new Random();
    private final String[] locations = {
        "Entrada Principal", "Laboratorio", "Bóveda",
        "Sala de Servidores", "Oficina Ejecutiva", "Pasillo Norte"
    };

    private double currentRate = 1.8;
    private boolean spikeActive = false;
    private double spikeTarget = 0;
    private double spikeStep = 0;

    private int consecutiveHigh = 0;

    @Override
    public SensorEvent processEvent(SensorEvent event) {
        long startTime = System.currentTimeMillis();

        log.debug("Procesando evento de movimiento en: {}", event.getLocation());

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
        if (value >= threshold) {
            consecutiveHigh++;
        } else if (consecutiveHigh > 0) {
            consecutiveHigh--;
        }
        return value >= highThreshold || consecutiveHigh >= 3;
    }

    @Override
    public String getSensorType() {
        return SensorType.MOTION.name();
    }

    @Override
    public SensorEvent simulateEvent() {
        if (!spikeActive && random.nextDouble() < 0.04) {
            spikeActive = true;
            spikeTarget = 8 + random.nextDouble() * 6;
            spikeStep = 0.8 + random.nextDouble() * 0.6;
        }

        if (spikeActive) {
            double next = currentRate + spikeStep;
            if (next >= spikeTarget) {
                currentRate = spikeTarget;
                if (random.nextDouble() < 0.25) {
                    spikeActive = false;
                }
            } else {
                currentRate = next;
            }
        } else {
            double drift = (2.0 - currentRate) * 0.12;
            double noise = (random.nextGaussian()) * 0.20;
            currentRate += drift + noise;
        }

        if (!spikeActive && currentRate > 2.0) {
            currentRate -= 0.2 + random.nextDouble() * 0.2;
        }

        currentRate = Math.max(0, Math.min(14, currentRate));
        double value = Math.round(currentRate);

        return SensorEvent.builder()
                .sensorType(SensorType.MOTION)
                .sensorId("MOTION-" + UUID.randomUUID().toString().substring(0, 8))
                .location(locations[random.nextInt(locations.length)])
                .value(value)
                .unit("detecciones/min")
                .description("Movimiento detectado por sensor infrarrojo")
                .timestamp(LocalDateTime.now())
                .critical(false)
                .build();
    }
}
