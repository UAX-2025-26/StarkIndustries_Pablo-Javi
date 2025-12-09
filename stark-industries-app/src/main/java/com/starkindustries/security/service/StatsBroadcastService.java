package com.starkindustries.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

// Servicio que construye y envía periódicamente un snapshot de estadísticas por WebSocket
@Service
@Slf4j
public class StatsBroadcastService {

    private final SensorProcessingService sensorProcessingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ThreadPoolTaskExecutor sensorExecutor;

    @Autowired
    public StatsBroadcastService(
            SensorProcessingService sensorProcessingService,
            SimpMessagingTemplate messagingTemplate,
            @Qualifier("sensorExecutor") ThreadPoolTaskExecutor sensorExecutor
    ) {
        this.sensorProcessingService = sensorProcessingService;
        this.messagingTemplate = messagingTemplate;
        this.sensorExecutor = sensorExecutor;
    }

    // Tarea programada: cada 5 segundos envía un snapshot de métricas al topic /topic/stats
    @Scheduled(fixedRate = 5000, initialDelay = 5000) // Define que este método se ejecutará automáticamente cada 5000ms (5 segundos) con un retraso inicial de 5 segundos
    public void broadcastStats() {
        try {
            Map<String, Object> snapshot = new HashMap<>();
            Map<String, Long> total = new HashMap<>();
            sensorProcessingService.getEventStatistics().forEach((k, v) -> total.put(k.name(), v));
            Map<String, Long> critical = new HashMap<>();
            sensorProcessingService.getCriticalEventStatistics().forEach((k, v) -> critical.put(k.name(), v));
            snapshot.put("totalEvents", total);
            snapshot.put("criticalEvents", critical);

            // Añade información del estado actual del pool de hilos de sensores
            try {
                Map<String, Object> threadPool = new HashMap<>();
                threadPool.put("active", sensorExecutor.getActiveCount());
                threadPool.put("poolSize", sensorExecutor.getPoolSize());
                threadPool.put("corePoolSize", sensorExecutor.getCorePoolSize());
                threadPool.put("maxPoolSize", sensorExecutor.getMaxPoolSize());
                snapshot.put("activeThreads", sensorExecutor.getActiveCount());
                snapshot.put("threadPool", threadPool);
            } catch (Exception e) {
                log.trace("No se pudieron obtener métricas de hilos: {}", e.getMessage());
            }

            // Envía el snapshot a todos los clientes suscritos al topic
            messagingTemplate.convertAndSend("/topic/stats", snapshot);
        } catch (Exception e) {
            log.debug("No se pudo publicar snapshot de estadísticas: {}", e.getMessage());
        }
    }
}
