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
@DisplayName("网关超时配置测试")
public class GatewayTimeoutTest {

    @Autowired
    private Environment environment;

    @BeforeEach
    void setUp() {
        log.info("开始网关超时配置测试...");
    }

    @Test
    @DisplayName("测试连接超时配置")
    void testConnectTimeout() {
        String connectTimeout = environment.getProperty(
            "spring.cloud.gateway.httpclient.connect-timeout");
        
        assertNotNull(connectTimeout, "连接超时配置不应为空");
        
        int timeoutMs = Integer.parseInt(connectTimeout);
        assertTrue(timeoutMs > 0, "连接超时应该大于0");
        assertTrue(timeoutMs <= 30000, "连接超时不应该超过30秒");
        
        log.info("连接超时配置: {} ms", timeoutMs);
    }

    @Test
    @DisplayName("测试响应超时配置")
    void testResponseTimeout() {
        String responseTimeout = environment.getProperty(
            "spring.cloud.gateway.httpclient.response-timeout");
        
        assertNotNull(responseTimeout, "响应超时配置不应为空");
        
        log.info("响应超时配置: {}", responseTimeout);
        
        assertTrue(responseTimeout.matches("\\d+[sm]?"), 
            "响应超时格式应该正确");
    }

    @Test
    @DisplayName("测试超时配置合理性")
    void testTimeoutConfigurationReasonable() {
        String connectTimeout = environment.getProperty(
            "spring.cloud.gateway.httpclient.connect-timeout");
        String responseTimeout = environment.getProperty(
            "spring.cloud.gateway.httpclient.response-timeout");
        
        int connectTimeoutMs = Integer.parseInt(connectTimeout);
        
        assertTrue(connectTimeoutMs >= 5000, 
            "连接超时建议至少5秒");
        assertTrue(connectTimeoutMs <= 20000, 
            "连接超时建议不超过20秒");
        
        log.info("超时配置合理性检查通过");
    }

    @Test
    @DisplayName("测试路由级别超时配置")
    void testRouteLevelTimeout() {
        String connectTimeout = environment.getProperty(
            "spring.cloud.gateway.httpclient.connect-timeout");
        
        assertNotNull(connectTimeout, "全局连接超时配置不应为空");
        
        log.info("全局超时配置: {} ms", connectTimeout);
    }

    @Test
    @DisplayName("测试超时配置对慢请求的处理")
    void testSlowRequestHandling() {
        String responseTimeout = environment.getProperty(
            "spring.cloud.gateway.httpclient.response-timeout");
        
        assertNotNull(responseTimeout, "响应超时配置不应为空");
        
        log.info("慢请求处理超时配置: {}", responseTimeout);
        
        assertTrue(responseTimeout.contains("s") || responseTimeout.contains("m"),
            "响应超时应该以秒或分钟为单位");
    }
}
