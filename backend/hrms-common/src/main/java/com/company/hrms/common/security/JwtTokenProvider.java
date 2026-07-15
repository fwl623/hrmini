package com.company.hrms.common.security;

import com.company.hrms.common.enums.DataScopeType;
import com.company.hrms.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT Access Token 签发与解析。
 * <p>
 * Token 内含 userId/roles/dataScope 等声明；<b>permissions 不写入 JWT</b>，
 * 由 auth 模块在 JwtAuthFilter 中从 Redis/DB 加载并填入 {@link LoginUser}。
 * Refresh Token / 黑名单同样由 auth + Redis 完成。
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessExpireSeconds;

    public JwtTokenProvider(
            @Value("${hrms.jwt.secret}") String secret,
            @Value("${hrms.jwt.access-expire-seconds:7200}") long accessExpireSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("hrms.jwt.secret must be configured");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("hrms.jwt.secret must be at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpireSeconds = accessExpireSeconds;
    }

    public String createAccessToken(LoginUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessExpireSeconds);
        DataScopeType scope = user.getDataScope() == null ? DataScopeType.SELF : user.getDataScope();
        return Jwts.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("employeeId", user.getEmployeeId())
                .claim("deptId", user.getDeptId())
                .claim("dataScope", scope.name())
                .claim("roles", user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException | MalformedJwtException | SecurityException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Token 无效或已过期");
        }
    }

    @SuppressWarnings("unchecked")
    public LoginUser toLoginUser(Claims claims) {
        LoginUser user = new LoginUser();
        user.setUserId(Long.valueOf(claims.getSubject()));
        user.setUsername(claims.get("username", String.class));
        Object employeeId = claims.get("employeeId");
        if (employeeId instanceof Number n) {
            user.setEmployeeId(n.longValue());
        }
        Object deptId = claims.get("deptId");
        if (deptId instanceof Number n) {
            user.setDeptId(n.longValue());
        }
        user.setDataScope(DataScopeType.from(claims.get("dataScope", String.class)));
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            user.setRoles((List<String>) list);
        }
        return user;
    }

    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    public long getAccessExpireSeconds() {
        return accessExpireSeconds;
    }
}
