package com.starkindustries.security.repository;

import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.model.SensorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// Repositorio de eventos de sensores: permite consultar por tipo, rango temporal y estadísticas
@Repository // Marca esta interfaz como un repositorio de Spring Data JPA, permitiendo que Spring genere automáticamente la implementación de acceso a datos
public interface SensorEventRepository extends JpaRepository<SensorEvent, Long> {

    // Todos los eventos de un tipo de sensor concreto
    List<SensorEvent> findBySensorType(SensorType sensorType);

    // Todos los eventos generados por un sensor concreto
    List<SensorEvent> findBySensorId(String sensorId);

    // Solo eventos marcados como críticos
    List<SensorEvent> findByCriticalTrue();

    // Eventos dentro de un intervalo de tiempo
    List<SensorEvent> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    // Eventos recientes de un tipo, a partir de una fecha dada
    @Query("SELECT se FROM SensorEvent se WHERE se.sensorType = :type AND se.timestamp >= :since")
    List<SensorEvent> findRecentBySensorType(SensorType type, LocalDateTime since);

    // Devuelve por cada tipo de sensor cuántos eventos hay almacenados
    @Query("SELECT se.sensorType, COUNT(se) FROM SensorEvent se GROUP BY se.sensorType")
    List<Object[]> countEventsBySensorType();

    // Tiempo medio de procesamiento (ms) para un tipo de sensor
    @Query("SELECT AVG(se.processingTimeMs) FROM SensorEvent se WHERE se.sensorType = :type")
    Double getAverageProcessingTime(SensorType type);
}
