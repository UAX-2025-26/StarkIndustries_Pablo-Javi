package com.starkindustries.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class StarkSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarkSecurityApplication.class, args);
        System.out.println("""
            Sistema iniciado correctamente.
            Acceso al dashboard: http://localhost:8080
            Actuator: http://localhost:8080/actuator
            H2 Console: http://localhost:8080/h2-console
            """);
    }
}
