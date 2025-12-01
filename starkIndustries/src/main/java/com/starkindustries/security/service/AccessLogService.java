package com.starkindustries.security.service;

import com.starkindustries.security.model.AccessLog;
import com.starkindustries.security.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// Servicio de dominio para registrar y consultar logs de acceso
@Service
@Slf4j
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;

    // Crea y persiste un nuevo registro de acceso (éxito o fallo)
    public AccessLog logAccess(
            String username,
            String ipAddress,
            AccessLog.AccessType accessType,
            boolean successful,
            String failureReason
    ) {
        AccessLog accessLog = AccessLog.builder()
                .username(username)
                .ipAddress(ipAddress)
                .accessType(accessType)
                .successful(successful)
                .failureReason(failureReason)
                .timestamp(LocalDateTime.now())
                .build();

        accessLog = accessLogRepository.save(accessLog);

        // Solo se hace log a nivel WARN cuando el acceso ha fallado
        if (!successful) {
            log.warn("Acceso fallido registrado: Usuario={}, IP={}, Razón={}",
                     username, ipAddress, failureReason);
        }

        return accessLog;
    }

    // Devuelve todos los accesos no exitosos
    public List<AccessLog> getFailedAttempts() {
        return accessLogRepository.findBySuccessfulFalse();
    }

    // Devuelve todos los accesos asociados a un usuario concreto
    public List<AccessLog> getAccessLogsByUser(String username) {
        return accessLogRepository.findByUsername(username);
    }

    // Devuelve IPs sospechosas cuyo número de fallos supera el umbral indicado
    public List<Object[]> getSuspiciousIpAddresses(int threshold) {
        return accessLogRepository.findSuspiciousIpAddresses(threshold);
    }
}
