package com.starkindustries.security.service;

import com.starkindustries.security.model.AccessLog;
import com.starkindustries.security.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;

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

        if (!successful) {
            log.warn("Acceso fallido registrado: Usuario={}, IP={}, Razón={}",
                     username, ipAddress, failureReason);
        }

        return accessLog;
    }

    public List<AccessLog> getFailedAttempts() {
        return accessLogRepository.findBySuccessfulFalse();
    }

    public List<AccessLog> getAccessLogsByUser(String username) {
        return accessLogRepository.findByUsername(username);
    }

    public List<Object[]> getSuspiciousIpAddresses(int threshold) {
        return accessLogRepository.findSuspiciousIpAddresses(threshold);
    }
}
