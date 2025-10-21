package com.starkindustries.security.service;

import com.starkindustries.security.model.SecurityAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Servicio de notificaciones en tiempo real (WebSocket y Email)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final JavaMailSender mailSender;

    /**
     * Envía notificaciones por múltiples canales de forma asíncrona
     */
    @Async("notificationExecutor")
    public void sendAlertNotifications(SecurityAlert alert) {
        log.info("Enviando notificaciones para alerta: {}", alert.getId());

        // WebSocket - Notificación en tiempo real
        sendWebSocketNotification(alert);

        // Email - Para alertas críticas
        if (alert.getLevel() == SecurityAlert.AlertLevel.CRITICAL ||
            alert.getLevel() == SecurityAlert.AlertLevel.HIGH) {
            sendEmailNotification(alert);
        }

        // Simular notificación push a móviles
        sendMobileNotification(alert);
    }

    private void sendWebSocketNotification(SecurityAlert alert) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", alert.getId());
            notification.put("level", alert.getLevel());
            notification.put("title", alert.getTitle());
            notification.put("message", alert.getMessage());
            notification.put("location", alert.getLocation());
            notification.put("timestamp", alert.getCreatedAt());

            // Enviar a todos los suscriptores
            messagingTemplate.convertAndSend("/topic/alerts", notification);

            // Enviar a canal específico por nivel
            messagingTemplate.convertAndSend("/topic/alerts/" + alert.getLevel(), notification);

            log.info("Notificación WebSocket enviada: Alerta {}", alert.getId());
        } catch (Exception e) {
            log.error("Error enviando notificación WebSocket", e);
        }
    }

    private void sendEmailNotification(SecurityAlert alert) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("security@starkindustries.com");
            message.setTo("admin@starkindustries.com", "security-team@starkindustries.com");
            message.setSubject("[STARK SECURITY] " + alert.getTitle());
            message.setText(String.format("""
                SISTEMA DE SEGURIDAD STARK INDUSTRIES
                =====================================

                Nivel: %s
                Ubicación: %s
                Mensaje: %s
                Sensor: %s (%s)
                Fecha/Hora: %s

                Por favor, tome acción inmediata.

                --
                Sistema Automatizado de Seguridad
                Stark Industries
                """,
                alert.getLevel(),
                alert.getLocation(),
                alert.getMessage(),
                alert.getRelatedSensorType(),
                alert.getSensorId(),
                alert.getCreatedAt()
            ));

            mailSender.send(message);
            log.info("Email de alerta enviado para: Alerta {}", alert.getId());
        } catch (Exception e) {
            log.error("Error enviando email de alerta", e);
        }
    }

    private void sendMobileNotification(SecurityAlert alert) {
        // Simulación de notificación push (placeholder)
        log.info("📱 Notificación PUSH simulada enviada: {} - {}",
                 alert.getTitle(), alert.getLocation());
    }

    /**
     * Envía evento genérico por WebSocket
     */
    public void sendEventNotification(String topic, Object payload) {
        try {
            messagingTemplate.convertAndSend(topic, payload);
            log.debug("Evento enviado a WebSocket: {}", topic);
        } catch (Exception e) {
            log.error("Error enviando evento WebSocket", e);
        }
    }
}
