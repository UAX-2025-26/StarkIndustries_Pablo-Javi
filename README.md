# Sistema de Seguridad Concurrente — Stark Industries

Resumen del proyecto, cómo ejecutarlo, quiénes son los autores y cómo está organizada la solución para cumplir el enunciado.

- Nombre del caso: Implementación de un Sistema de Seguridad Concurrente en Stark Industries
- Tecnologías: Java 21, Spring Boot 3.2, Spring Security (JWT), WebSocket (STOMP), Spring Data JPA (H2), Actuator, Micrometer/Prometheus, Lombok.
- Estado: Funcional con simulación de sensores y procesamiento concurrente.

Equipo:
- Pablo Lozano
- Javier Yustres

## Estructura del proyecto

```
concurrencia-stark-industries/
├── README.md (este archivo)
├── LICENSE
├── docs/ (documentación técnica y diagramas)
└── starkIndustries/ (módulo de la aplicación)
    ├── pom.xml
    ├── run.bat (script de ejecución)
    ├── test-login.html
    ├── docs/
    ├── logs/
    └── src/
        └── main/
            └── java/
                └── com/starkindustries/
```

El código de la aplicación está dentro del módulo `starkIndustries/`.

## Cómo ejecutar

Requisitos:
- Java 21 en PATH (verificar con `java -version`)
- Maven 3.9+ (verificar con `mvn -v`)

### Opción 1: Usando el script run.bat

```cmd
cd starkIndustries
run.bat
```

### Opción 2: Usando Maven directamente

```cmd
cd starkIndustries
mvn clean spring-boot:run
```

## Acceso rápido
- Dashboard: http://localhost:8080
- API (ejemplos):
  - Autenticación: POST /api/auth/login
  - Sensores: GET /api/sensors/events, POST /api/sensors/simulate
  - Alertas: GET /api/alerts/active
- WebSocket STOMP: ws://localhost:8080/ws (topics /topic/stats, /topic/sensors/*, /topic/alerts)
- Actuator: http://localhost:8080/actuator
- H2 Console: http://localhost:8080/h2-console (jdbc:h2:mem:securitydb)

## Credenciales iniciales

Creadas automáticamente por DataInitializer:
- admin / admin123 — ROLE_ADMIN
- jarvis / jarvis123 — ROLE_AUTHORIZED_USER

Descripción breve de la lógica (1 línea por archivo relevante):
- src/main/java/com/starkindustries/security/StarkSecurityApplication.java: Punto de entrada; habilita @EnableAsync y @EnableScheduling.
- config/AsyncConfiguration.java: Define ejecutores ThreadPool para sensores, alertas y notificaciones.
- config/SecurityConfiguration.java: Configura Spring Security + JWT, rutas públicas y roles.
- config/WebSocketConfiguration.java: Habilita STOMP y broker simple (/topic, /queue), endpoint /ws.
- config/DataInitializer.java: Crea usuarios por defecto con roles.
- sensor/Sensor.java: Contrato para sensores (procesar, alertar, simular).
- sensor/MotionSensor.java, TemperatureSensor.java, AccessControlSensor.java: Implementaciones IoC de sensores.
- service/SensorSimulationService.java: Programa eventos simulados periódicos y ráfagas.
- service/SensorProcessingService.java: Procesa eventos concurrentemente, persiste, emite métricas y WS.
- service/AlertService.java: Aplica cooldown, nivelea y persiste alertas, dispara notificaciones.
- service/NotificationService.java: Notifica por WebSocket y email; PUSH simulado.
- service/StatsBroadcastService.java: Publica snapshots periódicos de métricas por WebSocket.
- controller/AuthenticationController.java: Autenticación JWT.
- controller/SensorController.java: Endpoints de sensores y simulación.
- controller/AlertController.java: Consulta/gestión de alertas.
- controller/AdminController.java: Operaciones de administración y creación de usuarios.
- model/SensorEvent.java, SecurityAlert.java, User.java, AccessLog.java: Entidades del dominio y registro.
- repository/*.java: Repositorios JPA para entidades.
- resources/application.yml: Configuración (JWT, sensores, alertas, logging, Actuator, simulación).
- resources/static/*: UI simple con dashboard, JS para conectar STOMP y graficar.

Arquitectura (alto nivel):
- Sensores (IoC) generan eventos simulados (@Scheduled) -> Servicio de Procesamiento (@Async) -> Repositorios (JPA) y Métricas (Micrometer) -> WebSocket para UI.
- Eventos críticos -> Servicio de Alertas (cooldown, nivel) -> Notificaciones (WS, email) y repositorio de alertas.
- Seguridad: JWT para proteger APIs; roles ADMIN / AUTHORIZED_USER.

Configuración clave (application.yml):
- security.jwt.secret / expiration
- security.sensor.* (umbrales, límites)
- security.alerts.cooldown-ms
- stark.sensors.simulation.enabled y eventos min/max
- logging.file.name (logs/stark-security.log)

Métricas y monitorización:
- Actuator habilitado (/actuator) y métricas expuestas.
- Micrometer con Prometheus; contadores de eventos y tiempos de procesamiento.

Logs:
- Archivo: logs/stark-security.log
- Niveles: DEBUG para la app, INFO para frameworks principales.

Diagrama de clases:
- Ver starkindustries/docs/arquitectura-clases.md (Mermaid) y leer relaciones servicio/sensor/repositorio/controlador.

Pruebas rápidas (manuales):
- Comprobar dashboard moviéndose y llegada de eventos en /topic/sensors/events.
- Forzar simulación (si hay endpoints) y verificar alertas en /topic/alerts.
- Revisar H2 Console para eventos/alertas persistidos.

Notas: Email requiere configuración real de SMTP para envío externo; la BD es H2 en memoria para demo; broker WS simple.
