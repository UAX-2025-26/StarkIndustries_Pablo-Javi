package com.starkindustries.security.config;

import com.starkindustries.security.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// Configuración principal de Spring Security para la API
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    // Define la cadena de filtros HTTP y las reglas de autorización
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthFilter,
            AuthenticationProvider authenticationProvider
    ) throws Exception {
        http
                // Desactiva CSRF (API stateless con JWT)
                .csrf(AbstractHttpConfigurer::disable)
                // Activa CORS con configuración aparte
                .cors(cors -> {})
                // Reglas de autorización por ruta y método
                .authorizeHttpRequests(auth -> auth
                        // Recursos estáticos públicos
                        .requestMatchers("/", "/index.html", "/favicon.ico",
                                "/static/**", "/assets/**", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        // Endpoints de autenticación, WebSocket y consola H2
                        .requestMatchers("/api/auth/**", "/ws/**", "/h2-console/**").permitAll()
                        // Algunos endpoints de actuator de solo lectura expuestos públicamente
                        .requestMatchers(HttpMethod.GET, "/actuator", "/actuator/health", "/actuator/info").permitAll()
                        // Endpoints de administración y actuator completo sólo para ADMIN
                        .requestMatchers("/api/admin/**", "/actuator/**").hasRole("ADMIN")
                        // Sensores y alertas accesibles para ADMIN y usuarios autorizados
                        .requestMatchers("/api/sensors/**").hasAnyRole("ADMIN", "AUTHORIZED_USER")
                        .requestMatchers("/api/alerts/**").hasAnyRole("ADMIN", "AUTHORIZED_USER")
                        // Cualquier otra petición requiere estar autenticado
                        .anyRequest().authenticated()
                )
                // No se mantiene sesión HTTP: cada request debe traer su JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Provider que usará Security para autenticarse (UserDetailsService + PasswordEncoder)
                .authenticationProvider(authenticationProvider)
                // Inserta el filtro JWT antes del filtro estándar de username/password
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Permite frames de la misma origen (necesario para H2 console)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }

    // AuthenticationProvider basado en DAO, usando UserDetailsService y BCrypt
    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    // Expone el AuthenticationManager configurado por Spring (para AuthenticationService)
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // BCrypt para cifrar contraseñas de usuarios
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Configuración CORS permisiva para permitir llamadas desde el dashboard o herramientas externas
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
