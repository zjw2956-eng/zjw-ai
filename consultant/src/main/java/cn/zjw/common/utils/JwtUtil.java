package cn.zjw.common.utils;

import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

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
                .claim("username",username!=null?username:"")
                .claim("phone",phone!=null?phone:"")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(SignatureAlgorithm.HS256,secretKey)
                .compact();
    }

    /**
     * 解析JWT token,返回userId
     */
    public Long parseUserId(String token) {
        // 实现JWT解析逻辑
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Token is required");
        }
        if (!token.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid token format");
        }else{
            token = token.substring(prefix.length());
        }
        try {
            Claims claims=Jwts.parser()
            .verifyWith(getSecretKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (Exception | ExpiredJwtException e) {
            throw new UnauthorizedException("Invalid token");
        }
    }

    /**解析token，返回负载中的username */
    public String parseUsername(String token){
        if (token == null || token.isBlank()) return null;
        if (!token.startsWith("Bearer ")) {
            token = token.substring(prefix.length());
        }
        try {
            Claims claims=Jwts.parser()
            .verifyWith(getSecretKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
            return claims.get("username",String.class);
        } catch (Exception | ExpiredJwtException e) {
            throw new UnauthorizedException("Invalid token");
        }
    }

    /**解析token，返回负载中的phone */
    public String parsePhone(String token){
        if (token == null || token.isBlank()) return null;
        if (!token.startsWith("Bearer ")) {
            token = token.substring(prefix.length());
        }
        try {
            Claims claims=Jwts.parser()
            .verifyWith(getSecretKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
            return claims.get("phone",String.class);
        } catch (Exception | ExpiredJwtException e) {
            throw new UnauthorizedException("Invalid token");
        }
    }

    public String generateToken(Long userId, String username, String phone) {
        return generateToken(userId, null, null);
    }

    /** 兼容旧接口：解析取 userId */
    public Long parseToken(String token) {
        return parseUserId(token);
    }
}
