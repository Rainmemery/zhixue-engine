package com.rain.zhixueai.dispatcher.dto;

import com.rain.zhixueai.dispatcher.enums.HealthState;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实例健康状态DTO
 * 用于展示实例的健康检查信息
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceHealthDTO {
    
    private Long id;
    
    private Long instanceId;
    
    private HealthState healthState;
    
    private Long responseTimeMs;
    
    private Integer consecutiveFailures;
    
    private Integer consecutiveSuccesses;
    
    private LocalDateTime lastCheckTime;
    
    private LocalDateTime lastSuccessTime;
    
    private LocalDateTime lastFailureTime;
    
    private String lastErrorMessage;
    
    public boolean isHealthy() {
        return healthState == HealthState.HEALTHY;
    }
    
    public boolean isDegraded() {
        return healthState == HealthState.DEGRADED;
    }
    
    public boolean isUnhealthy() {
        return healthState == HealthState.UNHEALTHY;
    }
}
