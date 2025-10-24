# Arquitectura de Clases — Sistema de Seguridad Concurrente

```mermaid
classDiagram
    direction LR

    class Sensor {
      +processEvent(SensorEvent) SensorEvent
      +requiresAlert(Double) boolean
      +getSensorType() String
      +simulateEvent() SensorEvent
    }

    class MotionSensor
    class TemperatureSensor
    class AccessControlSensor
    Sensor <|.. MotionSensor
    Sensor <|.. TemperatureSensor
    Sensor <|.. AccessControlSensor

    class SensorSimulationService {
      +simulateSensorEvents()
      +simulateHighLoad()
    }
    class SensorProcessingService {
      +processEventAsync(SensorEvent) CompletableFuture
      +processBatchAsync(List~SensorEvent~) CompletableFuture
      +buildStatsSnapshot() Map
    }
    class AlertService {
      +createAlertFromEvent(SensorEvent) CompletableFuture~SecurityAlert~
    }
    class NotificationService {
      +sendAlertNotifications(SecurityAlert)
      +sendEventNotification(String, Object)
    }
    class StatsBroadcastService {
      +broadcastStats()
    }

    class SecurityConfiguration
    class WebSocketConfiguration
    class AsyncConfiguration
    class DataInitializer

    class AuthenticationService
    class JwtService
    class JwtAuthenticationFilter

    class SensorController
    class AlertController
    class AdminController
    class AuthenticationController
    class WebSocketController

    class SensorEventRepository
    class SecurityAlertRepository
    class AccessLogRepository
    class UserRepository

    class SensorEvent
    class SecurityAlert
    class AccessLog
    class User
    class SensorType

    SensorSimulationService --> Sensor : usa
    SensorSimulationService --> SensorProcessingService : envía lotes
    SensorProcessingService --> Sensor : delega procesamiento
    SensorProcessingService --> SensorEventRepository : persiste
    SensorProcessingService --> NotificationService : WS stats/events
    SensorProcessingService --> AlertService : crea alertas

    AlertService --> SecurityAlertRepository : persiste
    AlertService --> NotificationService : notifica

    NotificationService ..> SimpMessagingTemplate : WebSocket
    NotificationService ..> JavaMailSender : Email

    AuthenticationController --> AuthenticationService : login
    SecurityConfiguration --> JwtAuthenticationFilter : cadena
    JwtAuthenticationFilter --> JwtService : valida JWT

    AdminController ..> UserService
    UserService --> UserRepository
    DataInitializer --> UserService

    WebSocketController ..> SimpMessagingTemplate

    StatsBroadcastService ..> SensorProcessingService

    SensorEventRepository --> SensorEvent
    SecurityAlertRepository --> SecurityAlert
    AccessLogRepository --> AccessLog
    UserRepository --> User
```

Notas:
- Las flechas sólidas indican dependencias en tiempo de ejecución (inyección/uso directo). Las punteadas señalan dependencias de infraestructura (mensajería/correo).

