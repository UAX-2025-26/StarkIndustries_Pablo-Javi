package com.starkindustries.security.controller;

import com.starkindustries.security.service.SensorProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador WebSocket para comunicación bidireccional en tiempo real
 */
@Controller
@Slf4j
public class WebSocketController {

    private final SensorProcessingService sensorProcessingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ThreadPoolTaskExecutor sensorExecutor;

    @Autowired
    public WebSocketController(SensorProcessingService sensorProcessingService,
                               SimpMessagingTemplate messagingTemplate,
                               @Qualifier("sensorExecutor") ThreadPoolTaskExecutor sensorExecutor) {
        this.sensorProcessingService = sensorProcessingService;
        this.messagingTemplate = messagingTemplate;
        this.sensorExecutor = sensorExecutor;
    }

    @MessageMapping("/subscribe")
    @SendTo("/topic/alerts")
    public String subscribe(String message) {
        log.info("Cliente suscrito a alertas: {}", message);
        return "Suscripción exitosa al sistema de alertas";
    }

    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public String ping(String message) {
        return "pong: " + System.currentTimeMillis();
    }

    @MessageMapping("/stats/request")
    public void statsRequest(String payload) {
        try {
            Map<String, Object> snapshot = new HashMap<>();
            // Asegurar claves String en el payload para el frontend
            Map<String, Long> total = new HashMap<>();
            sensorProcessingService.getEventStatistics().forEach((k, v) -> total.put(k.name(), v));
            Map<String, Long> critical = new HashMap<>();
            sensorProcessingService.getCriticalEventStatistics().forEach((k, v) -> critical.put(k.name(), v));
            snapshot.put("totalEvents", total);
            snapshot.put("criticalEvents", critical);

        } catch (Exception e) {
            log.warn("No se pudo enviar snapshot solicitado: {}", e.getMessage());
        }
    }
}
