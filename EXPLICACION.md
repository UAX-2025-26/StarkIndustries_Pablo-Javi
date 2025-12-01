# TEMA 1 - concurrencia-stark-industries

## 1. Visión general de la arquitectura

Este proyecto simula el sistema de seguridad de Stark Industries usando Spring Boot. El objetivo didáctico principal es ver cómo se combinan:

- **IoC/DI de Spring**: el contenedor crea e inyecta servicios, repositorios, sensores, etc.
- **Programación concurrente**: procesado asíncrono de eventos de sensores con pools de hilos.
- **Comunicación en tiempo real**: WebSockets/STOMP para enviar estadísticas y eventos en vivo al dashboard.
- **Persistencia con Spring Data JPA**: repositorios que encapsulan el acceso concurrente a la BD.
- **Seguridad con Spring Security + JWT**: control de acceso concurrente a las APIs.

La aplicación principal es `StarkSecurityApplication`:

- `@SpringBootApplication`: arranca el contexto de Spring (escaneo de componentes, autoconfiguración).
- `@EnableAsync`: activa el soporte de métodos `@Async`, que son la base de la ejecución concurrente en este sistema.
- `@EnableScheduling`: permite tareas programadas (por ejemplo, simulación periódica de sensores o envío de estadísticas).

Al arrancar, la app levanta un servidor embebido (Tomcat) y deja accesible:

- Dashboard web (`index.html`) en `http://localhost:8080`.
- Endpoints de Actuator (`/actuator`) para métricas y health.
- Consola H2 (`/h2-console`) para inspeccionar los datos almacenados.

## 2. Componentes principales y su rol

### 2.1. Modelo de dominio (`model`)

Clases como `User`, `SensorEvent`, `SecurityAlert`, `AccessLog`, `SensorType` representan el **modelo de dominio** del sistema.

Ejemplo: `SensorEvent`:

- `@Entity` + `@Table("sensor_events")`: mapea la clase a una tabla relacional.
- Campos clave:
  - `sensorType`, `sensorId`, `location`: identifican el sensor origen.
  - `value`, `unit`: valor medido (temperatura, movimiento, acceso, etc.).
  - `critical`: bandera que indica si el evento es crítico (apertura no autorizada, temperatura fuera de rango,…).
  - `timestamp`, `processedAt`: tiempos de ocurrencia y de procesamiento.
  - `processingTimeMs`: latencia de procesamiento, útil para análisis de rendimiento.

Aquí se ve la separación entre **datos de entrada** y **datos de procesamiento**, importante para auditar qué hizo el sistema y cuándo.

### 2.2. Sensores (`sensor`)

`Sensor` es una **interfaz** que define el contrato común de todos los sensores:

- `SensorEvent processEvent(SensorEvent event)`: lógica específica del sensor (por ejemplo, normalizar valores, marcar como crítico,…).
- `boolean requiresAlert(Double value)`: política para decidir si un valor genera alerta.
- `String getSensorType()`: tipo de sensor para identificación.
- `SensorEvent simulateEvent()`: generación de eventos sintéticos para pruebas/simulación.

Implementaciones como `TemperatureSensor`, `MotionSensor` y `AccessControlSensor` aplican el patrón **Estrategia**: el sistema trata a todos como `Sensor`, pero cada uno implementa su comportamiento específico.

### 2.3. Repositorios (`repository`)

Interfaces como `SensorEventRepository`, `SecurityAlertRepository`, `UserRepository`, `AccessLogRepository` extienden de Spring Data (por ejemplo, `JpaRepository`):

- No se programa el acceso JDBC "a mano"; Spring genera la implementación.
- Gestión de concurrencia/aislamiento la maneja la base de datos.
- Se pueden definir métodos derivados del nombre (p.ej. `findBySensorType(...)`).

Desde el punto de vista de la concurrencia, varios hilos pueden llamar a estos repositorios simultáneamente sin que el desarrollador tenga que sincronizar nada: el framework y la BD se encargan.

### 2.4. Servicios (`service`)

Aquí se concentra la lógica de negocio y concurrente.

