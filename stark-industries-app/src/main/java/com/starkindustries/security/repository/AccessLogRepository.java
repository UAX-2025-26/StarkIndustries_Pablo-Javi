package com.starkindustries.security.repository;

import com.starkindustries.security.model.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// Repositorio de logs de acceso
@Repository // Marca esta interfaz como un repositorio de Spring Data JPA, permitiendo que Spring genere automáticamente la implementación de acceso a datos
public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    // Todos los logs de un usuario concreto
    List<AccessLog> findByUsername(String username);

    // Solo accesos fallidos
    List<AccessLog> findBySuccessfulFalse();

    // Accesos agrupados por una IP concreta
    List<AccessLog> findByIpAddress(String ipAddress);

    // Logs posteriores a un instante dado
    List<AccessLog> findByTimestampAfter(LocalDateTime since);

    // Número de intentos fallidos de un usuario desde una fecha determinada
    @Query("SELECT COUNT(al) FROM AccessLog al WHERE al.username = :username AND al.successful = false AND al.timestamp >= :since")
    Long countFailedAttemptsSince(String username, LocalDateTime since);

    // IPs con más fallos que el umbral indicado (para detectar IPs sospechosas)
    @Query("SELECT al.ipAddress, COUNT(al) FROM AccessLog al WHERE al.successful = false GROUP BY al.ipAddress HAVING COUNT(al) > :threshold")
    List<Object[]> findSuspiciousIpAddresses(int threshold);
}
