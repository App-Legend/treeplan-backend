package com.applegend.seokkeobom.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtProvider {
    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.access-ttl-minutes}") long accessTtlMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET은 32바이트 이상이어야 합니다.");
        }
        if (secret.startsWith("dev-only-secret")) {
            log.warn("개발용 JWT 비밀키를 사용 중입니다. 배포 전 JWT_SECRET 환경변수를 설정하세요.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    public String createAccessToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    /** 유효하면 사용자 ID, 아니면 empty. */
    public Optional<Long> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
