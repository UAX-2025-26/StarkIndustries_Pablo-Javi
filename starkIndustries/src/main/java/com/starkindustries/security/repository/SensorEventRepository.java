package com.starkindustries.security.repository;

import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.model.SensorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// Repositorio de eventos de sensores
@Repository
public interface SensorEventRepository extends JpaRepository<SensorEvent, Long> {

    List<SensorEvent> findBySensorType(SensorType sensorType);

    List<SensorEvent> findBySensorId(String sensorId);

    List<SensorEvent> findByCriticalTrue();

    List<SensorEvent> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT se FROM SensorEvent se WHERE se.sensorType = :type AND se.timestamp >= :since")
    List<SensorEvent> findRecentBySensorType(SensorType type, LocalDateTime since);

    @Query("SELECT se.sensorType, COUNT(se) FROM SensorEvent se GROUP BY se.sensorType")
    List<Object[]> countEventsBySensorType();

    @Query("SELECT AVG(se.processingTimeMs) FROM SensorEvent se WHERE se.sensorType = :type")
    Double getAverageProcessingTime(SensorType type);
}
