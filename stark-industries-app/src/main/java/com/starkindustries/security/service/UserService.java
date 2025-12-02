package com.starkindustries.security.service;

import com.starkindustries.security.model.User;
import com.starkindustries.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// Gestión básica de usuarios e integración con UserDetailsService (usado por Spring Security)
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    // Intentos máximos antes de bloquear la cuenta
    private static final int MAX_FAILED_ATTEMPTS = 3;
    // Duración del bloqueo en milisegundos (5 minutos)
    private static final long LOCK_TIME_DURATION = 5 * 60 * 1000;

    // Carga el usuario desde BD para el proceso de autenticación
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // Si la cuenta está bloqueada, comprobamos si ya ha expirado el tiempo de bloqueo
        if (!Boolean.TRUE.equals(user.getAccountNonLocked())) {
            if (unlockWhenTimeExpired(user)) {
                log.info("Cuenta desbloqueada automáticamente: {}", username);
            } else {
                throw new LockedException("Cuenta bloqueada debido a múltiples intentos fallidos");
            }
        }

        return user;
    }

    // Crea un nuevo usuario con contraseña ya codificada y la lista de roles indicada
    public User createUser(String username, String encodedPassword, String email, String fullName, List<String> roles) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("El usuario ya existe");
        }

        User user = User.builder()
                .username(username)
                .password(encodedPassword)
                .email(email)
                .fullName(fullName)
                .roles(roles)
                .enabled(true)
                .accountNonLocked(true)
                .failedAttempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);
        log.info("Usuario creado: {}", username);
        return user;
    }

    // Incrementa el número de intentos fallidos y bloquea si se supera el máximo permitido
    public void increaseFailedAttempts(User user) {
        int newFailAttempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(newFailAttempts);

        if (newFailAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setAccountNonLocked(false);
            user.setLockTime(LocalDateTime.now());
            log.warn("Cuenta bloqueada por intentos fallidos: {}", user.getUsername());
        }

        userRepository.save(user);
    }

    // Restablece el contador de intentos fallidos y actualiza la fecha de último login
    public void resetFailedAttempts(User user) {
        user.setFailedAttempts(0);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }

    // Comprueba si ha expirado el tiempo de bloqueo y, si es así, desbloquea la cuenta
    private boolean unlockWhenTimeExpired(User user) {
        LocalDateTime lockTime = user.getLockTime();
        if (lockTime == null) {
            return false;
        }
        long lockTimeInMillis = lockTime
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long currentTimeInMillis = System.currentTimeMillis();
        if (lockTimeInMillis + LOCK_TIME_DURATION < currentTimeInMillis) {
            user.setAccountNonLocked(true);
            user.setLockTime(null);
            user.setFailedAttempts(0);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    // Devuelve todos los usuarios existentes
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Obtiene un usuario por username o lanza excepción si no existe
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
