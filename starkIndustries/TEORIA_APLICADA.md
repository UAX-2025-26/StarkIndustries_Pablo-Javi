# Teoría aplicada al proyecto **Stark Industries Security System** (Tema 1)

Este documento conecta la teoría del archivo `Teoria.md` con la práctica concreta del
proyecto `starkIndustries` (sistema de seguridad de Stark Industries). La idea es que
puedas defender el proyecto explicando **qué hace cada parte** y **qué conceptos de la
asignatura se están aplicando**.

---

## 1. Ecosistema Spring en el proyecto

### 1.1. IoC, DI y Beans (Teoria.md §1.1)

**Concepto teórico**:
- Inversión de Control (IoC): el framework crea y gestiona los objetos (beans).
- Inyección de dependencias (DI): los objetos no se crean a sí mismos, reciben sus
  dependencias desde el contenedor.
- Beneficios: bajo acoplamiento, testabilidad, claridad de responsabilidades.

**Aplicación en el proyecto**:

- Punto de entrada: `StarkSecurityApplication` con `@SpringBootApplication`.
  - Activa el escaneo de componentes (`@Component`, `@Service`, `@Repository`),
    autoconfiguración de Spring Boot y el contenedor IoC.

- Beans de servicio (capa de negocio):
  - `SensorProcessingService`, `SensorSimulationService`, `StatsBroadcastService`,
    `AuthenticationService`, `AlertService`, `NotificationService`, `AccessLogService`,
    `UserService` anotados con `@Service`.
  - Sus dependencias se inyectan por constructor (gracias a `@RequiredArgsConstructor`).

  Ejemplo claro:
  - `AlertService` recibe `SecurityAlertRepository` y `NotificationService` por DI, de
    forma que **no sabe** cómo se construyen, sólo los usa.

- Beans de infraestructura:
  - `AsyncConfiguration`: definida con `@Configuration` y métodos `@Bean` que devuelven
    `ThreadPoolTaskExecutor` (`sensorExecutor`, `alertExecutor`, `notificationExecutor`).
  - `WebSocketConfiguration`, `SecurityConfiguration`, `DataInitializer` son beans de
    configuración que Spring registra en el contexto.

- Beans de acceso a datos:
  - `UserRepository`, `SensorEventRepository`, `SecurityAlertRepository`, `AccessLogRepository`
    anotados con `@Repository` y extendiendo `JpaRepository`.
  - Spring genera automáticamente la implementación: **tu código sólo define la interfaz
    y la semántica de las consultas**.

- Resolución de dependencias:
  - Cuando una clase necesita un repositorio o servicio (`private final XService`),
    Spring lo inyecta automáticamente al construir el bean.
  - En algunos casos se usa `@Qualifier("sensorExecutor")` para resolver qué ejecutor
    concreto se quiere inyectar (lo conecta con el concepto de `@Qualifier` de la teoría).

**Resumen para defenderlo**:
> El proyecto aplica IoC/DI para que todas las piezas (servicios, repositorios, sensores,
> ejecutores) sean **beans gestionados por Spring**. Eso evita crear objetos con `new`
> por todas partes, reduce el acoplamiento y permite configurar fácilmente pools de hilos,
> repositorios, etc. Es la aplicación directa de lo que se explica en la sección 1.1 de
> `Teoria.md`.

---

### 1.2. Spring Boot y autoconfiguración (Teoria.md §1.2)

