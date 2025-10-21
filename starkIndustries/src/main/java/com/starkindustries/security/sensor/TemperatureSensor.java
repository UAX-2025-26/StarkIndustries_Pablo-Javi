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
 * Bean gestionado por Spring para sensor de temperatura
 * Monitorea condiciones térmicas críticas (incendios, fallos de refrigeración)
 */
@Component("temperatureSensor")
@Slf4j
public class TemperatureSensor implements Sensor {

    @Value("${security.sensor.temperature.min:15.0}")
    private double minTemp;

    @Value("${security.sensor.temperature.max:30.0}")
    private double maxTemp;

    private final Random random = new Random();
    private final String[] locations = {
        "Sala de Servidores", "Laboratorio Químico", "Reactor Arc",
        "Almacén", "Centro de Datos", "Sala de Control"
    };

    @Override
    public SensorEvent processEvent(SensorEvent event) {
        long startTime = System.currentTimeMillis();

        log.debug("Procesando evento de temperatura en: {}", event.getLocation());

        // Simular procesamiento (cálculo de tendencias, predicción, etc.)
        try {
            Thread.sleep(random.nextInt(80) + 40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        event.setProcessedAt(LocalDateTime.now());
        event.setProcessedBy(Thread.currentThread().getName());
        event.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        event.setCritical(requiresAlert(event.getValue()));

        if (event.getCritical()) {
            log.warn("¡ALERTA! Temperatura crítica detectada: {}°C en {}",
                     event.getValue(), event.getLocation());
        } else {
            log.info("Temperatura normal: {}°C en {}", event.getValue(), event.getLocation());
        }

        return event;
    }

    @Override
    public boolean requiresAlert(Double value) {
        return value < minTemp || value > maxTemp;
    }

    @Override
    public String getSensorType() {
        return SensorType.TEMPERATURE.name();
    }

    @Override
    public SensorEvent simulateEvent() {
        // Ocasionalmente generar temperatura crítica
        double temp = random.nextDouble() * 50 + 10; // 10-60°C

        return SensorEvent.builder()
                .sensorType(SensorType.TEMPERATURE)
                .sensorId("TEMP-" + UUID.randomUUID().toString().substring(0, 8))
                .location(locations[random.nextInt(locations.length)])
                .value(Math.round(temp * 10.0) / 10.0)
                .unit("°C")
                .description("Lectura de temperatura ambiente")
                .timestamp(LocalDateTime.now())
                .critical(false)
                .build();
    }
}

