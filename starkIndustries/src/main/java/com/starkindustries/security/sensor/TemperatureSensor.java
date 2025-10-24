package com.starkindustries.security.sensor;

import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.model.SensorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

// Sensor de temperatura
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

    private boolean initialized = false;
    private double currentTemp;
    private double baseline;
    private boolean anomalyActive = false;
    private double anomalyTarget = 0;
    private double anomalyStep = 0;

    @Override
    public SensorEvent processEvent(SensorEvent event) {
        long startTime = System.currentTimeMillis();

        log.debug("Procesando evento de temperatura en: {}", event.getLocation());

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
        if (!initialized) {
            baseline = (minTemp + maxTemp) / 2.0;
            currentTemp = baseline + boundedGaussian(0, 0.8, -1.5, 1.5);
            initialized = true;
        }

        if (!anomalyActive && random.nextDouble() < 0.01) {
            anomalyActive = true;
            boolean high = random.nextBoolean();
            anomalyTarget = high ? (maxTemp + 15 + random.nextDouble() * 8) : (minTemp - 10 - random.nextDouble() * 6);
            anomalyStep = (anomalyTarget > currentTemp ? 1 : -1) * (0.6 + random.nextDouble() * 0.6);
        }

        if (anomalyActive) {
            double next = currentTemp + anomalyStep;
            if ((anomalyStep > 0 && next >= anomalyTarget) || (anomalyStep < 0 && next <= anomalyTarget)) {
                currentTemp = anomalyTarget;
                if (random.nextDouble() < 0.2) {
                    anomalyActive = false;
                }
            } else {
                currentTemp = next;
            }
        } else {
            double step = boundedGaussian(0, 0.18, -0.35, 0.35);
            double pull = (baseline - currentTemp) * 0.05;
            currentTemp += step + pull;
        }

        double temp = Math.round(currentTemp * 10.0) / 10.0;
        temp = Math.max(-20, Math.min(80, temp));
        currentTemp = temp;

        return SensorEvent.builder()
                .sensorType(SensorType.TEMPERATURE)
                .sensorId("TEMP-" + UUID.randomUUID().toString().substring(0, 8))
                .location(locations[random.nextInt(locations.length)])
                .value(temp)
                .unit("°C")
                .description("Lectura de temperatura ambiente")
                .timestamp(LocalDateTime.now())
                .critical(false)
                .build();
    }

    private double boundedGaussian(double mean, double stdDev, double min, double max) {
        double val = mean + random.nextGaussian() * stdDev;
        if (val < min) val = min + (min - val) * 0.1;
        if (val > max) val = max - (val - max) * 0.1;
        return val;
    }
}
