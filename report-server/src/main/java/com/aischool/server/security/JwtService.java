package com.aischool.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(@Value("${aischool.jwt.secret}") String secret,
                      @Value("${aischool.jwt.expire-hours}") int expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public String issue(Long userId, String username, String realName, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("realName", realName)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 解析并校验；无效/过期返回 null */
    public UserPrincipal parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return new UserPrincipal(Long.parseLong(c.getSubject()),
                    c.get("username", String.class),
                    c.get("realName", String.class),
                    c.get("role", String.class));
        } catch (Exception e) {
            return null;
        }
    }
}