- `SensorProcessingService`: corazón del procesamiento concurrente de eventos.
- `SensorSimulationService`: genera eventos periódicos para simular la actividad de la fábrica.
- `AlertService`: gestiona las alertas de seguridad derivadas de eventos críticos.
- `NotificationService`: envía notificaciones (por ejemplo, a otros sistemas o por WebSocket).
- `UserService`, `AuthenticationService`: gestión de usuarios y autenticación.
- `AccessLogService`: registro de accesos para auditoría.
- `StatsBroadcastService`: envío periódico de estadísticas agregadas mediante WebSocket.

#### SensorProcessingService (pieza clave de TEMA 1)

`SensorProcessingService` aplica varios conceptos de **Teoría de concurrencia (Tema 1)**:

- **Thread pools** con `ThreadPoolTaskExecutor`:
  - En vez de crear un hilo nuevo por petición (costoso), se usa un pool de hilos reutilizables.
  - El pool está configurado en `AsyncConfiguration` (tamaños de core, máximo, cola, prefijos de nombre).
- **Ejecución asíncrona con `@Async`**:
  - Métodos como `processEventAsync(...)` y `processBatchAsync(...)` se ejecutan en hilos del pool `sensorExecutor`.
  - El hilo HTTP que recibe la petición delega el trabajo al pool y queda libre para atender más clientes.
- **Estructuras concurrentes**:
  - `ConcurrentHashMap<SensorType, AtomicLong>` para contadores por tipo de sensor.
  - Se evita usar `synchronized` global y se reduce la contención entre hilos.
- **Futuros y composición de tareas**:
  - `CompletableFuture<SensorEvent>` para representar el resultado futuro del procesamiento.
  - `CompletableFuture.allOf(...)` para esperar a un lote completo de tareas concurrentes.

La secuencia en `processEventAsync` es:

1. **Inicio asíncrono**: el método se ejecuta en un hilo del pool `sensorExecutor` gracias a `@Async("sensorExecutor")`.
2. **Selección de estrategia de sensor**: usa `getSensorByType` para escoger la implementación correcta (`TemperatureSensor`, `MotionSensor`, etc.).
3. **Procesamiento del evento**: llama a `sensor.processEvent(event)` (posible lógica de negocio intensiva).
4. **Persistencia**: guarda el evento procesado en la base de datos con `sensorEventRepository.save`.
5. **Actualización de métricas**: incrementa contadores concurrentes y métricas de Micrometer.
6. **Difusión en tiempo real**:
   - Construye un snapshot de estadísticas (`buildStatsSnapshot`) y lo envía por `/topic/stats`.
   - Publica el evento individual en tópicos específicos (`/topic/sensors/{tipo}` y `/topic/sensors/events`).
7. **Generación de alerta** (si el evento es crítico): delega en `AlertService` para crear una `SecurityAlert`.
8. **Registro de tiempos**: mide la duración del procesamiento (`sensor.processing.time`) etiquetado por tipo.

En caso de error, incrementa un contador de errores y lanza una `RuntimeException` encapsulando la causa.

Este diseño muestra claramente la **separación de responsabilidades**:

- Procesado del valor del sensor: en las implementaciones de `Sensor`.
- Orquestación y concurrencia: en `SensorProcessingService`.
- Almacenamiento: en `SensorEventRepository`.
- Alertas y notificaciones: `AlertService` y `NotificationService`.

### 2.5. Configuración de concurrencia (`AsyncConfiguration`)

`AsyncConfiguration` implementa `AsyncConfigurer` y define varios `ThreadPoolTaskExecutor`:

- `sensorExecutor`: orientado a procesar eventos de sensores. Tiene:
  - `corePoolSize(20)`, `maxPoolSize(50)`, `queueCapacity(200)`: suficientes hilos para manejar múltiples sensores sin saturar el sistema.
  - `setWaitForTasksToCompleteOnShutdown(true)`: al apagar la app, espera a que se terminen las tareas pendientes.
- `alertExecutor`: para tareas relacionadas con alertas.
- `notificationExecutor`: para tareas de envío de notificaciones.

Además, `getAsyncExecutor()` devuelve por defecto `sensorExecutor`, de forma que si un método se marca con `@Async` sin indicar el nombre del executor, usará este pool.

Desde la perspectiva de teoría, esto ilustra:

- **Separación de pools por tipo de carga** (similar al patrón *Bulkhead*):
  - Eventos de sensores, alertas y notificaciones no compiten todos por el mismo conjunto de hilos.
