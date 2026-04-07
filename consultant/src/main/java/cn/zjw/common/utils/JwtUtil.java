package cn.zjw.common.utils;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cn.zjw.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expire-hours}")
    private long expireHours;


    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT token
     */
    public String generateToken(Long userId) {
        // 实现JWT生成逻辑
        Date now =new Date();
        Date expiry =new Date(now.getTime()+expireHours*60*60*1000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 解析JWT token,返回userId
     */
    public Long parseUserId(String token) {
        // 实现JWT解析逻辑
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException(401, "Token is required");
        }
        try {
            Claims claims=Jwts.parser()
            .verifyWith(getSecretKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            log.error("Token解析失败", e);
            throw new UnauthorizedException(401, "Invalid token");
        }
    }
}
