package com.rain.zhixueai.scheduling.health;

import com.rain.zhixueai.enums.AiModelType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelHealthStatus {
    
    private AiModelType modelType;
    
    private String modelName;
    
    private boolean available;
    
    private long responseTimeMs;
    
    private int consecutiveFailures;
    
    private int consecutiveSuccesses;
    
    private LocalDateTime lastCheckTime;
    
    private LocalDateTime lastSuccessTime;
    
    private LocalDateTime lastFailureTime;
    
    private String lastErrorMessage;
    
    private HealthState healthState;
    
    public enum HealthState {
        HEALTHY("健康", true),
        DEGRADED("降级", true),
        UNHEALTHY("不健康", false),
        UNKNOWN("未知", false);
        
        private final String description;
        private final boolean canServe;
        
        HealthState(String description, boolean canServe) {
            this.description = description;
            this.canServe = canServe;
        }
        
        public String getDescription() { return description; }
        public boolean canServe() { return canServe; }
    }
    
    public boolean canServe() {
        return available && healthState != null && healthState.canServe();
    }
    
    public boolean needsRecovery() {
        return healthState == HealthState.UNHEALTHY || healthState == HealthState.DEGRADED;
    }
    
    public static ModelHealthStatus unknown(AiModelType modelType, String modelName) {
        return ModelHealthStatus.builder()
                .modelType(modelType)
                .modelName(modelName)
                .available(false)
                .healthState(HealthState.UNKNOWN)
                .lastCheckTime(LocalDateTime.now())
                .build();
    }
    
    public static ModelHealthStatus healthy(AiModelType modelType, String modelName, long responseTimeMs) {
        return ModelHealthStatus.builder()
                .modelType(modelType)
                .modelName(modelName)
                .available(true)
                .responseTimeMs(responseTimeMs)
                .healthState(HealthState.HEALTHY)
                .lastCheckTime(LocalDateTime.now())
                .lastSuccessTime(LocalDateTime.now())
                .consecutiveSuccesses(1)
                .consecutiveFailures(0)
                .build();
    }
    
    public static ModelHealthStatus unhealthy(AiModelType modelType, String modelName, String errorMessage) {
        return ModelHealthStatus.builder()
                .modelType(modelType)
                .modelName(modelName)
                .available(false)
                .healthState(HealthState.UNHEALTHY)
                .lastCheckTime(LocalDateTime.now())
                .lastFailureTime(LocalDateTime.now())
                .lastErrorMessage(errorMessage)
                .consecutiveFailures(1)
                .consecutiveSuccesses(0)
                .build();
    }
}
