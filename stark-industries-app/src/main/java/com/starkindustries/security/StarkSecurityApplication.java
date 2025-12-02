package com.starkindustries.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Punto de entrada de la aplicación Spring Boot
// @SpringBootApplication: activa autoconfiguración, escaneo de componentes e IoC/DI
// @EnableAsync: permite usar métodos @Async para ejecutar tareas en hilos del pool
// @EnableScheduling: habilita tareas programadas con @Scheduled
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class StarkSecurityApplication {

    public static void main(String[] args) {
        // Arranca el contexto de Spring y el servidor embebido (Tomcat)
        SpringApplication.run(StarkSecurityApplication.class, args);
        // Mensaje informativo con las URLs principales para pruebas
        System.out.println("""
            Sistema iniciado correctamente.
            Acceso al dashboard: http://localhost:8080
            Actuator: http://localhost:8080/actuator
            H2 Console: http://localhost:8080/h2-console
            """);
    }
}
