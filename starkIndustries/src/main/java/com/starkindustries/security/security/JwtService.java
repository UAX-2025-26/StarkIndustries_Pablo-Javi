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

/**
 * Servicio para gestión de tokens JWT (JJWT 0.12.x)
 */
@Service
public class JwtService {

    @Value("${security.jwt.secret}")
    private String secretKeyProperty;

    @Value("${security.jwt.expiration:86400000}") // 24h por defecto
    private long jwtExpiration;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        this.secretKey = buildSecretKey(secretKeyProperty);
    }

    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.getSubject());
    }

    public <T> T extractClaim(String token, Function<io.jsonwebtoken.Claims, T> claimsResolver) {
        final io.jsonwebtoken.Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

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

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username != null && username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date exp = extractClaim(token, io.jsonwebtoken.Claims::getExpiration);
        return exp.before(new Date());
    }

    private io.jsonwebtoken.Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static SecretKey buildSecretKey(String rawSecret) {
        // Intentar Base64, si falla usar SHA-256 para obtener 32 bytes y construir la clave
        try {
            byte[] maybeB64 = Decoders.BASE64.decode(rawSecret);
            if (maybeB64 != null && maybeB64.length >= 32) {
                return Keys.hmacShaKeyFor(maybeB64);
            }
        } catch (IllegalArgumentException ignored) {
            // No era Base64, continuamos
        }
        // Derivar clave de 256 bits desde el secreto textual (robusto ante secretos cortos)
        byte[] keyBytes = sha256(rawSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Fallback improbable, pero garantizamos longitud mínima
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            if (bytes.length >= 32) return bytes;
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, 32));
            return padded;
        }
    }
}

