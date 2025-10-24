package com.starkindustries.security.controller;

import com.starkindustries.security.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Autenticación: login, logout y estado
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

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

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String username = authentication != null ? authentication.getName() : "anonymous";
        authenticationService.logout(username, ipAddress);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok("Sistema de autenticación operativo");
    }
}
