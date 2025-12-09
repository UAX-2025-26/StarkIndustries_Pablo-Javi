package com.starkindustries.security.config;

import com.starkindustries.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

// Inicializa datos básicos al arrancar la aplicación (usuarios por defecto)
@Configuration // Indica que esta clase contiene definiciones de beans (@Bean) que serán gestionados por el contenedor de Spring
@Slf4j // Anotación de Lombok que genera automáticamente un logger (log) para esta clase
@RequiredArgsConstructor // Anotación de Lombok que genera un constructor con todos los campos final, permitiendo inyección de dependencias por constructor
public class DataInitializer {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    // CommandLineRunner se ejecuta automáticamente al iniciar el contexto de Spring
    @Bean // Registra este método como un bean de Spring que será gestionado por el contenedor
    CommandLineRunner initDefaultAdmin() {
        return args -> {
            // Crear usuario admin si no existe
            try {
                userService.getUserByUsername("admin");
                log.info("Usuario admin ya existe");
            } catch (Exception e) {
                String encoded = passwordEncoder.encode("admin123");
                userService.createUser(
                        "admin",
                        encoded,
                        "admin@starkindustries.com",
                        "Administrador",
                        List.of("ROLE_ADMIN")
                );
                log.info("Usuario admin creado por defecto (admin/admin123)");
            }

            // Crear usuario jarvis (usuario autorizado) si no existe
            try {
                userService.getUserByUsername("jarvis");
                log.info("Usuario jarvis ya existe");
            } catch (Exception e) {
                String encoded = passwordEncoder.encode("jarvis123");
                userService.createUser(
                        "jarvis",
                        encoded,
                        "jarvis@starkindustries.com",
                        "J.A.R.V.I.S.",
                        List.of("ROLE_AUTHORIZED_USER")
                );
                log.info("Usuario jarvis creado por defecto (jarvis/jarvis123)");
            }
        };
    }
}
