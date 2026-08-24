package com.rain.zhixueai.resource;

import com.rain.zhixueai.config.ResourceConfigProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class ResourceIsolationManager {

    private final ResourceConfigProperties config;
    private final Map<String, IsolationContext> isolationContexts;
    private final MemoryMXBean memoryMXBean;
    private final OperatingSystemMXBean osMXBean;

    public ResourceIsolationManager(ResourceConfigProperties config) {
        this.config = config;
        this.isolationContexts = new ConcurrentHashMap<>();
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
        
        log.info("ResourceIsolationManager initialized: enabled={}", config.getIsolation().getEnabled());
    }

    public IsolationContext createIsolationContext(String toolName, Long userId) {
        if (!config.getIsolation().getEnabled()) {
            return new IsolationContext(toolName, userId, null);
        }
        
        ResourceConfigProperties.ResourceQuota quota = config.getIsolation()
            .getToolQuotas().getOrDefault(toolName, createDefaultQuota());
        
        IsolationContext context = new IsolationContext(toolName, userId, quota);
        context.setStartTime(System.currentTimeMillis());
        context.setStartMemory(getUsedMemoryMB());
        
        isolationContexts.put(context.getContextId(), context);
        
        log.debug("Created isolation context: tool={}, userId={}, quota={}", 
            toolName, userId, quota);
        
        return context;
    }

    public boolean checkResourceQuota(IsolationContext context) {
        if (context == null || context.getQuota() == null) {
            return true;
        }
        
        ResourceConfigProperties.ResourceQuota quota = context.getQuota();
        
        long currentMemory = getUsedMemoryMB();
        long memoryUsed = currentMemory - context.getStartMemory();
        
        if (quota.getMaxMemoryMB() != null && memoryUsed > quota.getMaxMemoryMB()) {
            log.warn("Memory quota exceeded for tool {}: used {}MB > limit {}MB",
                context.getToolName(), memoryUsed, quota.getMaxMemoryMB());
            return false;
        }
        
        return true;
    }

    public void releaseIsolationContext(IsolationContext context) {
        if (context == null) {
            return;
        }
        
        long duration = System.currentTimeMillis() - context.getStartTime();
        long memoryUsed = getUsedMemoryMB() - context.getStartMemory();
        
        context.setEndTime(System.currentTimeMillis());
        context.setEndMemory(getUsedMemoryMB());
        
        isolationContexts.remove(context.getContextId());
        
        log.debug("Released isolation context: tool={}, duration={}ms, memoryUsed={}MB",
            context.getToolName(), duration, memoryUsed);
    }

    public SystemResourceStatus getSystemResourceStatus() {
        SystemResourceStatus status = new SystemResourceStatus();
        
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        
        status.setMaxMemoryMB(maxMemory);
        status.setTotalMemoryMB(totalMemory);
        status.setUsedMemoryMB(usedMemory);
        status.setFreeMemoryMB(freeMemory);
        status.setMemoryUsagePercent((double) usedMemory / maxMemory * 100);
        
        status.setAvailableProcessors(Runtime.getRuntime().availableProcessors());
        
        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = 
                (com.sun.management.OperatingSystemMXBean) osMXBean;
            status.setSystemCpuLoad(sunOsBean.getSystemCpuLoad() * 100);
            status.setProcessCpuLoad(sunOsBean.getProcessCpuLoad() * 100);
        }
        
        status.setActiveIsolationContexts(isolationContexts.size());
        
        return status;
    }

    private long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private ResourceConfigProperties.ResourceQuota createDefaultQuota() {
        ResourceConfigProperties.ResourceQuota quota = new ResourceConfigProperties.ResourceQuota();
        quota.setMaxMemoryMB(config.getIsolation().getDefaultMaxMemoryMB());
        quota.setMaxCpuPercent(config.getIsolation().getDefaultMaxCpuPercent());
        return quota;
    }

    @Data
    public static class IsolationContext {
        private final String contextId;
        private final String toolName;
        private final Long userId;
        private final ResourceConfigProperties.ResourceQuota quota;
        private long startTime;
        private long endTime;
        private long startMemory;
        private long endMemory;
        
        public IsolationContext(String toolName, Long userId, ResourceConfigProperties.ResourceQuota quota) {
            this.contextId = toolName + "-" + userId + "-" + System.currentTimeMillis();
            this.toolName = toolName;
            this.userId = userId;
            this.quota = quota;
        }
    }

    @Data
    public static class SystemResourceStatus {
        private long maxMemoryMB;
        private long totalMemoryMB;
        private long usedMemoryMB;
        private long freeMemoryMB;
        private double memoryUsagePercent;
        private int availableProcessors;
        private double systemCpuLoad;
        private double processCpuLoad;
        private int activeIsolationContexts;
    }
}
