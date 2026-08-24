package com.rain.zhixueadmin.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("服务启动测试")
public class ServiceStartupTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        log.info("开始服务启动测试...");
    }

    @Test
    @DisplayName("测试应用上下文是否正常加载")
    void testApplicationContextLoaded() {
        assertNotNull(applicationContext, "应用上下文不应为空");
        log.info("应用上下文加载成功");
    }

    @Test
    @DisplayName("测试所有必需的Bean是否正确注入")
    void testRequiredBeansInjected() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        assertNotNull(beanNames, "Bean名称数组不应为空");
        assertTrue(beanNames.length > 0, "应该存在至少一个Bean");
        
        log.info("已加载的Bean数量: {}", beanNames.length);
        
        String[] requiredBeans = {
            "adminUserService",
            "adminRoleService",
            "systemLogService",
            "backupService",
            "monitoringService"
        };
        
        for (String beanName : requiredBeans) {
            assertTrue(
                Arrays.stream(beanNames).anyMatch(name -> name.contains(beanName)),
                "必需的Bean应该存在: " + beanName
            );
            log.info("Bean已注入: {}", beanName);
        }
    }

    @Test
    @DisplayName("测试数据源配置是否正确")
    void testDataSourceConfiguration() {
        assertNotNull(dataSource, "数据源不应为空");
        
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "数据库连接不应为空");
            assertFalse(connection.isClosed(), "数据库连接应该是打开状态");
            log.info("数据库连接成功: {}", connection.getMetaData().getURL());
        } catch (Exception e) {
            fail("数据库连接失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试应用配置是否正确加载")
    void testApplicationConfiguration() {
        String applicationName = environment.getProperty("spring.application.name");
        assertNotNull(applicationName, "应用名称不应为空");
        assertEquals("zhixue-admin", applicationName, "应用名称应该是zhixue-admin");
        
        String serverPort = environment.getProperty("server.port");
        assertNotNull(serverPort, "服务器端口不应为空");
        log.info("应用配置加载成功 - 应用名: {}, 端口: {}", applicationName, serverPort);
    }

    @Test
    @DisplayName("测试服务健康状态")
    void testServiceHealthStatus() {
        assertDoesNotThrow(() -> {
            Object healthIndicator = applicationContext.getBean("healthIndicator");
            assertNotNull(healthIndicator, "健康检查指示器不应为空");
        }, "健康检查组件应该存在");
        
        log.info("服务健康状态检查通过");
    }

    @Test
    @DisplayName("测试Nacos服务注册配置")
    void testNacosConfiguration() {
        String nacosServer = environment.getProperty("spring.cloud.nacos.discovery.server-addr");
        assertNotNull(nacosServer, "Nacos服务器地址不应为空");
        
        log.info("Nacos配置检查通过 - 服务器地址: {}", nacosServer);
    }

    @Test
    @DisplayName("测试MyBatis配置")
    void testMyBatisConfiguration() {
        String mapperLocations = environment.getProperty("mybatis.mapper-locations");
        assertNotNull(mapperLocations, "Mapper位置配置不应为空");
        
        log.info("MyBatis配置检查通过 - Mapper位置: {}", mapperLocations);
    }
}
