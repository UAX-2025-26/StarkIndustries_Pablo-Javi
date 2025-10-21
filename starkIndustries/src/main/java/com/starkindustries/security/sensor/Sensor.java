package com.starkindustries.security.sensor;

import com.starkindustries.security.model.SensorEvent;

/**
 * Interfaz común para todos los sensores del sistema
 */
public interface Sensor {

    /**
     * Procesa un evento capturado por el sensor
     */
    SensorEvent processEvent(SensorEvent event);

    /**
     * Indica si el valor requiere disparar alerta
     */
    boolean requiresAlert(Double value);

    /**
     * Tipo de sensor como texto (enum name)
     */
    String getSensorType();

    /**
     * Genera un evento simulado
     */
    SensorEvent simulateEvent();
}

