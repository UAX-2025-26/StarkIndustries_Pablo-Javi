package com.starkindustries.security.controller;

import com.starkindustries.security.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Controlador REST para login/logout y comprobación de estado del sistema de autenticación
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    // Endpoint de login: recibe credenciales y devuelve un JWT si son válidas
    @PostMapping("/login")
    public ResponseEntity<AuthenticationService.AuthenticationResponse> login(
            @RequestBody AuthenticationService.AuthenticationRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = httpRequest.getRemoteAddr();
        AuthenticationService.AuthenticationResponse response =
                authenticationService.authenticate(request, ipAddress);
        return ResponseEntity.ok(response);
    }

    // Endpoint de logout: solo registra el evento de cierre de sesión en los logs de acceso
    @PostMapping("/logout") // Define que este método maneja peticiones HTTP POST en la ruta "/api/auth/logout"
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String username = authentication != null ? authentication.getName() : "anonymous";
        authenticationService.logout(username, ipAddress);
        return ResponseEntity.ok().build();
    }

    // Endpoint simple para comprobar que el módulo de autenticación está operativo
    @GetMapping("/status") // Define que este método maneja peticiones HTTP GET en la ruta "/api/auth/status"
    public ResponseEntity<String> status() {
        return ResponseEntity.ok("Sistema de autenticación operativo");
    }
}
