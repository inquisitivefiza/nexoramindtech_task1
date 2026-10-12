package com.example.taskmanager.security;

import com.example.taskmanager.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final JwtParser parser;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes (256 bits) long");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.parser = Jwts.parser().verifyWith(key).build();
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature + expiry and returns the principal.
     * Throws a {@link io.jsonwebtoken.JwtException} (or IllegalArgumentException) when invalid.
     */
    public AuthenticatedUser parseToken(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        return new AuthenticatedUser(Long.parseLong(claims.getSubject()), claims.get("email", String.class));
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}
