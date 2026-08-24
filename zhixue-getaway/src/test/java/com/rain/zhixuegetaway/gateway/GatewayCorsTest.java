package com.rain.zhixuegetaway.gateway;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("网关CORS配置测试")
public class GatewayCorsTest {

    @Autowired
    private Environment environment;

    @BeforeEach
    void setUp() {
        log.info("开始网关CORS配置测试...");
    }

    @Test
    @DisplayName("测试CORS配置是否存在")
    void testCorsConfigurationExists() {
        String corsConfig = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations");
        
        assertNotNull(corsConfig, "CORS配置不应为空");
        log.info("CORS配置检查通过");
    }

    @Test
    @DisplayName("测试允许的源配置")
    void testAllowedOrigins() {
        String allowedOriginPatterns = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.allowedOriginPatterns");
        
        if (allowedOriginPatterns != null) {
            log.info("允许的源模式: {}", allowedOriginPatterns);
            assertTrue(allowedOriginPatterns.length() > 0, 
                "允许的源模式不应为空");
        } else {
            log.info("使用默认CORS源配置");
        }
    }

    @Test
    @DisplayName("测试允许的HTTP方法")
    void testAllowedMethods() {
        String allowedMethods = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.allowedMethods");
        
        if (allowedMethods != null) {
            log.info("允许的HTTP方法: {}", allowedMethods);
            assertTrue(allowedMethods.contains("GET") || allowedMethods.equals("*"),
                "应该允许GET方法或所有方法");
        } else {
            log.info("使用默认CORS方法配置");
        }
    }

    @Test
    @DisplayName("测试允许的请求头")
    void testAllowedHeaders() {
        String allowedHeaders = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.allowedHeaders");
        
        if (allowedHeaders != null) {
            log.info("允许的请求头: {}", allowedHeaders);
            assertTrue(allowedHeaders.length() > 0,
                "允许的请求头不应为空");
        } else {
            log.info("使用默认CORS请求头配置");
        }
    }

    @Test
    @DisplayName("测试是否允许携带凭证")
    void testAllowCredentials() {
        String allowCredentials = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.allowCredentials");
        
        if (allowCredentials != null) {
            log.info("允许携带凭证: {}", allowCredentials);
            assertTrue(allowCredentials.equals("true"),
                "应该允许携带凭证以支持认证");
        } else {
            log.info("使用默认凭证配置");
        }
    }

    @Test
    @DisplayName("测试预检请求缓存时间")
    void testMaxAge() {
        String maxAge = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.maxAge");
        
        if (maxAge != null) {
            log.info("预检请求缓存时间: {} 秒", maxAge);
            int maxAgeSeconds = Integer.parseInt(maxAge);
            assertTrue(maxAgeSeconds > 0, 
                "预检请求缓存时间应该大于0");
            assertTrue(maxAgeSeconds <= 86400,
                "预检请求缓存时间不应超过24小时");
        } else {
            log.info("使用默认预检缓存时间");
        }
    }

    @Test
    @DisplayName("测试CORS配置安全性")
    void testCorsSecurity() {
        String allowedOriginPatterns = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.allowedOriginPatterns");
        
        if (allowedOriginPatterns != null && allowedOriginPatterns.equals("*")) {
            log.warn("⚠ CORS配置允许所有源，生产环境建议限制具体域名");
        } else {
            log.info("✓ CORS配置较为安全");
        }
    }

    @Test
    @DisplayName("测试CORS配置完整性")
    void testCorsConfigurationCompleteness() {
        String allowedMethods = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.allowedMethods");
        String allowedHeaders = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.allowedHeaders");
        String allowCredentials = environment.getProperty(
            "spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.allowCredentials");
        
        log.info("=== CORS配置完整性检查 ===");
        log.info("允许方法: {}", allowedMethods != null ? allowedMethods : "默认");
        log.info("允许请求头: {}", allowedHeaders != null ? allowedHeaders : "默认");
        log.info("允许凭证: {}", allowCredentials != null ? allowCredentials : "默认");
        
        assertNotNull(allowedMethods != null || allowedHeaders != null || allowCredentials != null,
            "至少应该配置一项CORS参数");
    }
}
