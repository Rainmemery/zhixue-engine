package com.rain.zhixuecommon.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class JwtUtil {

    private static final String DEFAULT_SECRET = "mySecretKeyForZhixueAppWithProperLengthThatMeetsHMACRequirements";
    private static final long DEFAULT_EXPIRATION = 86400000L;
    private static final long DEFAULT_REFRESH_EXPIRATION = 604800000L;

    private final String secret;
    private final long expiration;
    private final long refreshExpiration;
    private final Key signInKey;

    public JwtUtil(
            @Value("${jwt.secret:mySecretKeyForZhixueAppWithProperLengthThatMeetsHMACRequirements}") String secret,
            @Value("${jwt.expiration:86400000}") Long expiration,
            @Value("${jwt.refreshExpiration:604800000}") Long refreshExpiration
    ) {
        this.secret = (secret != null && !secret.trim().isEmpty()) ? secret : DEFAULT_SECRET;
        this.expiration = (expiration != null) ? expiration : DEFAULT_EXPIRATION;
        this.refreshExpiration = (refreshExpiration != null) ? refreshExpiration : DEFAULT_REFRESH_EXPIRATION;
        this.signInKey = buildSignInKey(this.secret);

        log.info("JwtUtil 构造器初始化完成");
        log.info("Secret length: {}", this.secret.length());
        log.info("Expiration: {} ms", this.expiration);
        log.info("Refresh Expiration: {} ms", this.refreshExpiration);
    }

    private Key buildSignInKey(String secretKey) {
        byte[] keyBytes = secretKey.getBytes();
        if (keyBytes.length < 64) {
            StringBuilder sb = new StringBuilder(secretKey);
            while (sb.length() < 64) {
                sb.append(secretKey);
            }
            secretKey = sb.toString();
            keyBytes = secretKey.getBytes();
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Date getExpirationDateFromToken(String token) {
        try {
            return getClaimFromToken(token, Claims::getExpiration);
        } catch (Exception e) {
            log.error("获取令牌过期时间失败", e);
            return null;
        }
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        try {
            final Claims claims = getAllClaimsFromToken(token);
            return claimsResolver.apply(claims);
        } catch (Exception e) {
            log.error("从令牌中获取声明失败", e);
            return null;
        }
    }

    private Claims getAllClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signInKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("解析JWT令牌失败", e);
            throw e;
        }
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration != null && expiration.before(new Date());
    }

    public String generateToken(Long id) {
        try {
            if (id == null) {
                log.error("用户ID为空，无法生成JWT令牌");
                return null;
            }

            Map<String, Object> claims = new HashMap<>();
            return doGenerateToken(claims, id.toString());
        } catch (Exception e) {
            log.error("生成JWT令牌失败", e);
            return null;
        }
    }

    private String doGenerateToken(Map<String, Object> claims, String subject) {
        try {
            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(subject)
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + expiration))
                    .signWith(signInKey, SignatureAlgorithm.HS512)
                    .compact();
        } catch (Exception e) {
            log.error("构建JWT令牌失败", e);
            throw e;
        }
    }

    public String generateRefreshToken(Long id) {
        try {
            if (id == null) {
                log.error("用户ID为空，无法生成刷新令牌");
                return null;
            }

            Map<String, Object> claims = new HashMap<>();
            return doGenerateRefreshToken(claims, id.toString());
        } catch (Exception e) {
            log.error("生成刷新令牌失败", e);
            return null;
        }
    }

    private String doGenerateRefreshToken(Map<String, Object> claims, String subject) {
        try {
            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(subject)
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                    .signWith(signInKey, SignatureAlgorithm.HS512)
                    .compact();
        } catch (Exception e) {
            log.error("构建刷新令牌失败", e);
            throw e;
        }
    }

    public Boolean validateToken(String token, Long id) {
        try {
            final String userId = getUserIdFromToken(token);
            return userId != null && userId.equals(id.toString()) && !isTokenExpired(token);
        } catch (Exception e) {
            log.error("验证JWT令牌失败", e);
            return false;
        }
    }

    public Boolean validateRefreshToken(String refreshToken) {
        try {
            return !isTokenExpired(refreshToken);
        } catch (Exception e) {
            log.error("验证刷新令牌失败", e);
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        try {
            return getClaimFromToken(token, Claims::getSubject);
        } catch (Exception e) {
            log.error("从令牌中获取用户ID失败", e);
            return null;
        }
    }

    public Long getUserIdFromRefreshToken(String refreshToken) {
        try {
            String userIdStr = getClaimFromToken(refreshToken, Claims::getSubject);
            if (userIdStr != null) {
                return Long.parseLong(userIdStr);
            }
            return null;
        } catch (Exception e) {
            log.error("从刷新令牌中获取用户ID失败", e);
            return null;
        }
    }
}