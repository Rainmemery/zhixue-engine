package com.rain.zhixueai.dispatcher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rain.zhixueai.dispatcher.enums.HealthState;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实例健康状态实体
 * 记录模型实例的健康检查状态和历史
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_instance_health")
public class InstanceHealthStatus {
    
    @TableId(type = IdType.AUTO)
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
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    public HealthState getHealthStateOrDefault() {
        return healthState != null ? healthState : HealthState.UNHEALTHY;
    }
    
    public Integer getConsecutiveFailuresOrDefault() {
        return consecutiveFailures != null ? consecutiveFailures : 0;
    }
    
    public Integer getConsecutiveSuccessesOrDefault() {
        return consecutiveSuccesses != null ? consecutiveSuccesses : 0;
    }
    
    public boolean isHealthy() {
        return getHealthStateOrDefault() == HealthState.HEALTHY;
    }
    
    public boolean isDegraded() {
        return getHealthStateOrDefault() == HealthState.DEGRADED;
    }
    
    public boolean isUnhealthy() {
        return getHealthStateOrDefault() == HealthState.UNHEALTHY;
    }
}
