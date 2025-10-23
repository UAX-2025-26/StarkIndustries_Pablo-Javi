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

    @Value("${security.sensor.motion.threshold.high:9}")
    private int highThreshold;

    private final Random random = new Random();
    private final String[] locations = {
        "Entrada Principal", "Laboratorio", "Bóveda",
        "Sala de Servidores", "Oficina Ejecutiva", "Pasillo Norte"
    };

    // Estado de simulación: tasa de detecciones por minuto
    private double currentRate = 1.8; // baseline un poco más alto
    private boolean spikeActive = false;
    private double spikeTarget = 0;
    private double spikeStep = 0;

    // Histeresis para alertas: contar excedencias consecutivas
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
        // Incrementar contador si supera el umbral estándar
        if (value >= threshold) {
            consecutiveHigh++;
        } else if (consecutiveHigh > 0) {
            consecutiveHigh--;
        }
        // Alertar si supera el umbral alto o si sostiene sobre el umbral estándar por 3 ticks
        return value >= highThreshold || consecutiveHigh >= 3;
    }

    @Override
    public String getSensorType() {
        return SensorType.MOTION.name();
    }

    @Override
    public SensorEvent simulateEvent() {
        // Iniciar pico un poco más a menudo
        if (!spikeActive && random.nextDouble() < 0.02) { // 2% de prob.
            spikeActive = true;
            // pico objetivo más alto
            spikeTarget = 8 + random.nextDouble() * 6; // 8..14
            // paso gradual más rápido
            spikeStep = 0.8 + random.nextDouble() * 0.6; // 0.8..1.4 por tick
        }

        if (spikeActive) {
            double next = currentRate + spikeStep;
            if (next >= spikeTarget) {
                currentRate = spikeTarget;
                // chance de finalizar pico y empezar a decaer
                if (random.nextDouble() < 0.25) {
                    spikeActive = false;
                }
            } else {
                currentRate = next;
            }
        } else {
            // random walk suave hacia valores algo más altos (objetivo ~2.0)
            double drift = (2.0 - currentRate) * 0.12; // tendencia a ~2.0
            double noise = (random.nextGaussian()) * 0.20; // ruido moderado
            currentRate += drift + noise;
        }

        // Decaimiento suave si venimos de pico y no está activo
        if (!spikeActive && currentRate > 2.0) {
            currentRate -= 0.2 + random.nextDouble() * 0.2; // decae menos para mantener valores algo altos
        }

        // Clamp y discretización ligera
        currentRate = Math.max(0, Math.min(14, currentRate));
        double value = Math.round(currentRate); // entero natural

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
