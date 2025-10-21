package com.starkindustries.security.service;

import com.starkindustries.security.model.AccessLog;
import com.starkindustries.security.model.User;
import com.starkindustries.security.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Servicio de autenticación que gestiona login, logout y generación de tokens JWT
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;
    private final AccessLogService accessLogService;

    public AuthenticationResponse authenticate(AuthenticationRequest request, String ipAddress) {
        final String username = request.username();

        try {
            // Autenticar credenciales con el AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            request.password()
                    )
            );

            // Construir token y obtener entidad de usuario
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            User user;
            if (principal instanceof User) {
                user = (User) principal;
            } else {
                user = userService.getUserByUsername(principal.getUsername());
            }

            String jwtToken = jwtService.generateToken(principal);
            userService.resetFailedAttempts(user);

            // Registrar acceso exitoso
            accessLogService.logAccess(
                    username,
                    ipAddress,
                    AccessLog.AccessType.LOGIN,
                    true,
                    null
            );

            log.info("Autenticación exitosa para usuario: {}", username);

            return AuthenticationResponse.builder()
                    .token(jwtToken)
                    .username(user.getUsername())
                    .roles(user.getRoles())
                    .message("Autenticación exitosa")
                    .build();

        } catch (LockedException e) {
            // Cuenta bloqueada
            accessLogService.logAccess(
                    username,
                    ipAddress,
                    AccessLog.AccessType.LOGIN,
                    false,
                    "Cuenta bloqueada"
            );
            log.warn("Cuenta bloqueada para usuario: {}", username);
            throw new ResponseStatusException(HttpStatus.LOCKED, "Cuenta bloqueada debido a múltiples intentos fallidos");
        } catch (BadCredentialsException e) {
            // Credenciales inválidas
            try {
                User user = userService.getUserByUsername(username);
                userService.increaseFailedAttempts(user);
            } catch (Exception ignored) {
                // Usuario inexistente u otro error: no se incrementan intentos
            }

            accessLogService.logAccess(
                    username,
                    ipAddress,
                    AccessLog.AccessType.LOGIN,
                    false,
                    "Credenciales inválidas"
            );

            log.warn("Intento de autenticación fallido (credenciales) para usuario: {}", username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        } catch (AuthenticationException e) {
            // Cualquier otro problema de autenticación
            accessLogService.logAccess(
                    username,
                    ipAddress,
                    AccessLog.AccessType.LOGIN,
                    false,
                    "Error de autenticación"
            );
            log.warn("Error de autenticación para usuario {}: {}", username, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        } catch (Exception e) {
            // Error inesperado
            accessLogService.logAccess(
                    username,
                    ipAddress,
                    AccessLog.AccessType.LOGIN,
                    false,
                    "Error interno"
            );
            log.error("Error interno durante autenticación de {}: {}", username, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
        }
    }

    public void logout(String username, String ipAddress) {
        accessLogService.logAccess(
                username,
                ipAddress,
                AccessLog.AccessType.LOGOUT,
                true,
                null
        );
        log.info("Logout registrado para usuario: {}", username);
    }

    // DTOs
    public record AuthenticationRequest(String username, String password) {}

    @lombok.Builder
    public record AuthenticationResponse(
            String token,
            String username,
            java.util.List<String> roles,
            String message
    ) {}
}
