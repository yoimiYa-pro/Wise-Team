package com.teampm.security;

import com.teampm.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final AppProperties appProperties;
    private final SecretKey accessKey;
    private final SecretKey refreshKey;

    public JwtTokenProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
        String secret = appProperties.getJwt().getSecret();
        if (secret.length() < 32) {
            secret = secret + "0123456789abcdef0123456789ab".substring(0, 32 - secret.length());
        }
        this.accessKey = Keys.hmacShaKeyFor(secret.substring(0, Math.min(secret.length(), 64)).getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor((secret + "-refresh").getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username, String role) {
        long min = appProperties.getJwt().getAccessMinutes();
        Instant exp = Instant.now().plusSeconds(min * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(exp))
                .signWith(accessKey)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        long days = appProperties.getJwt().getRefreshDays();
        Instant exp = Instant.now().plusSeconds(days * 86400L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(exp))
                .signWith(refreshKey)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parser()
                .verifyWith(refreshKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
