package com.starkindustries.security.repository;

import com.starkindustries.security.model.SecurityAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// Repositorio de alertas de seguridad
@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {

    // Alertas no resueltas (activas)
    List<SecurityAlert> findByResolvedFalse();

    // Alertas por nivel concreto
    List<SecurityAlert> findByLevel(SecurityAlert.AlertLevel level);

    // Alertas creadas después de un instante dado
    List<SecurityAlert> findByCreatedAtAfter(LocalDateTime since);

    // Número de alertas activas por nivel (útil para estadísticas)
    @Query("SELECT COUNT(sa) FROM SecurityAlert sa WHERE sa.resolved = false AND sa.level = :level")
    Long countUnresolvedByLevel(SecurityAlert.AlertLevel level);

    // Alertas activas ordenadas por criticidad y fecha (para priorizar la atención)
    @Query("SELECT sa FROM SecurityAlert sa WHERE sa.resolved = false ORDER BY sa.level DESC, sa.createdAt DESC")
    List<SecurityAlert> findActiveAlertsPrioritized();
}
