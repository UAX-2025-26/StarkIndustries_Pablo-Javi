package com.starkindustries.security.sensor;

import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.model.SensorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

// Implementación de un sensor de temperatura simulado
@Component("temperatureSensor")
@Slf4j
public class TemperatureSensor implements Sensor {

    // Umbrales configurables desde application.yml
    @Value("${security.sensor.temperature.min:15.0}")
    private double minTemp;

    @Value("${security.sensor.temperature.max:30.0}")
    private double maxTemp;

    private final Random random = new Random();
    // Posibles ubicaciones del sensor
    private final String[] locations = {
        "Sala de Servidores", "Laboratorio Químico", "Reactor Arc",
        "Almacén", "Centro de Datos", "Sala de Control"
    };

    // Estado interno para simular una serie de tiempo realista
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

        // Simula un pequeño retardo de procesamiento
        try {
            Thread.sleep(random.nextInt(80) + 40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Rellena metadatos de procesamiento
        event.setProcessedAt(LocalDateTime.now());
        event.setProcessedBy(Thread.currentThread().getName());
        event.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        // Marca si el valor actual está fuera de los umbrales permitidos
        event.setCritical(requiresAlert(event.getValue()));

        if (event.getCritical()) {
            log.warn("¡ALERTA! Temperatura crítica detectada: {}°C en {}",
                     event.getValue(), event.getLocation());
        } else {
            log.info("Temperatura normal: {}°C en {}", event.getValue(), event.getLocation());
        }

        return event;
    }

    // Regla simple: alerta si está por debajo de min o por encima de max
    @Override
    public boolean requiresAlert(Double value) {
        return value < minTemp || value > maxTemp;
    }

    @Override
    public String getSensorType() {
        return SensorType.TEMPERATURE.name();
    }

    // Genera un nuevo evento simulado de temperatura
    @Override
    public SensorEvent simulateEvent() {
        // Inicialización perezosa de valores base
        if (!initialized) {
            baseline = (minTemp + maxTemp) / 2.0;
            currentTemp = baseline + boundedGaussian(0, 0.8, -1.5, 1.5);
            initialized = true;
        }

        // Ocasionalmente inicia una anomalía (subida/bajada brusca)
        if (!anomalyActive && random.nextDouble() < 0.01) {
            anomalyActive = true;
            boolean high = random.nextBoolean();
            anomalyTarget = high ? (maxTemp + 15 + random.nextDouble() * 8) : (minTemp - 10 - random.nextDouble() * 6);
            anomalyStep = (anomalyTarget > currentTemp ? 1 : -1) * (0.6 + random.nextDouble() * 0.6);
        }

        // Si hay anomalía activa, avanza hacia el valor objetivo
        if (anomalyActive) {
            double next = currentTemp + anomalyStep;
            if ((anomalyStep > 0 && next >= anomalyTarget) || (anomalyStep < 0 && next <= anomalyTarget)) {
                currentTemp = anomalyTarget;
                // Probabilidad de que la anomalía termine
                if (random.nextDouble() < 0.2) {
                    anomalyActive = false;
                }
            } else {
                currentTemp = next;
            }
        } else {
            // Sin anomalía: fluctuaciones suaves alrededor de la línea base
            double step = boundedGaussian(0, 0.18, -0.35, 0.35);
            double pull = (baseline - currentTemp) * 0.05;
            currentTemp += step + pull;
        }

        // Redondeo y límites razonables de temperatura
        double temp = Math.round(currentTemp * 10.0) / 10.0;
        temp = Math.max(-20, Math.min(80, temp));
        currentTemp = temp;

        // Construye el evento de sensor con los datos generados
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

    // Genera un valor aleatorio con distribución gaussiana pero acotado a un rango
    private double boundedGaussian(double mean, double stdDev, double min, double max) {
        double val = mean + random.nextGaussian() * stdDev;
        if (val < min) val = min + (min - val) * 0.1;
        if (val > max) val = max - (val - max) * 0.1;
        return val;
    }
}
