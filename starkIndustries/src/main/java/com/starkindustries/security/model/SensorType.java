package com.starkindustries.security.model;

// Tipos de sensores
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
