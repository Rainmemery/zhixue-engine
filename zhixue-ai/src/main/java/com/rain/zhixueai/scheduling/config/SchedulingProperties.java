package com.rain.zhixueai.scheduling.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "scheduling")
public class SchedulingProperties {
    
    private HealthCheck healthCheck = new HealthCheck();
    
    private Failover failover = new Failover();
    
    private TaskAnalyzer taskAnalyzer = new TaskAnalyzer();
    
    @Data
    public static class HealthCheck {
        private long intervalMs = 30000;
        private int timeoutSeconds = 5;
        private int failureThreshold = 3;
        private int successThreshold = 2;
    }
    
    @Data
    public static class Failover {
        private boolean enabled = true;
        private int maxRetryAttempts = 3;
        private long retryDelayMs = 1000;
    }
    
    @Data
    public static class TaskAnalyzer {
        private String ollamaModel = "qwen2.5-coder:3b-instruct-q5_K_M";
        private int timeoutSeconds = 30;
        private boolean fallbackToRules = true;
    }
}
