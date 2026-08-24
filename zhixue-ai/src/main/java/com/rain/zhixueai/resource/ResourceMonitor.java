package com.rain.zhixueai.resource;

import com.rain.zhixueai.config.ResourceConfigProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
public class ResourceMonitor {

    private final ResourceConfigProperties config;
    private final ResourceIsolationManager isolationManager;
    private final ToolConcurrencyController concurrencyController;
    private final PriorityToolExecutor toolExecutor;
    private final ConcurrentLinkedQueue<AlertEvent> alertHistory;
    
    private volatile boolean monitoringEnabled = true;

    public ResourceMonitor(ResourceConfigProperties config,
                          ResourceIsolationManager isolationManager,
                          ToolConcurrencyController concurrencyController,
                          PriorityToolExecutor toolExecutor) {
        this.config = config;
        this.isolationManager = isolationManager;
        this.concurrencyController = concurrencyController;
        this.toolExecutor = toolExecutor;
        this.alertHistory = new ConcurrentLinkedQueue<>();
        
        log.info("ResourceMonitor initialized: enabled={}", config.getMonitor().getEnabled());
    }

    @Scheduled(fixedDelayString = "${resource.monitor.check-interval-seconds:10}000")
    public void monitorResources() {
        if (!monitoringEnabled || !config.getMonitor().getEnabled()) {
            return;
        }
        
        try {
            ResourceIsolationManager.SystemResourceStatus systemStatus = 
                isolationManager.getSystemResourceStatus();
            
            checkMemoryUsage(systemStatus);
            checkCpuUsage(systemStatus);
            checkQueueSize();
            checkConcurrency();
            
        } catch (Exception e) {
            log.error("Error during resource monitoring", e);
        }
    }

    private void checkMemoryUsage(ResourceIsolationManager.SystemResourceStatus status) {
        ResourceConfigProperties.MonitorConfig monitorConfig = config.getMonitor();
        double memoryUsage = status.getMemoryUsagePercent();
        
        if (memoryUsage >= monitorConfig.getCriticalThresholdPercent()) {
            AlertEvent alert = new AlertEvent(
                "CRITICAL",
                "MEMORY",
                String.format("Memory usage critical: %.2f%% (threshold: %d%%)",
                    memoryUsage, monitorConfig.getCriticalThresholdPercent()),
                LocalDateTime.now()
            );
            recordAlert(alert);
            log.error("CRITICAL: {}", alert.getMessage());
            
        } else if (memoryUsage >= monitorConfig.getAlertThresholdPercent()) {
            AlertEvent alert = new AlertEvent(
                "WARNING",
                "MEMORY",
                String.format("Memory usage high: %.2f%% (threshold: %d%%)",
                    memoryUsage, monitorConfig.getAlertThresholdPercent()),
                LocalDateTime.now()
            );
            recordAlert(alert);
            log.warn("WARNING: {}", alert.getMessage());
        }
    }

    private void checkCpuUsage(ResourceIsolationManager.SystemResourceStatus status) {
        ResourceConfigProperties.MonitorConfig monitorConfig = config.getMonitor();
        double cpuLoad = status.getSystemCpuLoad();
        
        if (cpuLoad >= monitorConfig.getCriticalThresholdPercent()) {
            AlertEvent alert = new AlertEvent(
                "CRITICAL",
                "CPU",
                String.format("CPU usage critical: %.2f%% (threshold: %d%%)",
                    cpuLoad, monitorConfig.getCriticalThresholdPercent()),
                LocalDateTime.now()
            );
            recordAlert(alert);
            log.error("CRITICAL: {}", alert.getMessage());
            
        } else if (cpuLoad >= monitorConfig.getAlertThresholdPercent()) {
            AlertEvent alert = new AlertEvent(
                "WARNING",
                "CPU",
                String.format("CPU usage high: %.2f%% (threshold: %d%%)",
                    cpuLoad, monitorConfig.getAlertThresholdPercent()),
                LocalDateTime.now()
            );
            recordAlert(alert);
            log.warn("WARNING: {}", alert.getMessage());
        }
    }

    private void checkQueueSize() {
        ResourceConfigProperties.MonitorConfig monitorConfig = config.getMonitor();
        int queueSize = toolExecutor.getQueueSize();
        
        if (queueSize >= monitorConfig.getQueueSizeAlertThreshold()) {
            AlertEvent alert = new AlertEvent(
                "WARNING",
                "QUEUE",
                String.format("Tool execution queue size high: %d (threshold: %d)",
                    queueSize, monitorConfig.getQueueSizeAlertThreshold()),
                LocalDateTime.now()
            );
            recordAlert(alert);
            log.warn("WARNING: {}", alert.getMessage());
        }
    }

    private void checkConcurrency() {
        ToolConcurrencyController.ConcurrencyStatus status = concurrencyController.getStatus();
        
        double usagePercent = (1.0 - (double) status.getGlobalAvailable() / status.getGlobalMax()) * 100;
        
        if (usagePercent >= config.getMonitor().getCriticalThresholdPercent()) {
            AlertEvent alert = new AlertEvent(
                "WARNING",
                "CONCURRENCY",
                String.format("Concurrency usage high: %.2f%% (available: %d/%d)",
                    usagePercent, status.getGlobalAvailable(), status.getGlobalMax()),
                LocalDateTime.now()
            );
            recordAlert(alert);
            log.warn("WARNING: {}", alert.getMessage());
        }
        
        for (Map.Entry<String, Integer> entry : status.getToolActive().entrySet()) {
            String toolName = entry.getKey();
            int active = entry.getValue();
            int limit = config.getConcurrency().getToolLimits().getOrDefault(toolName, 0);
            
            if (limit > 0 && active >= limit * 0.8) {
                AlertEvent alert = new AlertEvent(
                    "INFO",
                    "TOOL_CONCURRENCY",
                    String.format("Tool %s concurrency near limit: %d/%d", toolName, active, limit),
                    LocalDateTime.now()
                );
                recordAlert(alert);
                log.info("INFO: {}", alert.getMessage());
            }
        }
    }

    private void recordAlert(AlertEvent alert) {
        alertHistory.offer(alert);
        
        while (alertHistory.size() > 100) {
            alertHistory.poll();
        }
    }

    public List<AlertEvent> getRecentAlerts(int count) {
        List<AlertEvent> alerts = new ArrayList<>();
        int i = 0;
        for (AlertEvent alert : alertHistory) {
            if (i++ >= count) break;
            alerts.add(alert);
        }
        return alerts;
    }

    public void setMonitoringEnabled(boolean enabled) {
        this.monitoringEnabled = enabled;
        log.info("Resource monitoring {}", enabled ? "enabled" : "disabled");
    }

    @Data
    public static class AlertEvent {
        private final String level;
        private final String type;
        private final String message;
        private final LocalDateTime timestamp;
        
        public AlertEvent(String level, String type, String message, LocalDateTime timestamp) {
            this.level = level;
            this.type = type;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}
