package com.starkindustries.security.sensor;

import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.model.SensorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

// Control de acceso
@Component("accessSensor")
@Slf4j
public class AccessControlSensor implements Sensor {

    @Value("${security.sensor.access.max-failed-attempts:3}")
    private int maxFailedAttempts;

    private final Random random = new Random();
    private final String[] locations = {
        "Puerta Principal", "Bóveda Segura", "Laboratorio Nivel 5",
        "Reactor Arc - Acceso Restringido", "Sala de Control Central", "Armería"
    };

    private boolean attackBurstActive = false;
    private int burstTicksRemaining = 0;

    @Override
    public SensorEvent processEvent(SensorEvent event) {
        long startTime = System.currentTimeMillis();

        log.debug("Procesando intento de acceso en: {}", event.getLocation());

        try {
            Thread.sleep(random.nextInt(120) + 60);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        event.setProcessedAt(LocalDateTime.now());
        event.setProcessedBy(Thread.currentThread().getName());
        event.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        event.setCritical(requiresAlert(event.getValue()));

        if (event.getValue() == 0) {
            log.info("Acceso concedido en: {}", event.getLocation());
        } else if (event.getCritical()) {
            log.error("¡INTRUSIÓN DETECTADA! {} intentos fallidos en {}",
                     event.getValue().intValue(), event.getLocation());
        } else {
            log.warn("Intento de acceso fallido en: {}", event.getLocation());
        }

        return event;
    }

    @Override
    public boolean requiresAlert(Double value) {
        return value >= maxFailedAttempts;
    }

    @Override
    public String getSensorType() {
        return SensorType.ACCESS.name();
    }

    @Override
    public SensorEvent simulateEvent() {
        if (!attackBurstActive && random.nextDouble() < 0.012) {
            attackBurstActive = true;
            burstTicksRemaining = 2 + random.nextInt(4);
        }

        int failedAttempts;
        if (attackBurstActive) {
            failedAttempts = 2 + random.nextInt(5);
            burstTicksRemaining--;
            if (burstTicksRemaining <= 0 || random.nextDouble() < 0.2) {
                attackBurstActive = false;
            }
        } else {
            if (random.nextDouble() < 0.90) {
                failedAttempts = 0;
            } else {
                failedAttempts = 1 + random.nextInt(2);
            }
        }

        String description = failedAttempts == 0
            ? "Acceso autorizado - Credenciales válidas"
            : "Acceso denegado - Credenciales inválidas (Intentos: " + failedAttempts + ")";

        return SensorEvent.builder()
                .sensorType(SensorType.ACCESS)
                .sensorId("ACCESS-" + UUID.randomUUID().toString().substring(0, 8))
                .location(locations[random.nextInt(locations.length)])
                .value((double) failedAttempts)
                .unit("intentos")
                .description(description)
                .timestamp(LocalDateTime.now())
                .critical(false)
                .build();
    }
}
