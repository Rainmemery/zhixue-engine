package com.rain.zhixueadmin.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@ActiveProfiles("test")
@DisplayName("服务启动时间测试")
public class ServiceStartupTimeTest {

    private static final long MAX_STARTUP_TIME_MS = 30000;
    private static final long OPTIMAL_STARTUP_TIME_MS = 10000;

    @Test
    @DisplayName("测试服务启动时间是否在合理范围内")
    void testServiceStartupTime() {
        long startTime = System.currentTimeMillis();
        
        String[] args = new String[]{
            "--spring.profiles.active=test",
            "--server.port=0"
        };
        
        ConfigurableApplicationContext context = null;
        try {
            context = SpringApplication.run(
                Class.forName("com.rain.zhixueadmin.ZhixueAdminApplication"),
                args
            );
            
            long endTime = System.currentTimeMillis();
            long startupTime = endTime - startTime;
            
            log.info("服务启动时间: {} ms", startupTime);
            log.info("启动时间: {} 秒", TimeUnit.MILLISECONDS.toSeconds(startupTime));
            
            assertTrue(startupTime < MAX_STARTUP_TIME_MS, 
                String.format("启动时间应小于%d毫秒，实际为%d毫秒", MAX_STARTUP_TIME_MS, startupTime));
            
            if (startupTime < OPTIMAL_STARTUP_TIME_MS) {
                log.info("✓ 服务启动性能优秀");
            } else {
                log.warn("⚠ 服务启动时间较长，建议优化");
            }
            
        } catch (ClassNotFoundException e) {
            fail("找不到应用主类: " + e.getMessage());
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    @Test
    @DisplayName("测试服务启动各阶段耗时")
    void testStartupPhaseTiming() {
        long startTime = System.currentTimeMillis();
        
        String[] args = new String[]{
            "--spring.profiles.active=test",
            "--server.port=0"
        };
        
        ConfigurableApplicationContext context = null;
        try {
            long beforeSpringInit = System.currentTimeMillis();
            
            context = SpringApplication.run(
                Class.forName("com.rain.zhixueadmin.ZhixueAdminApplication"),
                args
            );
            
            long afterSpringInit = System.currentTimeMillis();
            
            long springInitTime = afterSpringInit - beforeSpringInit;
            
            log.info("=== 启动阶段耗时分析 ===");
            log.info("Spring初始化耗时: {} ms", springInitTime);
            log.info("总启动时间: {} ms", afterSpringInit - startTime);
            
            assertTrue(springInitTime < MAX_STARTUP_TIME_MS,
                "Spring初始化时间过长");
                
        } catch (ClassNotFoundException e) {
            fail("找不到应用主类: " + e.getMessage());
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    @Test
    @DisplayName("测试并发启动性能")
    void testConcurrentStartupPerformance() {
        int concurrentCount = 3;
        long startTime = System.currentTimeMillis();
        
        Thread[] threads = new Thread[concurrentCount];
        boolean[] results = new boolean[concurrentCount];
        
        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    String[] args = new String[]{
                        "--spring.profiles.active=test",
                        "--server.port=0"
                    };
                    
                    ConfigurableApplicationContext context = SpringApplication.run(
                        Class.forName("com.rain.zhixueadmin.ZhixueAdminApplication"),
                        args
                    );
                    
                    Thread.sleep(1000);
                    context.close();
                    results[index] = true;
                    log.info("并发测试 {} 完成", index + 1);
                } catch (Exception e) {
                    log.error("并发测试 {} 失败: {}", index + 1, e.getMessage());
                    results[index] = false;
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            try {
                thread.join(60000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        log.info("并发启动测试总耗时: {} ms", totalTime);
        
        int successCount = 0;
        for (boolean result : results) {
            if (result) successCount++;
        }
        
        assertTrue(successCount >= concurrentCount / 2,
            "至少一半的并发启动应该成功");
    }
}
