package com.starkindustries.security.repository;

import com.starkindustries.security.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Repositorio de usuarios: encapsula el acceso a la tabla users
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Busca un usuario por su username (login)
    Optional<User> findByUsername(String username);

    // Busca un usuario por su email
    Optional<User> findByEmail(String email);

    // Comprueba si existe un usuario con ese username
    boolean existsByUsername(String username);

    // Comprueba si existe un usuario con ese email
    boolean existsByEmail(String email);
}
