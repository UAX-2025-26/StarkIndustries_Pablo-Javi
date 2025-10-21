package com.starkindustries.security.repository;

import com.starkindustries.security.model.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio para gestionar logs de acceso
 */
@Repository
public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    List<AccessLog> findByUsername(String username);

    List<AccessLog> findBySuccessfulFalse();

    List<AccessLog> findByIpAddress(String ipAddress);

    List<AccessLog> findByTimestampAfter(LocalDateTime since);

    @Query("SELECT COUNT(al) FROM AccessLog al WHERE al.username = :username AND al.successful = false AND al.timestamp >= :since")
    Long countFailedAttemptsSince(String username, LocalDateTime since);

    @Query("SELECT al.ipAddress, COUNT(al) FROM AccessLog al WHERE al.successful = false GROUP BY al.ipAddress HAVING COUNT(al) > :threshold")
    List<Object[]> findSuspiciousIpAddresses(int threshold);
}
