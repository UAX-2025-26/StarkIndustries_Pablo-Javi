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

// Servicio encargado de generar eventos de sensores simulados para pruebas y demo
@Service // Marca esta clase como un componente de servicio de Spring para que sea detectado automáticamente y registrado en el contexto de Spring
@Slf4j // Anotación de Lombok que genera automáticamente un logger (log) para esta clase
@RequiredArgsConstructor // Anotación de Lombok que genera un constructor con todos los campos final, permitiendo inyección de dependencias por constructor
public class SensorSimulationService {

    private final SensorProcessingService sensorProcessingService;
    // Mapa de sensores disponibles inyectado por Spring (clave = beanName)
    private final Map<String, Sensor> sensors;
    private final Random random = new Random();

    // Flag para activar/desactivar la simulación (leído de configuración)
    @Value("${stark.sensors.simulation.enabled:false}") // Inyecta el valor de la propiedad de configuración, con valor por defecto "false" si no está definida
    private boolean simulationEnabled;

    // Número mínimo de eventos por ciclo de simulación
    @Value("${stark.sensors.simulation.events.min:1}") // Inyecta el valor de la propiedad de configuración, con valor por defecto "1" si no está definida
    private int minEvents;

    // Número máximo de eventos por ciclo de simulación
    @Value("${stark.sensors.simulation.events.max:3}") // Inyecta el valor de la propiedad de configuración, con valor por defecto "3" si no está definida
    private int maxEvents;

    // Flag para activar ráfagas de alta carga
    @Value("${stark.sensors.simulation.high-load.enabled:false}") // Inyecta el valor de la propiedad de configuración, con valor por defecto "false" si no está definida
    private boolean highLoadEnabled;

    // Tamaño del lote en modo alta carga
    @Value("${stark.sensors.simulation.high-load.batch-size:15}") // Inyecta el valor de la propiedad de configuración, con valor por defecto "15" si no está definida
    private int highLoadBatchSize;

    // Genera eventos periódicos en base a la configuración (normal load)
    @Scheduled(fixedRate = 5000) // Define que este método se ejecutará automáticamente cada 5000ms (5 segundos)
    public void simulateSensorEvents() {
        if (!simulationEnabled) {
            return; // si la simulación está desactivada, no hace nada
        }

        int span = Math.max(1, (maxEvents - minEvents + 1));
        int eventCount = minEvents + random.nextInt(span);
        List<SensorEvent> events = new ArrayList<>();

        log.debug("Generando {} eventos de sensores simultáneos", eventCount);

        // Genera eventos llamando a simulateEvent() de sensores aleatorios
        for (int i = 0; i < eventCount; i++) {
            Sensor sensor = getRandomSensor();
            SensorEvent event = sensor.simulateEvent();
            events.add(event);
        }

        // Envía el lote para procesamiento concurrente
        sensorProcessingService.processBatchAsync(events);
    }

    // Simula ráfagas de alta carga cada 30 segundos
    @Scheduled(fixedRate = 30000) // Cada 30 segundos
    public void simulateHighLoad() {
        if (!simulationEnabled || !highLoadEnabled) {
            return;
        }

        log.info("Simulando alta carga - Generando {} eventos concurrentes", highLoadBatchSize);

        List<SensorEvent> events = new ArrayList<>();
        for (int i = 0; i < highLoadBatchSize; i++) {
            Sensor sensor = getRandomSensor();
            events.add(sensor.simulateEvent());
        }

        sensorProcessingService.processBatchAsync(events);
    }

    // Log inicial cuando la aplicación está lista, mostrando la configuración de simulación
    @EventListener(ApplicationReadyEvent.class) // Indica que este método se ejecutará automáticamente cuando Spring termine de inicializar la aplicación, respondiendo al evento ApplicationReadyEvent
    public void onApplicationReady() {
        log.info("Sistema de simulación de sensores iniciado (enabled={})", simulationEnabled);
        if (simulationEnabled) {
            log.info("   - Eventos normales: cada 5 segundos ({}-{} eventos por ciclo)", minEvents, maxEvents);
            log.info("   - Ráfagas de alta carga: {} (batch: {})",
                    highLoadEnabled ? "habilitadas" : "deshabilitadas", highLoadBatchSize);
        } else {
            log.info("   - Simulación deshabilitada por configuración");
        }
    }

    // Selecciona un sensor aleatorio del mapa de sensores
    private Sensor getRandomSensor() {
        List<Sensor> sensorList = new ArrayList<>(sensors.values());
        return sensorList.get(random.nextInt(sensorList.size()));
    }

    // Métodos públicos para activar/desactivar simulación en tiempo de ejecución
    public void enableSimulation() {
        this.simulationEnabled = true;
        log.info("Simulación de sensores habilitada");
    }

    public void disableSimulation() {
        this.simulationEnabled = false;
        log.info("Simulación de sensores deshabilitada");
    }
}