- **Dimensión de pools**: se eligen tamaños de core/máximo y capacidad de cola para equilibrar rendimiento y estabilidad.

### 2.6. Configuración WebSocket (`WebSocketConfiguration`)

`WebSocketConfiguration` habilita mensajería STOMP sobre WebSocket:

- `@EnableWebSocketMessageBroker`: activa el broker de mensajes en memoria.
- `configureMessageBroker`:
  - `enableSimpleBroker("/topic", "/queue")`: define prefijos para canales de salida hacia el cliente.
  - `setApplicationDestinationPrefixes("/app")`: prefijo para mensajes que los clientes envían al servidor.
- `registerStompEndpoints`:
  - Define el endpoint de conexión `/ws` con soporte `SockJS`.

Esto permite que el dashboard reciba datos en tiempo real sin hacer polling; los hilos de procesamiento simplemente publican en el broker y éste distribuye a los clientes suscritos.

### 2.7. Seguridad (`SecurityConfiguration` + `JwtAuthenticationFilter` + `JwtService`)

`SecurityConfiguration` define las reglas de seguridad:

- Se desactiva CSRF para simplificar el uso de APIs.
- Se habilita CORS con configuración laxa para permitir llamadas desde el frontend.
- Se marcan rutas públicas:
  - `/`, `index.html`, recursos estáticos, `/api/auth/**`, `/ws/**`, `/h2-console/**`, `/actuator/health`, etc.
- Se restringen otras rutas por rol:
  - `/api/admin/**` y `/actuator/**` solo para `ROLE_ADMIN`.
  - `/api/sensors/**` y `/api/alerts/**` para `ROLE_ADMIN` y `ROLE_AUTHORIZED_USER`.
- `SessionCreationPolicy.STATELESS`: el sistema no usa sesión HTTP; la autenticación es por **JWT** en cada petición.
- Se añade `JwtAuthenticationFilter` antes de `UsernamePasswordAuthenticationFilter` para extraer y validar el token JWT de cada request.

Puntos de teoría relevantes:

- **Seguridad concurrente**: muchas peticiones pueden llegar a la vez; el filtro JWT y los servicios de autenticación deben ser *thread-safe* (sin estado mutable por petición).
- **Bajo acoplamiento**: el servicio `UserDetailsService` se inyecta en el `AuthenticationProvider` (`DaoAuthenticationProvider`).

Los componentes `JwtService` y `JwtAuthenticationFilter` se encargan de:

- Generar tokens JWT con información de usuario/roles.
- Validar tokens en cada petición.
- Construir un `Authentication` que Spring Security coloca en el `SecurityContext` por hilo.

### 2.8. Inicialización de datos (`DataInitializer`)

`DataInitializer` suele ser un componente que se ejecuta al inicio para:

- Crear usuarios de prueba (admin, usuario autorizado).
- Crear sensores, ubicaciones, etc.

Así puedes levantar el proyecto y tener datos mínimos para probar sin configuración manual.

### 2.9. Controladores (`controller`)

- `AuthenticationController`: expone endpoints para login y obtención de JWT.
- `SensorController`: endpoints para recibir eventos de sensores o lanzar simulaciones.
- `AlertController`: para consultar o gestionar alertas.
- `AdminController`: paneles y acciones administrativas.
- `WebSocketController`:gestiona destinos específicos para mensajería (dependiendo de la implementación exacta).

La idea es que los controladores **no contienen lógica de negocio compleja**; actúan como capa de entrada y delegan en servicios.

## 3. Flujo completo de un evento de sensor (end-to-end)

1. **Generación del evento**:
   - Real por hardware (en un escenario real) o simulado por `SensorSimulationService`.
   - Se crea un `SensorEvent` con tipo, valor, hora, etc.

2. **Entrada en el sistema**:
   - Llega una petición HTTP/REST a `SensorController` o un mensaje desde una tarea programada.

3. **Delegación asíncrona**:
   - El controlador llama a `SensorProcessingService.processEventAsync(event)`.
   - Gracias a `@Async("sensorExecutor")`, la ejecución pasa a un hilo del pool.

