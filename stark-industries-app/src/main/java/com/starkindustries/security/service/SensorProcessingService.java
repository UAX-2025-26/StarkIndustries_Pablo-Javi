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

// Procesamiento concurrente de eventos de sensores. Esta clase es el "corazón concurrente" del sistema,
// donde se aplican los conceptos de programación concurrente vistos en teoría:
// - Uso de hilos gestionados por un ThreadPool (`ThreadPoolTaskExecutor`).
@Service
@Slf4j
@RequiredArgsConstructor
public class SensorProcessingService {

    // Repositorio JPA: acceso concurrente seguro gestionado por Spring y la BD
    private final SensorEventRepository sensorEventRepository;
    // Servicios delegados: aplican el principio de separación de responsabilidades
    private final AlertService alertService;
    private final NotificationService notificationService;
    // Mapa de sensores inyectado por Spring (IoC/DI). Clave = beanName, Valor = implementación concreta
    // Permite seleccionar en tiempo de ejecución qué estrategia de procesamiento usar para cada tipo de sensor
    private final Map<String, Sensor> sensors;
    // Registro de métricas (Micrometer): expone contadores y tiempos a Actuator/Prometheus
    private final MeterRegistry meterRegistry;
    // Canal WebSocket para difusión en tiempo real (no bloquea peticiones HTTP)
    private final SimpMessagingTemplate messagingTemplate;
    // Executor específico para sensores, definido en `AsyncConfiguration`.
    // Es un pool de hilos reutilizables: mejor uso de CPU que crear un hilo por petición.
    private final ThreadPoolTaskExecutor sensorExecutor;

    // Contadores por tipo de sensor. Se usa ConcurrentHashMap + AtomicLong para:
    // - Evitar bloqueos gruesos (no se usa `synchronized` global).
    // - Permitir muchas actualizaciones concurrentes con baja contención.
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

    @Async("sensorExecutor") // Indica que este método se ejecutará de forma asíncrona en un hilo separado usando el executor especificado ("sensorExecutor")
    public CompletableFuture<SensorEvent> processEventAsync(SensorEvent event) {
        // Marcamos claramente que este método se ejecuta en un hilo del pool `sensorExecutor`.
        // Desde el punto de vista del controlador HTTP, la llamada es "fire-and-forget":
        // el hilo del servidor delega el trabajo a este pool y puede atender otras peticiones.
        log.debug("Iniciando procesamiento asíncrono de evento: {} - Thread: {}",
                  event.getSensorType(), Thread.currentThread().getName());

        // Tomamos una muestra de tiempo con Micrometer para medir la latencia por tipo de sensor.
        // Esto nos permite analizar el rendimiento bajo carga (concepto de benchmarking y profiling).
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Seleccionamos la implementación de `Sensor` adecuada según el tipo.
            // Este es un ejemplo de "estrategia": la lógica específica de cada sensor se encapsula en su clase.
            Sensor sensor = getSensorByType(event.getSensorType());

            // Procesamos el evento (cálculos, normalización, etc.) en el hilo del pool.
            SensorEvent processedEvent = sensor.processEvent(event);

            // Persistimos el evento en BD. Spring y la BD se encargan de la seguridad en concurrencia a nivel de datos.
            processedEvent = sensorEventRepository.save(processedEvent);

            // Actualizamos contadores concurrentes y métricas centrales.
            updateMetrics(processedEvent);

            // Construimos un snapshot consistente de estadísticas. Nótese que no se bloquea el pool:
            // sólo se leen contadores atómicos, operación O(1) y muy rápida.
            Map<String, Object> snapshot = buildStatsSnapshot();
            // Difundimos las estadísticas a los clientes Web vía WebSocket/SSE, desacoplando lectura y escritura.
            messagingTemplate.convertAndSend("/topic/stats", snapshot);
            log.debug("Evento enviado a WebSocket: /topic/stats -> {}", snapshot);

            // Publicamos el evento concreto en canales específicos.
            broadcastEvent(processedEvent);

            // Si el evento es crítico, se encadena de forma asíncrona un flujo de alertas/ notificaciones.
            if (processedEvent.getCritical()) {
                // Aquí se aplica el principio de "reacción a eventos": el sistema actúa según el tipo de evento.
                alertService.createAlertFromEvent(processedEvent);
            }

            // Cerramos la medición de tiempo y registramos la métrica etiquetada por tipo de sensor.
            sample.stop(Timer.builder("sensor.processing.time")
                    .tag("type", event.getSensorType().name())
                    .register(meterRegistry));

            log.info("Evento procesado: ID={}, Tipo={}, Crítico={}",
                     processedEvent.getId(), processedEvent.getSensorType(), processedEvent.getCritical());

            // Devolvemos un CompletableFuture ya completado con el resultado.
            // En otros escenarios podríamos encadenar más etapas con `thenApply`, `thenCompose`, etc.
            return CompletableFuture.completedFuture(processedEvent);

        } catch (Exception e) {
            // En sistemas concurrentes el manejo de errores es clave: un fallo en un hilo del pool
            // no debe tumbar el proceso completo. Se registra una métrica de error y se encapsula la excepción.
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
        // Procesa un lote de eventos en paralelo reutilizando el método unitario.
        // Cada evento se procesa en un hilo potencialmente distinto del pool, ilustrando
        // el patrón "fork-join" simplificado con `CompletableFuture.allOf`.
        log.info("Procesando lote de {} eventos concurrentemente", events.size());

        // Transformamos la lista en una lista de futuros individuales.
        List<CompletableFuture<SensorEvent>> futures = events.stream()
                .map(this::processEventAsync)
                .toList();

        // `allOf` espera a que todos los futuros terminen (éxito o error).
        // Es el equivalente de usar un `CountDownLatch` pero con la API de CompletableFuture.
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    private void updateMetrics(SensorEvent event) {
        // Se registran métricas por tipo de sensor aprovechando gauges + contadores atómicos.
        // `computeIfAbsent` es seguro en `ConcurrentHashMap` y evita crear contadores duplicados.
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

        // Métrica adicional de eventos procesados, etiquetada por criticidad.
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
        // Este método encapsula la publicación de eventos individuales por WebSocket.
        // Concepto de "event-driven": cada vez que el backend procesa algo,
        // los clientes suscritos reciben la actualización sin hacer polling.
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", processedEvent.getSensorType().name());
            payload.put("sensorId", processedEvent.getSensorId());
            payload.put("location", processedEvent.getLocation());
            payload.put("value", processedEvent.getValue());
            payload.put("unit", processedEvent.getUnit());
            payload.put("critical", processedEvent.getCritical());
            payload.put("timestamp", processedEvent.getTimestamp());

            // Canal específico por tipo de sensor (permite a los clientes suscribirse sólo a lo que les interesa)
            String typeTopic = "/topic/sensors/" + processedEvent.getSensorType().name().toLowerCase();
            messagingTemplate.convertAndSend(typeTopic, payload);
            // Canal agregado con todos los eventos de sensores
            messagingTemplate.convertAndSend("/topic/sensors/events", payload);
        } catch (Exception ex) {
            // Los fallos al notificar por WS no deben parar el procesamiento de sensores.
            // Se registra en log a nivel debug para diagnóstico sin saturar el log principal.
            log.debug("No se pudo publicar evento individual por WS: {}", ex.getMessage());
        }
    }
}
