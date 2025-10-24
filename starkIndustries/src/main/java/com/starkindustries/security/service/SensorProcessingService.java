package com.starkindustries.security.service;

import com.starkindustries.security.model.SensorEvent;
import com.starkindustries.security.model.SensorType;
import com.starkindustries.security.repository.SensorEventRepository;
import com.starkindustries.security.sensor.Sensor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// Procesamiento concurrente de eventos de sensores
@Service
@Slf4j
@RequiredArgsConstructor
public class SensorProcessingService {

    private final SensorEventRepository sensorEventRepository;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final Map<String, Sensor> sensors;
    private final MeterRegistry meterRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final ThreadPoolTaskExecutor sensorExecutor;

    private final Map<SensorType, AtomicLong> eventCounters = new ConcurrentHashMap<>();
    private final Map<SensorType, AtomicLong> criticalCounters = new ConcurrentHashMap<>();

    // Snapshot con claves String y valores por tipo presentes
    public Map<String, Object> buildStatsSnapshot() {
        Map<String, Long> total = new HashMap<>();
        for (SensorType st : SensorType.values()) total.put(st.name(), 0L);
        getEventStatistics().forEach((k, v) -> total.put(k.name(), v));

        Map<String, Long> critical = new HashMap<>();
        for (SensorType st : SensorType.values()) critical.put(st.name(), 0L);
        getCriticalEventStatistics().forEach((k, v) -> critical.put(k.name(), v));

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("totalEvents", total);
        snapshot.put("criticalEvents", critical);

        try {
            Map<String, Object> threadPool = new HashMap<>();
            threadPool.put("active", sensorExecutor.getActiveCount());
            threadPool.put("poolSize", sensorExecutor.getPoolSize());
            threadPool.put("corePoolSize", sensorExecutor.getCorePoolSize());
            threadPool.put("maxPoolSize", sensorExecutor.getMaxPoolSize());
            snapshot.put("activeThreads", sensorExecutor.getActiveCount());
            snapshot.put("threadPool", threadPool);
        } catch (Exception e) {
            log.trace("No se pudieron adjuntar métricas de hilos al snapshot: {}", e.getMessage());
        }

        return snapshot;
    }

    @Async("sensorExecutor")
    public CompletableFuture<SensorEvent> processEventAsync(SensorEvent event) {
        log.debug("Iniciando procesamiento asíncrono de evento: {} - Thread: {}",
                  event.getSensorType(), Thread.currentThread().getName());

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Sensor sensor = getSensorByType(event.getSensorType());
            SensorEvent processedEvent = sensor.processEvent(event);
            processedEvent = sensorEventRepository.save(processedEvent);
            updateMetrics(processedEvent);

            Map<String, Object> snapshot = buildStatsSnapshot();
            messagingTemplate.convertAndSend("/topic/stats", snapshot);
            log.debug("Evento enviado a WebSocket: /topic/stats -> {}", snapshot);

            broadcastEvent(processedEvent);

            if (processedEvent.getCritical()) {
                alertService.createAlertFromEvent(processedEvent);
            }

            sample.stop(Timer.builder("sensor.processing.time")
                    .tag("type", event.getSensorType().name())
                    .register(meterRegistry));

            log.info("Evento procesado: ID={}, Tipo={}, Crítico={}",
                     processedEvent.getId(), processedEvent.getSensorType(), processedEvent.getCritical());

            return CompletableFuture.completedFuture(processedEvent);

        } catch (Exception e) {
            log.error("Error procesando evento de sensor: {}", event.getSensorType(), e);
            Counter.builder("sensor.processing.errors")
                    .tag("type", event.getSensorType().name())
                    .register(meterRegistry)
                    .increment();
            throw new RuntimeException("Error procesando evento", e);
        }
    }

    private Sensor getSensorByType(SensorType sensorType) {
        return switch (sensorType) {
            case MOTION -> sensors.get("motionSensor");
            case TEMPERATURE -> sensors.get("temperatureSensor");
            case ACCESS -> sensors.get("accessSensor");
        };
    }

    @Async("sensorExecutor")
    public CompletableFuture<List<SensorEvent>> processBatchAsync(List<SensorEvent> events) {
        log.info("Procesando lote de {} eventos concurrentemente", events.size());
        List<CompletableFuture<SensorEvent>> futures = events.stream()
                .map(this::processEventAsync)
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    private void updateMetrics(SensorEvent event) {
        eventCounters.computeIfAbsent(event.getSensorType(), k -> {
            AtomicLong counter = new AtomicLong(0);
            meterRegistry.gauge("sensor.events.total",
                    List.of(io.micrometer.core.instrument.Tag.of("type", k.name())),
                    counter);
            return counter;
        }).incrementAndGet();

        if (event.getCritical()) {
            criticalCounters.computeIfAbsent(event.getSensorType(), k -> {
                AtomicLong counter = new AtomicLong(0);
                meterRegistry.gauge("sensor.events.critical",
                        List.of(io.micrometer.core.instrument.Tag.of("type", k.name())),
                        counter);
                return counter;
            }).incrementAndGet();
        }

        Counter.builder("sensor.events.processed")
                .tag("type", event.getSensorType().name())
                .tag("critical", String.valueOf(event.getCritical()))
                .register(meterRegistry)
                .increment();
    }

    public Map<SensorType, Long> getEventStatistics() {
        Map<SensorType, Long> stats = new ConcurrentHashMap<>();
        eventCounters.forEach((type, counter) -> stats.put(type, counter.get()));
        return stats;
    }

    public Map<SensorType, Long> getCriticalEventStatistics() {
        Map<SensorType, Long> stats = new ConcurrentHashMap<>();
        criticalCounters.forEach((type, counter) -> stats.put(type, counter.get()));
        return stats;
    }

    // Publica el evento procesado en tópicos WebSocket
    private void broadcastEvent(SensorEvent processedEvent) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", processedEvent.getSensorType().name());
            payload.put("sensorId", processedEvent.getSensorId());
            payload.put("location", processedEvent.getLocation());
            payload.put("value", processedEvent.getValue());
            payload.put("unit", processedEvent.getUnit());
            payload.put("critical", processedEvent.getCritical());
            payload.put("timestamp", processedEvent.getTimestamp());

            String typeTopic = "/topic/sensors/" + processedEvent.getSensorType().name().toLowerCase();
            messagingTemplate.convertAndSend(typeTopic, payload);
            messagingTemplate.convertAndSend("/topic/sensors/events", payload);
        } catch (Exception ex) {
            log.debug("No se pudo publicar evento individual por WS: {}", ex.getMessage());
        }
    }
}
