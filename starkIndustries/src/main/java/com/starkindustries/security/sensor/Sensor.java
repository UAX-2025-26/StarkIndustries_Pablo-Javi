package com.starkindustries.security.sensor;

import com.starkindustries.security.model.SensorEvent;

// Contrato básico de un sensor del sistema
public interface Sensor {

    // Procesa un evento (rellena metadatos, decide criticidad, etc.)
    SensorEvent processEvent(SensorEvent event);

    // Indica si un valor concreto debería disparar una alerta
    boolean requiresAlert(Double value);

    // Devuelve el tipo lógico de sensor (coincide con SensorType.name())
    String getSensorType();

    // Genera un evento simulado (usado por el sistema de simulación)
    SensorEvent simulateEvent();
}
