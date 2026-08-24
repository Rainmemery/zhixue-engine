package com.rain.zhixueai.dispatcher.health;

import com.rain.zhixueai.dispatcher.entity.InstanceHealthStatus;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.enums.HealthState;
import com.rain.zhixueai.dispatcher.enums.InstanceStatus;
import com.rain.zhixueai.dispatcher.service.ModelInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckScheduler {
    
    private final InstanceHealthChecker healthChecker;
    private final ModelInstanceService instanceService;
    private final HealthStatusUpdateService healthStatusUpdateService;
    private final FailoverManager failoverManager;
    private final HealthCheckProperties healthCheckProperties;
    
    @Scheduled(fixedRateString = "${dispatcher.health-check.interval-seconds:30}000")
    public void performHealthCheck() {
        log.debug("开始执行定时健康检查");
        
        List<ModelInstance> instances = instanceService.getAllInstances();
        int healthyCount = 0;
        int unhealthyCount = 0;
        int skippedCount = 0;
        
        for (ModelInstance instance : instances) {
            if (instance.getStatusOrDefault() == InstanceStatus.DISABLED) {
                skippedCount++;
                continue;
            }
            
            try {
                HealthCheckResult result = healthChecker.checkHealth(instance);
                healthStatusUpdateService.updateHealthStatus(instance.getId(), result);
                
                if (result.isHealthy()) {
                    healthyCount++;
                } else {
                    unhealthyCount++;
                    if (shouldTriggerFailover(instance.getId())) {
                        failoverManager.handleInstanceFailure(instance, result.getErrorMessage());
                    }
                }
            } catch (Exception e) {
                log.error("健康检查异常: instanceId={}", instance.getId(), e);
                unhealthyCount++;
            }
        }
        
        log.debug("健康检查完成: 健康={}, 不健康={}, 跳过={}", healthyCount, unhealthyCount, skippedCount);
    }
    
    @Scheduled(fixedRate = 60000)
    public void checkRecovery() {
        log.debug("开始检查故障实例恢复");
        
        List<ModelInstance> instances = instanceService.getAllInstances();
        int recoveredCount = 0;
        
        for (ModelInstance instance : instances) {
            InstanceHealthStatus healthStatus = instanceService.getInstanceHealth(instance.getId());
            
            if (healthStatus == null) {
                continue;
            }
            
            if (healthStatus.getHealthStateOrDefault() == HealthState.UNHEALTHY ||
                healthStatus.getHealthStateOrDefault() == HealthState.DEGRADED) {
                
                HealthCheckResult result = healthChecker.checkHealth(instance);
                
                if (result.isHealthy()) {
                    healthStatusUpdateService.updateHealthStatus(instance.getId(), result);
                    
                    if (shouldMarkAsRecovered(instance.getId())) {
                        failoverManager.handleInstanceRecovery(instance);
                        recoveredCount++;
                    }
                }
            }
        }
        
        if (recoveredCount > 0) {
            log.info("故障实例恢复检查完成: 恢复实例数={}", recoveredCount);
        }
    }
    
    private boolean shouldTriggerFailover(Long instanceId) {
        InstanceHealthStatus healthStatus = instanceService.getInstanceHealth(instanceId);
        if (healthStatus == null) {
            return false;
        }
        
        int consecutiveFailures = healthStatus.getConsecutiveFailuresOrDefault();
        return consecutiveFailures >= healthCheckProperties.getFailureThreshold();
    }
    
    private boolean shouldMarkAsRecovered(Long instanceId) {
        InstanceHealthStatus healthStatus = instanceService.getInstanceHealth(instanceId);
        if (healthStatus == null) {
            return false;
        }
        
        int consecutiveSuccesses = healthStatus.getConsecutiveSuccessesOrDefault();
        return consecutiveSuccesses >= healthCheckProperties.getRecoveryThreshold();
    }
}
