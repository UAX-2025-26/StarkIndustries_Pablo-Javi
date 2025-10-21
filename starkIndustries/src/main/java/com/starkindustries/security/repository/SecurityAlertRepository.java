package com.starkindustries.security.repository;

import com.starkindustries.security.model.SecurityAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio para gestionar alertas de seguridad
 */
@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {

    List<SecurityAlert> findByResolvedFalse();

    List<SecurityAlert> findByLevel(SecurityAlert.AlertLevel level);

    List<SecurityAlert> findByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT COUNT(sa) FROM SecurityAlert sa WHERE sa.resolved = false AND sa.level = :level")
    Long countUnresolvedByLevel(SecurityAlert.AlertLevel level);

    @Query("SELECT sa FROM SecurityAlert sa WHERE sa.resolved = false ORDER BY sa.level DESC, sa.createdAt DESC")
    List<SecurityAlert> findActiveAlertsPrioritized();
}

