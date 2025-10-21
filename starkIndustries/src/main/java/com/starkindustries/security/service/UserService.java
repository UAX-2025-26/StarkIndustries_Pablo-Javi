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

/**
 * Servicio de gestión de usuarios implementando UserDetailsService
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCK_TIME_DURATION = 5 * 60 * 1000; // 5 minutos

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        if (!Boolean.TRUE.equals(user.getAccountNonLocked())) {
            if (unlockWhenTimeExpired(user)) {
                log.info("Cuenta desbloqueada automáticamente: {}", username);
            } else {
                throw new LockedException("Cuenta bloqueada debido a múltiples intentos fallidos");
            }
        }

        return user;
    }

    /**
     * Crea un usuario. La contraseña debe venir ya codificada.
     */
    public User createUser(String username, String encodedPassword, String email, String fullName, List<String> roles) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("El usuario ya existe");
        }

        User user = User.builder()
                .username(username)
                .password(encodedPassword) // se espera contraseña ENCODED
                .email(email)
                .fullName(fullName)
                .roles(roles)
                .enabled(true)
                .accountNonLocked(true)
                .failedAttempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);
        log.info("Usuario creado exitosamente: {}", username);
        return user;
    }

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

    public void resetFailedAttempts(User user) {
        user.setFailedAttempts(0);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }

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

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
