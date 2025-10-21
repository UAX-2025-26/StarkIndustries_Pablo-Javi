package com.starkindustries.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aplicación principal del Sistema de Seguridad de Stark Industries
 *
 * @EnableAsync: Habilita el procesamiento asíncrono para gestión concurrente
 * @EnableScheduling: Permite tareas programadas para simulación de sensores
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class StarkSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarkSecurityApplication.class, args);
        System.out.println("""

            ╔═══════════════════════════════════════════════════════════╗
            ║   STARK INDUSTRIES - ADVANCED SECURITY SYSTEM             ║
            ║   Sistema de Gestión de Sensores Concurrentes v1.0        ║
            ╚═══════════════════════════════════════════════════════════╝

            Sistema iniciado correctamente.
            Acceso al dashboard: http://localhost:8080
            Actuator: http://localhost:8080/actuator
            H2 Console: http://localhost:8080/h2-console
            """);
    }
}

