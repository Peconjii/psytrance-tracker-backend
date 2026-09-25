package com.psytrance.psytrance_tracker_backend.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/** Creates and checks the signed login tokens (HMAC-SHA256, valid for 24 hours). */
@Component
public class JwtUtil {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // Throws on startup if the secret is shorter than 32 bytes, instead of failing on the first login
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TOKEN_LIFETIME.toMillis()))
                .signWith(key)
                .compact();
    }

    /** Throws a JwtException if the token is expired, tampered with or not a JWT at all. */
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
