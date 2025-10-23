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

    // Estado para simulación realista
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
        // Inicialización perezosa del baseline y valor actual
        if (!initialized) {
            // Baseline centrado dentro del rango normal ligeramente hacia el centro
            baseline = (minTemp + maxTemp) / 2.0; // ~22.5 por defecto
            // Dispersión inicial pequeña
            currentTemp = baseline + boundedGaussian(0, 0.8, -1.5, 1.5);
            initialized = true;
        }

        // Activar anomalía rara vez (p ~ 0.3%) si no hay una activa
        if (!anomalyActive && random.nextDouble() < 0.003) {
            anomalyActive = true;
            boolean high = random.nextBoolean();
            // Objetivo fuera de rango, pero alcanzado gradualmente
            anomalyTarget = high ? (maxTemp + 15 + random.nextDouble() * 8) : (minTemp - 10 - random.nextDouble() * 6);
            // Paso pequeño por tick (~0.6-1.2°C)
            anomalyStep = (anomalyTarget > currentTemp ? 1 : -1) * (0.6 + random.nextDouble() * 0.6);
        }

        // Actualizar currentTemp: random walk con leve re-versión al baseline
        if (anomalyActive) {
            double next = currentTemp + anomalyStep;
            // Si sobrepasamos objetivo, mantenernos cerca y luego desactivar lentamente
            if ((anomalyStep > 0 && next >= anomalyTarget) || (anomalyStep < 0 && next <= anomalyTarget)) {
                currentTemp = anomalyTarget;
                // Empieza a volver gradualmente al baseline tras un breve plateau
                if (random.nextDouble() < 0.2) { // 20% de prob por tick de finalizar anomalía
                    anomalyActive = false;
                }
            } else {
                currentTemp = next;
            }
        } else {
            // Paso normal pequeño (-0.3..0.3) + ligera fuerza hacia el baseline
            double step = boundedGaussian(0, 0.18, -0.35, 0.35);
            double pull = (baseline - currentTemp) * 0.05; // 5% hacia baseline
            currentTemp += step + pull;
        }

        // Redondeo a 0.1°C y clamp razonable
        double temp = Math.round(currentTemp * 10.0) / 10.0;
        // Limitar a un rango físico amplio para evitar valores absurdos
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
