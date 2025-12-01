package com.starkindustries.security.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Filtro que se ejecuta una vez por request y extrae/valida el JWT de la cabecera Authorization
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    // Se usa ObjectProvider para obtener el UserDetailsService perezosamente
    private final ObjectProvider<UserDetailsService> userDetailsServiceProvider;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   ObjectProvider<UserDetailsService> userDetailsServiceProvider) {
        this.jwtService = jwtService;
        this.userDetailsServiceProvider = userDetailsServiceProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Si no hay cabecera Authorization o no es Bearer, se sigue la cadena sin autenticar
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extrae el token quitando el prefijo "Bearer "
        jwt = authHeader.substring(7);
        // Obtiene el username (subject) del token
        username = jwtService.extractUsername(jwt);

        // Sólo continúa si no hay autenticación previa en el contexto
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetailsService userDetailsService = userDetailsServiceProvider.getObject();
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Si el token es válido, se crea un Authentication y se guarda en el SecurityContext
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        // Continúa con el resto de filtros de la cadena
        filterChain.doFilter(request, response);
    }
}
