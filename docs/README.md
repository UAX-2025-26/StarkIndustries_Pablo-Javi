# Documentación - Stark Industries Security System

Esta carpeta contiene toda la documentación técnica del proyecto de monitorización de sensores con procesamiento concurrente en tiempo real.

## Contenido

### Documentos principales

- **EXPLICACION.md**: Descripción detallada de cada componente de la arquitectura, decisiones de diseño y funcionamiento del sistema.
- **TEORIA_APLICADA.md**: Conceptos teóricos de programación concurrente aplicados en este proyecto (Tema 1).

## Diagramas UML

### 1. Diagrama de Clases (`clases-stark-industries.mmd`)
Muestra la estructura completa de clases del proyecto, incluyendo:
- **Paquete model**: Entidades de dominio (User, SensorEvent, SecurityAlert, AccessLog, SensorType)
- **Paquete sensor**: Interfaces y implementaciones de sensores (Temperature, Motion, AccessControl)
- **Paquete repository**: Interfaces JPA para persistencia
- **Paquete service**: Lógica de negocio y procesamiento (SensorProcessingService, AlertService, etc.)
- **Paquete config**: Configuración de async, WebSocket y seguridad
- **Paquete controller**: Endpoints REST y WebSocket

**Características**:
- Todas las relaciones incluyen etiquetas descriptivas (implementa, usa, gestiona, etc.)
- Diferencia clara entre interfaces y clases concretas
- Muestra dependencias con framework Spring y Java estándar

### 2. Diagrama de Secuencia (`secuencia-stark-industries.mmd`)
Ilustra el flujo completo de procesamiento de un evento de sensor:
1. Recepción del evento vía HTTP POST
2. Procesamiento asíncrono en pool de threads (@Async)
3. Delegación a implementación específica de Sensor
4. Persistencia en base de datos (H2)
5. Actualización de estadísticas concurrentes (ConcurrentHashMap + AtomicLong)
6. Notificación por WebSocket a clientes suscritos
7. Creación de alertas para eventos críticos
8. Notificación de alertas

### 3. Diagrama de Actividad (`actividad-stark-industries.mmd`)
Representa el flujo de actividades incluyendo:
- Procesamiento principal de eventos
- Simulación periódica de eventos (@Scheduled cada 5 segundos)
- Broadcasting de estadísticas (@Scheduled cada 10 segundos)
- Decisión sobre generación de alertas para eventos críticos
- Subprocesos paralelos ejecutándose concurrentemente

## Visualización de diagramas

### GitHub
Los archivos `.mmd` se visualizan automáticamente en GitHub al navegar por el repositorio.

### Editores locales
- **VS Code**: Instalar extensión "Markdown Preview Mermaid Support" o "Mermaid Preview"
- **IntelliJ IDEA**: Instalar plugin "Mermaid" desde Settings → Plugins

### Online
Copia el contenido de cualquier archivo `.mmd` y pégalo en:
- [Mermaid Live Editor](https://mermaid.live/)
- [Mermaid Chart](https://www.mermaidchart.com/)

## Conceptos de concurrencia reflejados

Estos diagramas ilustran los siguientes conceptos de programación concurrente aplicados:

1. **Procesamiento asíncrono**: Uso de `@Async` y `CompletableFuture` para no bloquear hilos
2. **Thread pools**: Executors configurados para diferentes tipos de tareas (sensores, alertas, notificaciones)
3. **Estructuras thread-safe**: `ConcurrentHashMap` y `AtomicLong` para contadores sin locks
4. **Comunicación asíncrona**: WebSocket (STOMP) para actualizaciones en tiempo real
5. **Persistencia concurrente**: Acceso a BD mediante repositorios JPA thread-safe
6. **Tareas programadas**: `@Scheduled` para simulación y broadcasting periódicos
7. **Patrón Strategy**: Interfaz Sensor con múltiples implementaciones
8. **Separación de concerns**: Diferentes thread pools para diferentes responsabilidades

## Arquitectura del sistema

```
Hardware/Simulador
    ↓ (HTTP POST)
SensorController
    ↓ (@Async)
SensorProcessingService (pool: sensorExecutor)
    ├─→ Sensor específico (Temperature/Motion/AccessControl)
    ├─→ SensorEventRepository (persistencia)
    ├─→ Actualización de contadores (ConcurrentHashMap)
    ├─→ WebSocket: /topic/stats, /topic/sensors/{tipo}
    └─→ Si crítico:
        └─→ AlertService (pool: alertExecutor)
            ├─→ SecurityAlertRepository
            └─→ NotificationService (pool: notificationExecutor)
                └─→ WebSocket: /topic/alerts
```

## Endpoints principales

### REST
- `POST /api/sensors/event` - Recibir evento de sensor
- `GET /api/sensors/simulate` - Simular evento aleatorio
- `GET /api/alerts` - Consultar alertas pendientes
- `POST /api/alerts/{id}/acknowledge` - Reconocer alerta

### WebSocket (STOMP)
- `/topic/stats` - Estadísticas globales
- `/topic/sensors/{tipo}` - Eventos por tipo de sensor
- `/topic/alerts` - Alertas de seguridad

## Configuración de thread pools

Definidos en `AsyncConfiguration.java`:

1. **sensorExecutor**: 
   - Core: 5 hilos
   - Max: 10 hilos
   - Queue: 50 tareas
   - Procesamiento de eventos de sensores

2. **alertExecutor**:
   - Core: 3 hilos
   - Max: 5 hilos
   - Queue: 20 tareas
   - Creación de alertas

3. **notificationExecutor**:
   - Core: 3 hilos
   - Max: 5 hilos
   - Queue: 30 tareas
   - Envío de notificaciones

## Actualización de diagramas

Si modificas la arquitectura del proyecto:
1. Edita el archivo `.mmd` apropiado usando sintaxis Mermaid
2. Verifica la sintaxis en [Mermaid Live Editor](https://mermaid.live/)
3. Asegúrate de añadir etiquetas descriptivas a las relaciones nuevas
4. Actualiza este README si añades nuevos diagramas
5. Documenta los cambios en el commit

## Referencias

- [Documentación de Mermaid](https://mermaid.js.org/)
- [Sintaxis de diagramas de clases](https://mermaid.js.org/syntax/classDiagram.html)
- [Sintaxis de diagramas de secuencia](https://mermaid.js.org/syntax/sequenceDiagram.html)
- [Sintaxis de flowcharts](https://mermaid.js.org/syntax/flowchart.html)

