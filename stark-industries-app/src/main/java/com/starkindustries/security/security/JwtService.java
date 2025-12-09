package com.starkindustries.security.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// Servicio responsable de construir y validar tokens JWT
@Service // Marca esta clase como un componente de servicio de Spring para que sea detectado automáticamente y registrado en el contexto de Spring
public class JwtService {

    // Secreto configurable via application.yml
    @Value("${security.jwt.secret}") // Inyecta el valor de la propiedad de configuración "security.jwt.secret" desde application.yml
    private String secretKeyProperty;

    // Tiempo de vida del token en milisegundos
    @Value("${security.jwt.expiration:86400000}") // Inyecta el valor de la propiedad de configuración, con valor por defecto "86400000" (24 horas) si no está definida
    private long jwtExpiration;

    // Clave simétrica derivada del secreto
    private SecretKey secretKey;

    // Inicializa la clave una vez cargadas las propiedades de Spring
    @PostConstruct // Indica que este método se ejecutará automáticamente después de que Spring inyecte todas las dependencias y propiedades
    void init() {
        this.secretKey = buildSecretKey(secretKeyProperty);
    }

    // Extrae el username (subject) de un JWT
    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.getSubject());
    }

    // Método genérico para extraer una claim concreta
    public <T> T extractClaim(String token, Function<io.jsonwebtoken.Claims, T> claimsResolver) {
        final io.jsonwebtoken.Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Genera un token simple para un usuario sin claims extra
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    // Genera un token añadiendo claims extra si se desea
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    // Construye el JWT con subject, fechas y firma HMAC-SHA256
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        Date now = new Date();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // Comprueba que el token pertenece al usuario y que no está expirado
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username != null && username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    // Revisa la fecha de expiración
    private boolean isTokenExpired(String token) {
        Date exp = extractClaim(token, io.jsonwebtoken.Claims::getExpiration);
        return exp.before(new Date());
    }

    // Parsea todas las claims del token verificando la firma
    private io.jsonwebtoken.Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Construye una SecretKey robusta a partir del secreto configurado
    private static SecretKey buildSecretKey(String rawSecret) {
        try {
            // Si viene en Base64 y tiene longitud suficiente, se usa directamente
            byte[] maybeB64 = Decoders.BASE64.decode(rawSecret);
            if (maybeB64 != null && maybeB64.length >= 32) {
                return Keys.hmacShaKeyFor(maybeB64);
            }
        } catch (IllegalArgumentException ignored) {}
        // Si no, se deriva una clave usando SHA-256 sobre el texto
        byte[] keyBytes = sha256(rawSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Deriva bytes usando SHA-256, y si no está disponible, rellena/pad a 32 bytes
    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            if (bytes.length >= 32) return bytes;
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, 32));
            return padded;
        }
    }
}
