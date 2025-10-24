package com.starkindustries.security.sensor;

import com.starkindustries.security.model.SensorEvent;

// Contrato básico de un sensor del sistema
public interface Sensor {

    SensorEvent processEvent(SensorEvent event);

    boolean requiresAlert(Double value);

    String getSensorType();

    SensorEvent simulateEvent();
}
