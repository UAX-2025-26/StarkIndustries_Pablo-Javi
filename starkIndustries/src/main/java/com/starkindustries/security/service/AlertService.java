package com.starkindustries.security.service;

import com.starkindustries.security.model.SecurityAlert;
import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.repository.SecurityAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// Servicio encargado de crear y gestionar alertas de seguridad a partir de eventos críticos
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private final SecurityAlertRepository alertRepository;
    private final NotificationService notificationService;

    // Mapa para aplicar un "cooldown" por sensor/ubicación y evitar alertas duplicadas
    private final Map<String, Long> lastAlertByKey = new ConcurrentHashMap<>();

    @Value("${security.alerts.cooldown-ms:120000}")
    private long alertsCooldownMs;

    // Crea una alerta asíncrona a partir de un evento de sensor crítico
    @Async("alertExecutor")
    public CompletableFuture<SecurityAlert> createAlertFromEvent(SensorEvent event) {
        String key = event.getSensorType().name() + "@" + String.valueOf(event.getLocation());
        long now = System.currentTimeMillis();
        Long last = lastAlertByKey.get(key);
        // Si la última alerta para esa clave es reciente, se omite para evitar spam
        if (last != null && (now - last) < alertsCooldownMs) {
            log.info("Rate limit: omitiendo alerta repetida para {} ({} ms restantes)", key, alertsCooldownMs - (now - last));
            return CompletableFuture.completedFuture(null);
        }
        lastAlertByKey.put(key, now);

        log.warn("Creando alerta de seguridad para evento crítico: {} en {}",
                 event.getSensorType(), event.getLocation());

        // Determina el nivel de la alerta a partir del tipo y valor del evento
        SecurityAlert.AlertLevel level = determineAlertLevel(event);

        SecurityAlert alert = SecurityAlert.builder()
                .level(level)
                .title(generateAlertTitle(event))
                .message(generateAlertMessage(event))
                .relatedSensorType(event.getSensorType())
                .sensorId(event.getSensorId())
                .location(event.getLocation())
                .createdAt(LocalDateTime.now())
                .resolved(false)
                .build();

        // Persiste la alerta antes de notificarla
        alert = alertRepository.save(alert);

        // Lanza las notificaciones asociadas (WS, email, etc.)
        notificationService.sendAlertNotifications(alert);

        log.info("Alerta creada: ID={}, Nivel={}", alert.getId(), alert.getLevel());

        return CompletableFuture.completedFuture(alert);
    }

    // Devuelve las alertas aún no resueltas
    public List<SecurityAlert> getActiveAlerts() {
        return alertRepository.findByResolvedFalse();
    }

    // Devuelve las alertas activas priorizadas por criticidad y fecha
    public List<SecurityAlert> getAlertsPrioritized() {
        return alertRepository.findActiveAlertsPrioritized();
    }

    // Marca una alerta como reconocida por un usuario concreto
    public SecurityAlert acknowledgeAlert(Long alertId, String username) {
        SecurityAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alerta no encontrada"));

        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedBy(username);

        log.info("Alerta {} reconocida por {}", alertId, username);

        return alertRepository.save(alert);
    }

    // Marca una alerta como resuelta y, si no estaba reconocida, la reconoce también
    public SecurityAlert resolveAlert(Long alertId, String username) {
        SecurityAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alerta no encontrada"));

        alert.setResolved(true);
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(LocalDateTime.now());
            alert.setAcknowledgedBy(username);
        }

        log.info("Alerta {} resuelta por {}", alertId, username);

        return alertRepository.save(alert);
    }

    // Regla de negocio para traducir un evento de sensor a un nivel de alerta
    private SecurityAlert.AlertLevel determineAlertLevel(SensorEvent event) {
        return switch (event.getSensorType()) {
            case ACCESS -> event.getValue() >= 5 ?
                    SecurityAlert.AlertLevel.CRITICAL : SecurityAlert.AlertLevel.HIGH;
            case TEMPERATURE -> event.getValue() > 50 || event.getValue() < 10 ?
                    SecurityAlert.AlertLevel.CRITICAL : SecurityAlert.AlertLevel.MEDIUM;
            case MOTION -> event.getValue() >= 10 ?
                    SecurityAlert.AlertLevel.HIGH : SecurityAlert.AlertLevel.MEDIUM;
        };
    }

    // Título corto según el tipo de sensor que generó la alerta
    private String generateAlertTitle(SensorEvent event) {
        return switch (event.getSensorType()) {
            case ACCESS -> "INTRUSIÓN DETECTADA";
            case TEMPERATURE -> "TEMPERATURA CRÍTICA";
            case MOTION -> "MOVIMIENTO SOSPECHOSO";
        };
    }

    // Mensaje descriptivo a partir del contenido del evento
    private String generateAlertMessage(SensorEvent event) {
        return String.format(
                "Alerta de %s detectada en %s. Valor: %.2f %s. Sensor ID: %s. Timestamp: %s",
                event.getSensorType().getDescription(),
                event.getLocation(),
                event.getValue(),
                event.getUnit(),
                event.getSensorId(),
                event.getTimestamp()
        );
    }
}
