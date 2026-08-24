package com.rain.zhixueai.resource;

import com.rain.zhixueai.config.ResourceConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ToolConcurrencyController {

    private final ResourceConfigProperties config;
    private final Semaphore globalSemaphore;
    private final Map<String, Semaphore> toolSemaphores;
    private final Map<Long, AtomicInteger> userActiveTasks;
    private final Map<String, AtomicInteger> toolActiveCounts;

    public ToolConcurrencyController(ResourceConfigProperties config) {
        this.config = config;
        ResourceConfigProperties.ConcurrencyConfig concConfig = config.getConcurrency();
        
        this.globalSemaphore = new Semaphore(concConfig.getMaxConcurrentTools());
        this.toolSemaphores = new ConcurrentHashMap<>();
        this.userActiveTasks = new ConcurrentHashMap<>();
        this.toolActiveCounts = new ConcurrentHashMap<>();
        
        for (Map.Entry<String, Integer> entry : concConfig.getToolLimits().entrySet()) {
            toolSemaphores.put(entry.getKey(), new Semaphore(entry.getValue()));
            toolActiveCounts.put(entry.getKey(), new AtomicInteger(0));
        }
        
        log.info("ToolConcurrencyController initialized: maxGlobal={}, toolLimits={}",
            concConfig.getMaxConcurrentTools(), concConfig.getToolLimits());
    }

    public boolean tryAcquire(String toolName, Long userId) {
        try {
            if (!globalSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                log.warn("Global concurrency limit reached, cannot acquire for tool: {}", toolName);
                return false;
            }
            
            Semaphore toolSemaphore = toolSemaphores.get(toolName);
            if (toolSemaphore != null && !toolSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                globalSemaphore.release();
                log.warn("Tool-specific concurrency limit reached for: {}", toolName);
                return false;
            }
            
            if (userId != null) {
                AtomicInteger userCount = userActiveTasks.computeIfAbsent(userId, k -> new AtomicInteger(0));
                ResourceConfigProperties.ConcurrencyConfig concConfig = config.getConcurrency();
                
                if (userCount.get() >= concConfig.getMaxConcurrentPerUser()) {
                    if (toolSemaphore != null) {
                        toolSemaphore.release();
                    }
                    globalSemaphore.release();
                    log.warn("User {} concurrency limit reached", userId);
                    return false;
                }
                userCount.incrementAndGet();
            }
            
            AtomicInteger toolCount = toolActiveCounts.get(toolName);
            if (toolCount != null) {
                toolCount.incrementAndGet();
            }
            
            log.debug("Acquired concurrency permit: tool={}, userId={}, globalAvailable={}, toolAvailable={}",
                toolName, userId, globalSemaphore.availablePermits(),
                toolSemaphore != null ? toolSemaphore.availablePermits() : "N/A");
            
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring concurrency permit", e);
            return false;
        }
    }

    public void release(String toolName, Long userId) {
        Semaphore toolSemaphore = toolSemaphores.get(toolName);
        if (toolSemaphore != null) {
            toolSemaphore.release();
            AtomicInteger toolCount = toolActiveCounts.get(toolName);
            if (toolCount != null) {
                toolCount.decrementAndGet();
            }
        }
        
        globalSemaphore.release();
        
        if (userId != null) {
            AtomicInteger userCount = userActiveTasks.get(userId);
            if (userCount != null) {
                userCount.decrementAndGet();
            }
        }
        
        log.debug("Released concurrency permit: tool={}, userId={}", toolName, userId);
    }

    public ConcurrencyStatus getStatus() {
        ConcurrencyStatus status = new ConcurrencyStatus();
        status.setGlobalAvailable(globalSemaphore.availablePermits());
        status.setGlobalMax(config.getConcurrency().getMaxConcurrentTools());
        
        Map<String, Integer> toolAvailable = new ConcurrentHashMap<>();
        for (Map.Entry<String, Semaphore> entry : toolSemaphores.entrySet()) {
            toolAvailable.put(entry.getKey(), entry.getValue().availablePermits());
        }
        status.setToolAvailable(toolAvailable);
        
        Map<String, Integer> toolActive = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : toolActiveCounts.entrySet()) {
            toolActive.put(entry.getKey(), entry.getValue().get());
        }
        status.setToolActive(toolActive);
        
        return status;
    }

    public static class ConcurrencyStatus {
        private int globalAvailable;
        private int globalMax;
        private Map<String, Integer> toolAvailable;
        private Map<String, Integer> toolActive;

        public int getGlobalAvailable() {
            return globalAvailable;
        }

        public void setGlobalAvailable(int globalAvailable) {
            this.globalAvailable = globalAvailable;
        }

        public int getGlobalMax() {
            return globalMax;
        }

        public void setGlobalMax(int globalMax) {
            this.globalMax = globalMax;
        }

        public Map<String, Integer> getToolAvailable() {
            return toolAvailable;
        }

        public void setToolAvailable(Map<String, Integer> toolAvailable) {
            this.toolAvailable = toolAvailable;
        }

        public Map<String, Integer> getToolActive() {
            return toolActive;
        }

        public void setToolActive(Map<String, Integer> toolActive) {
            this.toolActive = toolActive;
        }
    }
}