**Concepto teórico**:
- Spring Boot usa el principio de **convención sobre configuración**.
- Los *starters* (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-security`, `spring-boot-starter-mail`, etc.) incluyen todas las
  dependencias necesarias.
- Autoconfiguración: según lo que haya en el classpath, Spring Boot configura beans
  por defecto (servidor web embebido, DataSource H2, etc.).

**Aplicación en el proyecto**:

- Fichero `pom.xml` (no lo ves aquí, pero existe en el proyecto) añade starters de:
  - Web (`spring-boot-starter-web`): para exponer controladores REST y servir el
    dashboard (`index.html`).
  - Security (`spring-boot-starter-security`): para la autenticación con JWT.
  - Data JPA (`spring-boot-starter-data-jpa`): para acceso a la BD H2.
  - Actuator (`spring-boot-starter-actuator`): para endpoints `/actuator`.
  - WebSocket (stomp) y Mail.

- Configuración mínima en `application.yml`:
  - Define sólo detalles que quieres personalizar (URL de H2, usuario, contraseña,
    logging, parámetros de sensores y alertas).
  - El resto (servidor embebido Tomcat, DataSource HikariCP, etc.) lo configura
    automáticamente Spring Boot.

**Resumen para defenderlo**:
> El proyecto `starkIndustries` es una **aplicación Spring Boot** típica: con los starters
> adecuados y unas pocas propiedades en `application.yml` se obtiene un servidor web con
> seguridad, JPA, WebSocket y Actuator prácticamente sin configuración manual, que es el
> enfoque de Spring Boot explicado en `Teoria.md`.

---

### 1.3. Spring Data JPA (Teoria.md §1.3)

**Concepto teórico**:
- Spring Data abstrae el acceso a datos con interfaces `Repository`/`JpaRepository`.
- Mediante **nombres de método** (`findByXAndY`) o `@Query` se definen consultas sin
  escribir implementación.
- Transacciones, conexión y mapping entidad–tabla las gestiona el framework.

**Aplicación en el proyecto**:

- Entidades JPA (`model`):
  - `User`, `SensorEvent`, `SecurityAlert`, `AccessLog` con `@Entity` y `@Table`.
- Repositorios:
  - `UserRepository` con métodos:
    - `findByUsername`, `findByEmail`, `existsByUsername`, `existsByEmail`.
  - `SensorEventRepository` con consultas por tipo, rango temporal y estadísticas
    (`countEventsBySensorType`, `getAverageProcessingTime`).
  - `SecurityAlertRepository` con consultas para encontrar alertas activas, por nivel,
    y priorizadas.
  - `AccessLogRepository` con consultas para IPs sospechosas y conteo de fallos.

**Resumen para defenderlo**:
> La capa de persistencia sigue el modelo de Spring Data JPA de `Teoria.md`: las entidades
> representan tablas, los repositorios son interfaces que Spring implementa, y el servicio
> (`AlertService`, `AccessLogService`, `UserService`, `SensorProcessingService`) usa esos
> repositorios sin saber nada de SQL ni conexiones.

---

## 2. Concurrencia en el sistema de sensores (Teoria.md §2)

Aunque el proyecto de benchmarks (`pruebas-rendimiento-concurrencia`) se centra en medir
rendimiento, en `starkIndustries` se **aplican los mismos conceptos** pero con un objetivo
funcional: procesar eventos de sensores en paralelo.

### 2.1. Thread pools y `Executor` (Conceptos de §2.2)

**Concepto teórico**:
- El framework Executor separa **qué** se ejecuta (Runnable/Callable) de **cómo** se
  ejecuta (pool de hilos).
- Beneficios: reuso de hilos, control de concurrencia, evitar crear hilos a lo loco.

**Aplicación en el proyecto**:

- Clase `AsyncConfiguration` define tres `ThreadPoolTaskExecutor`:
  - `sensorExecutor`: procesa eventos de sensores.
  - `alertExecutor`: crea alertas a partir de eventos críticos.
  - `notificationExecutor`: envía notificaciones (WebSocket/email) de forma asíncrona.

- Uso en servicios:
  - `SensorProcessingService` está anotado con `@Async("sensorExecutor")` en el método
    `processEventAsync`, por lo que cada evento se procesa en un hilo del pool de
    sensores.
  - `AlertService.createAlertFromEvent` usa `@Async("alertExecutor")` para que la
    generación de alertas no bloquee el procesamiento del evento.
  - `NotificationService.sendAlertNotifications` usa `@Async("notificationExecutor")` para
    no bloquear el hilo que creó la alerta.

Se ve una aplicación directa del patrón `ExecutorService` del tema:
- En lugar de crear `new Thread(…)`, se delega a pools gestionados por Spring.

### 2.2. `CompletableFuture` y composición asíncrona

**Concepto teórico relacionado**:
- Futures/CompletableFuture permiten representar resultados futuros y componer tareas sin
  bloquear hilos innecesariamente.

**Aplicación en el proyecto**:

- `SensorProcessingService.processEventAsync` devuelve `CompletableFuture<SensorEvent>`:
  - Esto permite que la API HTTP pueda **hacer join** si quiere el resultado, o lanzar
    procesamiento en segundo plano.
- `SensorProcessingService.processBatchAsync` crea varios `CompletableFuture` y los
  combina con `CompletableFuture.allOf(...)` (un patrón tipo fork-join, explicado
de forma práctica en el código).

### 2.3. Estructuras concurrentes (Teoria.md §2.3)

**Concepto teórico**:
- `ConcurrentHashMap`, `AtomicLong`, etc. sirven para acumular estadísticas o contadores
  sin necesidad de sincronización manual.

**Aplicación en el proyecto**:

- `SensorProcessingService`:
  - Usa `ConcurrentHashMap<SensorType, AtomicLong> eventCounters` y `criticalCounters`.
  - Cada vez que procesa un evento incrementa el contador correspondiente con
    `computeIfAbsent(..., k -> new AtomicLong()).incrementAndGet()`.
  - No usa `synchronized`; se apoya en la atomicidad de `AtomicLong` y la seguridad de
    `ConcurrentHashMap` bajo concurrencia.

- `AlertService`:
  - Utiliza `ConcurrentHashMap<String, Long> lastAlertByKey` para aplicar lógica de
    **rate limiting** de alertas por sensor/ubicación bajo acceso concurrente desde
    varios hilos del pool de sensores.

**Resumen para defenderlo**:
> Aquí se ve la teoría de concurrencia aplicada: no se gestionan hilos a mano sino con
> `ThreadPoolTaskExecutor` (un executor especializado de Spring), se usan `CompletableFuture`
> para componer trabajo asíncrono, y las estadísticas se guardan en estructuras
> concurrentes (`ConcurrentHashMap`, `AtomicLong`) en lugar de usar `synchronized`.

---

## 3. Seguridad y autenticación (relación con AOP y cross-cutting concerns)

Aunque no es AOP con anotaciones personalizadas como en `procesamiento-pedidos`, la
seguridad y los logs de acceso representan **lógica transversal**, muy ligada a la idea
de AOP del tema.

### 3.1. Filtro JWT como cross-cutting (paralelismo con AOP §3)

- `JwtAuthenticationFilter` extiende `OncePerRequestFilter`:
  - Se ejecuta para **cada petición HTTP**.
  - Extrae el header `Authorization`, valida el token JWT (`JwtService`) y, si es válido,
    mete un `UsernamePasswordAuthenticationToken` en el `SecurityContextHolder`.
  - Es análogo a un aspecto `@Around` que se aplica a todos los endpoints protegidos.

- `SecurityConfiguration` añade este filtro antes de `UsernamePasswordAuthenticationFilter`.

Puedes explicarlo como:
> En lugar de un aspecto `@Around`, se usa un filtro de Spring Security que intercepta
> todas las peticiones y aplica la lógica de seguridad de forma transversal.

### 3.2. `UserService` y bloqueo de cuentas (gestión de estado concurrente)

- `UserService` implementa `UserDetailsService` para que Spring Security pueda cargar
  usuarios desde BD.
- Aplica una política de seguridad:
  - Máximo de 3 intentos fallidos (`MAX_FAILED_ATTEMPTS`).
  - Bloqueo temporal de 5 minutos (`LOCK_TIME_DURATION`).

- Métodos clave:
  - `increaseFailedAttempts(User user)` incrementa contador y bloquea cuando llega al
    tope, guardando `lockTime`.
  - `resetFailedAttempts(User user)` resetea contadores y `lastLogin` al login correcto.
  - `unlockWhenTimeExpired(User user)` comprueba si ya ha pasado el tiempo y desbloquea.

Esto aplica el concepto de **estado compartido y política de acceso** del tema de
concurrencia: aunque aquí se llama desde flujos HTTP, las reglas que se aplican son
claramente definidas y aisladas en el servicio.

### 3.3. Logs de acceso y alertas como observabilidad (ideas de AOP)

- `AccessLogService` + `AccessLogRepository` registran cada intento de login/logout,
  IP, éxito/fracaso, etc.
- `AlertService` convierte eventos críticos en `SecurityAlert` persistente.
- `NotificationService` difunde estas alertas por WebSocket y email.

Esta capa de observabilidad y logging es precisamente el tipo de **lógica transversal**
que en `Teoria.md` se asocia a AOP: aquí se implementa con servicios dedicados y
llamadas explícitas (desde `AuthenticationService`, `SensorProcessingService`, etc.).

---

## 4. Comunicación en tiempo real (relación con programacion reactiva)

Aunque el backend de `starkIndustries` es MVC clásico (no WebFlux), se usan conceptos
cercanos al modelo reactivo del tema 4:

- WebSocket + STOMP:
  - `WebSocketConfiguration` expone `/ws` como endpoint SockJS/STOMP.
  - `NotificationService` y `StatsBroadcastService` publican mensajes en `/topic/alerts`
    y `/topic/stats`.
  - El cliente (JS en `app.js`) se suscribe y reacciona a cada mensaje de forma asíncrona,
    actualizando las gráficas en tiempo real.

- Patrón **event-driven**:
  - `SensorProcessingService.broadcastEvent` construye un `payload` ligero del evento
    y lo envía a `/topic/sensors/{tipo}`.
  - El frontend reacciona cada vez que llega un evento, muy similar a consumir un `Flux`
    en WebFlux, aunque aquí no se use Reactor explícitamente.

Puedes enlazar con `Teoria.md` así:
> Aunque aquí no usamos `Mono`/`Flux`, sí explotamos un modelo **asíncrono y orientado
> a flujos de eventos**, que es la misma idea base de la programación reactiva: los
> clientes reciben actualizaciones según se producen eventos en el sistema, sin
> necesidad de hacer polling.

---

## 5. Arquitectura de capas y microservicios (Teoria.md §5)

En este tema se presenta la arquitectura de microservicios, pero este proyecto concreto
es **un solo servicio**. Aun así, respeta varias **buenas prácticas** que se reutilizan
en microservicios:

- Separación de responsabilidades:
  - Controladores REST (`controller`): exponen la API HTTP/WS.
  - Servicios (`service`): encapsulan la lógica de negocio.
  - Repositorios (`repository`): acceso a datos.
  - Modelo (`model`): entidades de dominio y DTOs (records).

- Pensado para escalar horizontalmente:
  - Al usar JWT (stateless) y pools de hilos para trabajo pesado, la instancia no
    guarda estado de sesión en memoria, lo que facilita el escalado en múltiples
    réplicas, como se recomienda en microservicios.

- Observabilidad:
  - Actuator expone `/actuator/health`, `/actuator/metrics`, etc., que son los
    puntos de entrada típicos para Prometheus/Grafana en entornos cloud.

Puedes situarlo como:
> Este proyecto sería uno de los microservicios de un sistema mayor de Stark Industries
> (p.ej. “security-service”). Sigue las mismas ideas del tema de microservicios: estado
> local (BD), API bien definida (`/api/*`), logging y métricas expuestas por Actuator.

---

## 6. Resumen para la defensa del proyecto

Cuando tengas que explicarlo, puedes estructurarlo así:

1. **Arquitectura general**:
   - Aplicación Spring Boot monolítica pero estructurada en capas.
   - Uso intensivo de IoC/DI, repositorios Spring Data y configuración centralizada
     en `application.yml` (relacionado con §1 de `Teoria.md`).

2. **Concurrencia**:
   - Pools de hilos dedicados (sensores, alertas, notificaciones) configurados en
     `AsyncConfiguration`.
   - Métodos `@Async` que devuelven `CompletableFuture`.
   - Uso de `ConcurrentHashMap` y `AtomicLong` para métricas.
   - Conexión con el tema de `ExecutorService` y estructuras concurrentes (§2).

3. **Seguridad**:
   - Filtro JWT (`JwtAuthenticationFilter`) como lógica transversal.
   - `UserService` con bloqueo por intentos fallidos y desbloqueo automático.
   - `AccessLogService` y `AlertService` como mecanismos de auditoría y detección de
     comportamiento sospechoso.

4. **Comunicación en tiempo real**:
   - WebSocket/STOMP para notificaciones y estadísticas.
   - El frontend recibe flujos de eventos y actualiza gráficas en tiempo real,
     enlazando con la idea de programación reactiva del tema, aunque sin usar WebFlux.

5. **Relación con microservicios y arquitectura cloud**:
   - Diseño listo para escalar horizontalmente (stateless + JWT + Actuator).
   - Podría ser un microservicio de seguridad dentro de una plataforma mayor, como se
     describe en la sección de microservicios de `Teoria.md`.

Con este documento puedes justificar **cada decisión técnica** del proyecto usando los
conceptos exactos de la asignatura: IoC/DI, pools de hilos, estructuras concurrentes,
seguridad transversal, eventos en tiempo real y buenas prácticas de arquitectura.
