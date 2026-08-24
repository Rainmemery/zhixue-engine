package com.rain.zhixuegetaway.gateway;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("网关路由测试")
public class GatewayRouteTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private RouteLocator routeLocator;

    private List<Route> routes;

    @BeforeEach
    void setUp() {
        log.info("开始网关路由测试...");
        routes = new ArrayList<>();
        routeLocator.getRoutes().collectList().block();
    }

    @Test
    @DisplayName("测试网关应用上下文加载")
    void testGatewayApplicationContextLoaded() {
        assertNotNull(applicationContext, "网关应用上下文不应为空");
        log.info("网关应用上下文加载成功");
    }

    @Test
    @DisplayName("测试路由定位器是否正确配置")
    void testRouteLocatorConfigured() {
        assertNotNull(routeLocator, "路由定位器不应为空");
        
        List<Route> routeList = routeLocator.getRoutes().collectList().block();
        assertNotNull(routeList, "路由列表不应为空");
        assertTrue(routeList.size() > 0, "应该存在至少一条路由");
        
        log.info("已配置路由数量: {}", routeList.size());
        
        routeList.forEach(route -> {
            log.info("路由ID: {}, URI: {}, 断言: {}", 
                route.getId(), 
                route.getUri(), 
                route.getPredicate());
        });
    }

    @Test
    @DisplayName("测试AI服务路由配置")
    void testAiServiceRoute() {
        List<Route> routeList = routeLocator.getRoutes().collectList().block();
        
        boolean hasAiRoute = routeList.stream()
            .anyMatch(route -> route.getId().contains("ai-service-route"));
        
        assertTrue(hasAiRoute, "应该存在AI服务路由");
        log.info("AI服务路由配置检查通过");
    }

    @Test
    @DisplayName("测试用户服务路由配置")
    void testUserServiceRoute() {
        List<Route> routeList = routeLocator.getRoutes().collectList().block();
        
        boolean hasUserRoute = routeList.stream()
            .anyMatch(route -> route.getId().contains("user-service-route"));
        
        assertTrue(hasUserRoute, "应该存在用户服务路由");
        log.info("用户服务路由配置检查通过");
    }

    @Test
    @DisplayName("测试学习服务路由配置")
    void testLearningServiceRoute() {
        List<Route> routeList = routeLocator.getRoutes().collectList().block();
        
        boolean hasLearningRoute = routeList.stream()
            .anyMatch(route -> route.getId().contains("learning-service-route"));
        
        assertTrue(hasLearningRoute, "应该存在学习服务路由");
        log.info("学习服务路由配置检查通过");
    }

    @Test
    @DisplayName("测试题目服务路由配置")
    void testProblemServiceRoute() {
        List<Route> routeList = routeLocator.getRoutes().collectList().block();
        
        boolean hasProblemRoute = routeList.stream()
            .anyMatch(route -> route.getId().contains("problem-service-route"));
        
        assertTrue(hasProblemRoute, "应该存在题目服务路由");
        log.info("题目服务路由配置检查通过");
    }

    @Test
    @DisplayName("测试管理服务路由配置")
    void testAdminServiceRoute() {
        List<Route> routeList = routeLocator.getRoutes().collectList().block();
        
        boolean hasAdminRoute = routeList.stream()
            .anyMatch(route -> route.getId().contains("admin-service-route"));
        
        assertTrue(hasAdminRoute, "应该存在管理服务路由");
        log.info("管理服务路由配置检查通过");
    }

    @Test
    @DisplayName("测试RAG服务路由配置")
    void testRagServiceRoute() {
        List<Route> routeList = routeLocator.getRoutes().collectList().block();
        
        boolean hasRagRoute = routeList.stream()
            .anyMatch(route -> route.getId().contains("rag-service-route"));
        
        assertTrue(hasRagRoute, "应该存在RAG服务路由");
        log.info("RAG服务路由配置检查通过");
    }

    @Test
    @DisplayName("测试所有路由URI格式")
    void testRouteUriFormat() {
        List<Route> routeList = routeLocator.getRoutes().collectList().block();
        
        routeList.forEach(route -> {
            String uri = route.getUri().toString();
            assertTrue(
                uri.startsWith("lb://") || uri.startsWith("http://") || uri.startsWith("https://"),
                "路由URI格式应该正确: " + uri
            );
            log.info("路由URI格式验证通过: {} -> {}", route.getId(), uri);
        });
    }

    @Test
    @DisplayName("测试路由断言配置")
    void testRoutePredicates() {
        List<Route> routeList = routeLocator.getRoutes().collectList().block();
        
        routeList.forEach(route -> {
            assertNotNull(route.getPredicate(), "路由断言不应为空");
            log.info("路由断言: {} -> {}", route.getId(), route.getPredicate());
        });
    }

    @Test
    @DisplayName("测试服务发现配置")
    void testServiceDiscoveryConfiguration() {
        String discoveryLocatorEnabled = environment.getProperty(
            "spring.cloud.gateway.discovery.locator.enabled");
        
        assertNotNull(discoveryLocatorEnabled, "服务发现定位器配置不应为空");
        assertEquals("true", discoveryLocatorEnabled, "服务发现定位器应该启用");
        
        log.info("服务发现配置检查通过");
    }
}
