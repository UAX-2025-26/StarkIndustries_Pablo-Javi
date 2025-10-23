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

    @Value("${stark.sensors.simulation.events.min:1}")
    private int minEvents;

    @Value("${stark.sensors.simulation.events.max:3}")
    private int maxEvents;

    @Value("${stark.sensors.simulation.high-load.enabled:false}")
    private boolean highLoadEnabled;

    @Value("${stark.sensors.simulation.high-load.batch-size:15}")
    private int highLoadBatchSize;

    /**
     * Genera eventos de sensores cada 5 segundos
     */
    @Scheduled(fixedRate = 5000)
    public void simulateSensorEvents() {
        if (!simulationEnabled) {
            return;
        }

        int span = Math.max(1, (maxEvents - minEvents + 1));
        int eventCount = minEvents + random.nextInt(span);
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
        if (!simulationEnabled || !highLoadEnabled) {
            return;
        }

        log.info("🔥 Simulando alta carga - Generando {} eventos concurrentes", highLoadBatchSize);

        List<SensorEvent> events = new ArrayList<>();
        for (int i = 0; i < highLoadBatchSize; i++) {
            Sensor sensor = getRandomSensor();
            events.add(sensor.simulateEvent());
        }

        sensorProcessingService.processBatchAsync(events);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("✅ Sistema de simulación de sensores iniciado (enabled={})", simulationEnabled);
        if (simulationEnabled) {
            log.info("   - Eventos normales: cada 5 segundos ({}-{} eventos por ciclo)", minEvents, maxEvents);
            log.info("   - Ráfagas de alta carga: {} (batch: {})",
                    highLoadEnabled ? "habilitadas" : "deshabilitadas", highLoadBatchSize);
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