4. **Procesamiento concurrente**:
   - En el hilo del pool:
     - Se aplica lógica del sensor concreto.
     - Se guarda el resultado en BD.
     - Se actualizan métricas y contadores.
   - Otros eventos pueden estarse procesando en paralelo en otros hilos del mismo pool.

5. **Reacción a eventos críticos**:
   - Si `critical == true`, se llama a `alertService.createAlertFromEvent`.
   - Esto puede derivar en notificaciones externas o registros adicionales.

6. **Difusión a clientes**:
   - `SensorProcessingService` construye un snapshot de estadísticas y lo envía por `/topic/stats`.
   - El evento individual se envía por `/topic/sensors/...`.
   - El dashboard Web, suscrito a estos tópicos, actualiza la interfaz en tiempo real.

7. **Monitoreo y métricas**:
   - Micrometer expone métricas como `sensor.events.total`, `sensor.events.critical`, `sensor.processing.time`.
   - Actuator/Prometheus pueden recolectar estos datos para analizar comportamiento bajo distintas cargas.

## 4. Decisiones de diseño y relación con la teoría (Tema 1)

1. **Uso de `@Async` + `ThreadPoolTaskExecutor` en lugar de crear hilos a mano**:
   - Teoría: crear/destruir hilos es costoso; conviene reciclarlos con pools.
   - Implementación: `AsyncConfiguration` define pools; `SensorProcessingService` usa `@Async` para delegar trabajo.

2. **Separación de pools por tipo de carga**:
   - Teoría: patrón *Bulkhead* y aislamiento de recursos para evitar que una carga sature todo el sistema.
   - Implementación: `sensorExecutor`, `alertExecutor`, `notificationExecutor`.

3. **Uso de colecciones concurrentes y tipos atómicos**:
   - Teoría: condiciones de carrera ocurren cuando varios hilos escriben/leen el mismo estado sin sincronización.
   - Implementación: `ConcurrentHashMap` + `AtomicLong` evitan tener que escribir `synchronized` y escalan mejor bajo alta concurrencia.

4. **Diseño orientado a eventos (event-driven)**:
   - Teoría: en sistemas concurrentes de alta carga, es habitual diseñar flujos dirigidos por eventos (mensajes) en lugar de llamadas bloqueantes.
   - Implementación: `processEventAsync` + publicación por WebSocket + `AlertService` muestran esta cadena de reacciones.

5. **Seguridad sin estado (JWT) y stateless sessions**:
   - Teoría: en entornos concurrentes y escalables (por ejemplo, balanceadores), mantener estado de sesión en el servidor dificulta el escalado.
   - Implementación: `SessionCreationPolicy.STATELESS` y JWT hacen que cada petición lleve sus credenciales.

6. **Uso de métricas y logs estructurados**:
   - Teoría: para entender el comportamiento de un sistema concurrente, no basta con probar en local; hace falta instrumentación.
   - Implementación: uso de Micrometer y logs con información de hilo, tipo de sensor, criticidad, etc.

## 5. Cómo defender este proyecto en una exposición

Al explicarlo, puedes seguir este guion:

1. **Contexto**: "Este proyecto simula un sistema de seguridad industrial. El objetivo es ver cómo aplicar la teoría de concurrencia con Spring Boot".
2. **Arquitectura**: comenta brevemente los paquetes (`model`, `repository`, `service`, `sensor`, `config`, `controller`).
3. **Concurrencia**:
   - Explica `AsyncConfiguration` y los pools de hilos.
   - Explica cómo `@Async` y `CompletableFuture` permiten atender muchos eventos a la vez.
   - Menciona estructuras concurrentes (`ConcurrentHashMap`, `AtomicLong`).
4. **Flujo de un evento**: recorre de forma narrativa los pasos 1–7 de la sección anterior.
5. **Relación con la teoría**:
   - Conecta con IoC/DI (inyección de servicios y sensores).
   - Explica el uso de Executor y diferéncialo de crear threads a mano.
   - Menciona cómo se evita la sincronización explícita usando estructuras de `java.util.concurrent`.
6. **Seguridad y tiempo real**:
   - Cuenta cómo se protege la API con JWT y roles.
   - Explica brevemente WebSocket/STOMP para actualización en vivo.

Con esto deberías poder explicar el proyecto como si lo hubieras diseñado tú, justificando cada decisión con argumentos de teoría de concurrencia y de arquitectura Spring.
