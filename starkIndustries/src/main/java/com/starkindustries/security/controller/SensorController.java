package com.starkindustries.security.controller;

import com.starkindustries.security.repository.SensorEventRepository;
import com.starkindustries.security.service.SensorProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Controlador REST para gestión de sensores y eventos
 */
@RestController
@RequestMapping("/api/sensors")
public class SensorController {

    private final SensorProcessingService sensorProcessingService;
    private final SensorEventRepository sensorEventRepository;
    private final ThreadPoolTaskExecutor sensorExecutor;

    @Autowired
    public SensorController(SensorProcessingService sensorProcessingService,
                            SensorEventRepository sensorEventRepository,
                            @Qualifier("sensorExecutor") ThreadPoolTaskExecutor sensorExecutor) {
        this.sensorProcessingService = sensorProcessingService;
        this.sensorEventRepository = sensorEventRepository;
        this.sensorExecutor = sensorExecutor;
    }

    /**
     * Procesa un evento de sensor manualmente
     */
    @PostMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<com.starkindustries.security.model.SensorEvent> processEvent(
            @RequestBody com.starkindustries.security.model.SensorEvent event) {
        CompletableFuture<com.starkindustries.security.model.SensorEvent> future =
                sensorProcessingService.processEventAsync(event);
        com.starkindustries.security.model.SensorEvent processed = future.join();
        return ResponseEntity.ok(processed);
    }

    /**
     * Obtiene todos los eventos de sensores
     */
    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<com.starkindustries.security.model.SensorEvent>> getAllEvents() {
        return ResponseEntity.ok(sensorEventRepository.findAll());
    }

    /**
     * Obtiene eventos por tipo de sensor
     */
    @GetMapping("/events/type/{type}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<com.starkindustries.security.model.SensorEvent>> getEventsByType(
            @PathVariable com.starkindustries.security.model.SensorType type) {
        return ResponseEntity.ok(sensorEventRepository.findBySensorType(type));
    }

    /**
     * Obtiene eventos críticos
     */
    @GetMapping("/events/critical")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<com.starkindustries.security.model.SensorEvent>> getCriticalEvents() {
        return ResponseEntity.ok(sensorEventRepository.findByCriticalTrue());
    }

