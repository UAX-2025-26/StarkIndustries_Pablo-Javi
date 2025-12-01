package com.starkindustries.security.model;

// Tipos de sensores soportados por el sistema, cada uno con una descripción legible
public enum SensorType {
    // Detecta movimiento en zonas concretas (pasillos, entradas, etc.)
    MOTION("Sensor de Movimiento"),
    // Mide temperatura ambiente en distintas ubicaciones críticas
    TEMPERATURE("Sensor de Temperatura"),
    // Controla intentos de acceso (tarjetas, códigos, etc.) a zonas restringidas
    ACCESS("Control de Acceso");

    private final String description;

    SensorType(String description) {
        this.description = description;
    }

    // Devuelve una descripción amigable del tipo de sensor (usada en mensajes y alertas)
    public String getDescription() {
        return description;
    }
}
