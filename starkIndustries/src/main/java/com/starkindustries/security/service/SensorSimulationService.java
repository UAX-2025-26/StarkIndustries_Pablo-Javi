package com.starkindustries.security.service;

import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.sensor.Sensor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Servicio de simulación de eventos de sensores
 * Genera eventos concurrentes para demostrar la capacidad del sistema
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SensorSimulationService {

    private final SensorProcessingService sensorProcessingService;
    private final Map<String, Sensor> sensors;
    private final Random random = new Random();

    @Value("${stark.sensors.simulation.enabled:false}")
    private boolean simulationEnabled;

    /**
     * Genera eventos de sensores cada 5 segundos
     */
    @Scheduled(fixedRate = 5000)
    public void simulateSensorEvents() {
        if (!simulationEnabled) {
            return;
        }

        // Generar entre 3 y 8 eventos simultáneos
        int eventCount = random.nextInt(6) + 3;
        List<SensorEvent> events = new ArrayList<>();

        log.debug("Generando {} eventos de sensores simultáneos", eventCount);

        for (int i = 0; i < eventCount; i++) {
            // Seleccionar sensor aleatorio
            Sensor sensor = getRandomSensor();
            SensorEvent event = sensor.simulateEvent();
            events.add(event);
        }

        // Procesar eventos concurrentemente
        sensorProcessingService.processBatchAsync(events);
    }

    /**
     * Simula una ráfaga de eventos para probar alta concurrencia
     */
    @Scheduled(fixedRate = 30000) // Cada 30 segundos
    public void simulateHighLoad() {
        if (!simulationEnabled) {
            return;
        }

        log.info("🔥 Simulando alta carga - Generando 50 eventos concurrentes");

        List<SensorEvent> events = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Sensor sensor = getRandomSensor();
            events.add(sensor.simulateEvent());
        }

        sensorProcessingService.processBatchAsync(events);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("✅ Sistema de simulación de sensores iniciado (enabled={})", simulationEnabled);
        if (simulationEnabled) {
            log.info("   - Eventos normales: cada 5 segundos");
            log.info("   - Ráfagas de alta carga: cada 30 segundos");
        } else {
            log.info("   - Simulación deshabilitada por configuración");
        }
    }

    private Sensor getRandomSensor() {
        List<Sensor> sensorList = new ArrayList<>(sensors.values());
        return sensorList.get(random.nextInt(sensorList.size()));
    }

    public void enableSimulation() {
        this.simulationEnabled = true;
        log.info("Simulación de sensores habilitada");
    }

    public void disableSimulation() {
        this.simulationEnabled = false;
        log.info("Simulación de sensores deshabilitada");
    }
}
