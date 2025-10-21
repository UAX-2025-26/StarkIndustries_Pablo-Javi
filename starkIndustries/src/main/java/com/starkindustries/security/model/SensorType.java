package com.starkindustries.security.model;

/**
 * Enumeración de tipos de sensores soportados por el sistema
 */
public enum SensorType {
    MOTION("Sensor de Movimiento"),
    TEMPERATURE("Sensor de Temperatura"),
    ACCESS("Control de Acceso");

    private final String description;

    SensorType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

