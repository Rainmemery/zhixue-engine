package com.rain.zhixueai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "resource")
public class ResourceConfigProperties {

    private ExecutorConfig executor = new ExecutorConfig();
    
    private ConcurrencyConfig concurrency = new ConcurrencyConfig();
    
    private IsolationConfig isolation = new IsolationConfig();
    
    private MonitorConfig monitor = new MonitorConfig();
    
    @Data
    public static class ExecutorConfig {
        
        private Integer corePoolSize = 4;
        
        private Integer maxPoolSize = 8;
        
        private Integer queueCapacity = 100;
        
        private Integer keepAliveSeconds = 60;
        
        private String threadNamePrefix = "tool-exec-";
        
        private Boolean allowCoreThreadTimeOut = true;
    }
    
    @Data
    public static class ConcurrencyConfig {
        
        private Integer maxConcurrentTools = 5;
        
        private Integer maxConcurrentPerUser = 2;
        
        private Map<String, Integer> toolLimits = new HashMap<>();
        
        public ConcurrencyConfig() {
            toolLimits.put("problem_generate", 2);
            toolLimits.put("problem_recommend", 3);
            toolLimits.put("user_profile", 5);
            toolLimits.put("difficulty_adapt", 5);
            toolLimits.put("wrong_question", 3);
        }
    }
    
    @Data
    public static class IsolationConfig {
        
        private Boolean enabled = true;
        
        private Map<String, ResourceQuota> toolQuotas = new HashMap<>();
        
        private Integer defaultMaxMemoryMB = 512;
        
        private Integer defaultMaxCpuPercent = 50;
        
        public IsolationConfig() {
            ResourceQuota generateQuota = new ResourceQuota();
            generateQuota.setMaxMemoryMB(1024);
            generateQuota.setMaxCpuPercent(80);
            generateQuota.setTimeoutSeconds(120);
            toolQuotas.put("problem_generate", generateQuota);
            
            ResourceQuota recommendQuota = new ResourceQuota();
            recommendQuota.setMaxMemoryMB(512);
            recommendQuota.setMaxCpuPercent(50);
            recommendQuota.setTimeoutSeconds(60);
            toolQuotas.put("problem_recommend", recommendQuota);
        }
    }
    
    @Data
    public static class ResourceQuota {
        
        private Integer maxMemoryMB;
        
        private Integer maxCpuPercent;
        
        private Integer timeoutSeconds;
    }
    
    @Data
    public static class MonitorConfig {
        
        private Boolean enabled = true;
        
        private Integer checkIntervalSeconds = 10;
        
        private Integer alertThresholdPercent = 80;
        
        private Integer criticalThresholdPercent = 95;
        
        private Integer queueSizeAlertThreshold = 50;
    }
}
