package com.issuemanagement.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    public static final String DEFAULT_DEV_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${security.jwt.issuer:ai-issue-management}") String issuer,
            @Value("${spring.profiles.active:}") String activeProfiles) {

        boolean isProduction = activeProfiles != null && (
                activeProfiles.contains("prod") || activeProfiles.contains("production")
        );

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must not be blank.");
        }

        if (isProduction) {
            if (DEFAULT_DEV_SECRET.equalsIgnoreCase(secret)) {
                throw new IllegalStateException(
                        "Production security violation: Default local development JWT secret is in use. " +
                        "A secure, randomly generated JWT_SECRET of at least 256 bits must be configured via environment variables.");
            }
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException(
                        "Production security violation: JWT_SECRET must be at least 256 bits (32 bytes) long.");
            }
        }

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    public String generateToken(UserPrincipal principal) {
        String sessionId = principal.getSessionId() != null ? principal.getSessionId() : java.util.UUID.randomUUID().toString();
        return generateToken(principal, sessionId);
    }

    public String generateToken(UserPrincipal principal, String sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("userId", principal.getId())
                .claim("role", principal.getRole().name())
                .claim("sessionId", sessionId)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Object userIdObj = getClaimsFromToken(token).get("userId");
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public String getSessionIdFromToken(String token) {
        Object sessionIdObj = getClaimsFromToken(token).get("sessionId");
        if (sessionIdObj != null) {
            return sessionIdObj.toString();
        }
        // Fallback for tokens issued without explicit sessionId (e.g. legacy/test tokens)
        String username = getUsernameFromToken(token);
        return java.util.UUID.nameUUIDFromBytes(("fallback-session-" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