    /**
     * Obtiene eventos en un rango de fechas
     */
    @GetMapping("/events/range")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<com.starkindustries.security.model.SensorEvent>> getEventsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        return ResponseEntity.ok(sensorEventRepository.findByTimestampBetween(start, end));
    }

    /**
     * Obtiene estadísticas de eventos procesados
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Long> total = new HashMap<>();
        sensorProcessingService.getEventStatistics().forEach((k, v) -> total.put(k.name(), v));
        Map<String, Long> critical = new HashMap<>();
        sensorProcessingService.getCriticalEventStatistics().forEach((k, v) -> critical.put(k.name(), v));

        // También devolver desde BD por compatibilidad
        List<Object[]> rows = sensorEventRepository.countEventsBySensorType();
        Map<String, Long> fromDb = new HashMap<>();
        for (Object[] row : rows) {
            fromDb.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        // Métricas del pool de hilos
        Map<String, Object> threadPool = new HashMap<>();
        try {
            threadPool.put("active", sensorExecutor.getActiveCount());
            threadPool.put("poolSize", sensorExecutor.getPoolSize());
            threadPool.put("corePoolSize", sensorExecutor.getCorePoolSize());
            threadPool.put("maxPoolSize", sensorExecutor.getMaxPoolSize());
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
                "totalEvents", total,
                "criticalEvents", critical,
                "eventsByType", fromDb,
                "activeThreads", sensorExecutor.getActiveCount(),
                "threadPool", threadPool
        ));
    }

    /**
     * Obtiene tiempo promedio de procesamiento por tipo
     */
    @GetMapping("/statistics/processing-time/{type}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<Double> getAverageProcessingTime(
            @PathVariable com.starkindustries.security.model.SensorType type) {
        Double avgTime = sensorEventRepository.getAverageProcessingTime(type);
        return ResponseEntity.ok(avgTime != null ? avgTime : 0.0);
    }

    /**
     * Endpoint de diagnóstico para verificar el estado del sistema
     */
    @GetMapping("/diagnostics")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();

        // Contar eventos totales
        long totalEventsInDb = sensorEventRepository.count();
        diagnostics.put("totalEventsInDatabase", totalEventsInDb);

        // Contar eventos críticos
        long criticalEventsInDb = sensorEventRepository.findByCriticalTrue().size();
        diagnostics.put("criticalEventsInDatabase", criticalEventsInDb);

        // Estadísticas de los contadores en memoria
        diagnostics.put("inMemoryStats", sensorProcessingService.getEventStatistics());
        diagnostics.put("inMemoryCritical", sensorProcessingService.getCriticalEventStatistics());

        // Eventos por tipo desde BD
        List<Object[]> eventsByType = sensorEventRepository.countEventsBySensorType();
        Map<String, Long> eventsByTypeMap = new HashMap<>();
        for (Object[] row : eventsByType) {
            eventsByTypeMap.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        diagnostics.put("eventsByTypeFromDB", eventsByTypeMap);

        return ResponseEntity.ok(diagnostics);
    }

    /**
     * Serie temporal de temperaturas recientes para gráficos
     */
    @GetMapping("/temperatures/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<Map<String, Object>>> getRecentTemperatures(
            @RequestParam(name = "minutes", defaultValue = "60") int minutes,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "criticalOnly", defaultValue = "true") boolean criticalOnly
    ) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(Math.max(1, minutes));
        List<com.starkindustries.security.model.SensorEvent> events = sensorEventRepository
                .findRecentBySensorType(com.starkindustries.security.model.SensorType.TEMPERATURE, since);

        // Filtrar por críticos si se solicita
        if (criticalOnly) {
            events = events.stream().filter(e -> Boolean.TRUE.equals(e.getCritical())).collect(Collectors.toList());
        }

        // Ordenar por timestamp ascendente y limitar al último N
        events.sort(Comparator.comparing(com.starkindustries.security.model.SensorEvent::getTimestamp));
        if (events.size() > limit) {
            events = events.subList(events.size() - limit, events.size());
        }

        // Mapear a payload ligero
        List<Map<String, Object>> payload = events.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("timestamp", e.getTimestamp());
            m.put("value", e.getValue());
            m.put("unit", e.getUnit());
            m.put("location", e.getLocation());
            m.put("critical", e.getCritical());
            return m;
        }).toList();

        return ResponseEntity.ok(payload);
    }

    /**
     * Serie temporal de eventos de movimiento recientes para gráficos
     */
    @GetMapping("/motion/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<Map<String, Object>>> getRecentMotion(
            @RequestParam(name = "minutes", defaultValue = "60") int minutes,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "criticalOnly", defaultValue = "false") boolean criticalOnly
    ) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(Math.max(1, minutes));
        List<com.starkindustries.security.model.SensorEvent> events = sensorEventRepository
                .findRecentBySensorType(com.starkindustries.security.model.SensorType.MOTION, since);

        if (criticalOnly) {
            events = events.stream().filter(e -> Boolean.TRUE.equals(e.getCritical())).collect(Collectors.toList());
        }

        events.sort(Comparator.comparing(com.starkindustries.security.model.SensorEvent::getTimestamp));
        if (events.size() > limit) {
            events = events.subList(events.size() - limit, events.size());
        }

        List<Map<String, Object>> payload = events.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("timestamp", e.getTimestamp());
            m.put("value", e.getValue());
            m.put("location", e.getLocation());
            m.put("critical", e.getCritical());
            return m;
        }).toList();

        return ResponseEntity.ok(payload);
    }

    /**
     * Serie temporal de eventos de acceso recientes para gráficos
     */
    @GetMapping("/access/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHORIZED_USER')")
    public ResponseEntity<List<Map<String, Object>>> getRecentAccess(
            @RequestParam(name = "minutes", defaultValue = "60") int minutes,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "criticalOnly", defaultValue = "false") boolean criticalOnly
    ) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(Math.max(1, minutes));
        List<com.starkindustries.security.model.SensorEvent> events = sensorEventRepository
                .findRecentBySensorType(com.starkindustries.security.model.SensorType.ACCESS, since);

        if (criticalOnly) {
            events = events.stream().filter(e -> Boolean.TRUE.equals(e.getCritical())).collect(Collectors.toList());
        }

        events.sort(Comparator.comparing(com.starkindustries.security.model.SensorEvent::getTimestamp));
        if (events.size() > limit) {
            events = events.subList(events.size() - limit, events.size());
        }

        List<Map<String, Object>> payload = events.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("timestamp", e.getTimestamp());
            m.put("value", e.getValue());
            m.put("location", e.getLocation());
            m.put("critical", e.getCritical());
            return m;
        }).toList();

        return ResponseEntity.ok(payload);
    }
}
