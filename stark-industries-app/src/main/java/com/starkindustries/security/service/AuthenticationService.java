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

// Servicio de autenticación: valida credenciales, genera JWT y registra accesos
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationService {

    // Delegado de Spring Security que ejecuta el proceso de autenticación
    private final AuthenticationManager authenticationManager;
    // Servicio de dominio para gestionar usuarios (bloqueos, intentos, etc.)
    private final UserService userService;
    // Servicio encargado de generar y validar tokens JWT
    private final JwtService jwtService;
    // Servicio para persistir logs de acceso (login/logout)
    private final AccessLogService accessLogService;

    // Autentica al usuario y, si tiene éxito, genera un JWT y registra el acceso
    public AuthenticationResponse authenticate(AuthenticationRequest request, String ipAddress) {
        final String username = request.username();

        try {
            // Se delega la autenticación al AuthenticationManager configurado en SecurityConfiguration
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            request.password()
                    )
            );

            // Obtenemos el principal resultante (UserDetails) tras la autenticación
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            User user;
            if (principal instanceof User) {
                user = (User) principal;
            } else {
                // Si el principal no es nuestra entidad User, la recuperamos desde el servicio
                user = userService.getUserByUsername(principal.getUsername());
            }

            // Generamos un token JWT para el usuario autenticado
            String jwtToken = jwtService.generateToken(principal);
            // Restablecemos el contador de intentos fallidos y registramos último login
            userService.resetFailedAttempts(user);

            // Registro de acceso exitoso
            accessLogService.logAccess(
                    username,
                    ipAddress,
                    AccessLog.AccessType.LOGIN,
                    true,
                    null
            );

            log.info("Autenticación exitosa para usuario: {}", username);

            // Devolvemos DTO con token y datos básicos del usuario
            return AuthenticationResponse.builder()
                    .token(jwtToken)
                    .username(user.getUsername())
                    .roles(user.getRoles())
                    .message("Autenticación exitosa")
                    .build();

        } catch (LockedException e) {
            // Caso en el que la cuenta está bloqueada
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
            // Credenciales incorrectas: se incrementa contador de intentos y se registra
            try {
                User user = userService.getUserByUsername(username);
                userService.increaseFailedAttempts(user);
            } catch (Exception ignored) {}

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
            // Cualquier otro problema de autenticación (por ejemplo, configuración)
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
            // Errores internos no previstos
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

    // Registra un logout exitoso del usuario (no invalida JWT, simplemente deja constancia)
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

    // DTO de entrada para la petición de autenticación (login)
    public record AuthenticationRequest(String username, String password) {}

    // DTO de salida para la respuesta de autenticación (contiene JWT y metadatos básicos)
    @lombok.Builder
    public record AuthenticationResponse(
            String token,
            String username,
            java.util.List<String> roles,
            String message
    ) {}
}
