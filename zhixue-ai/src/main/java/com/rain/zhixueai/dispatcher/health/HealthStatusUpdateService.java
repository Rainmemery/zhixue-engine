package com.rain.zhixueai.dispatcher.health;

import com.rain.zhixueai.dispatcher.entity.InstanceHealthStatus;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.enums.HealthState;
import com.rain.zhixueai.dispatcher.enums.InstanceStatus;
import com.rain.zhixueai.dispatcher.mapper.InstanceHealthStatusMapper;
import com.rain.zhixueai.dispatcher.mapper.ModelInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthStatusUpdateService {
    
    private final ModelInstanceMapper modelInstanceMapper;
    private final InstanceHealthStatusMapper healthStatusMapper;
    private final HealthCheckProperties healthCheckProperties;
    
    @Transactional(rollbackFor = Exception.class)
    public void updateHealthStatus(Long instanceId, HealthCheckResult result) {
        if (instanceId == null || result == null) {
            log.warn("更新健康状态参数无效: instanceId={}, result={}", instanceId, result);
            return;
        }
        
        ModelInstance instance = modelInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("实例不存在，无法更新健康状态: instanceId={}", instanceId);
            return;
        }
        
        InstanceHealthStatus healthStatus = healthStatusMapper.findByInstanceId(instanceId);
        if (healthStatus == null) {
            log.warn("健康状态记录不存在: instanceId={}", instanceId);
            return;
        }
        
        if (result.isHealthy()) {
            handleHealthyResult(instance, healthStatus, result);
        } else {
            handleUnhealthyResult(instance, healthStatus, result);
        }
        
        healthStatus.setLastCheckTime(result.getCheckTime());
        healthStatus.setResponseTimeMs(result.getResponseTimeMs());
        healthStatus.setUpdatedAt(LocalDateTime.now());
        healthStatusMapper.updateById(healthStatus);
        
        log.info("健康状态更新完成: instanceId={}, healthy={}, healthState={}", 
                instanceId, result.isHealthy(), healthStatus.getHealthState());
    }
    
    private void handleHealthyResult(ModelInstance instance, InstanceHealthStatus healthStatus, HealthCheckResult result) {
        int consecutiveSuccesses = healthStatus.getConsecutiveSuccessesOrDefault() + 1;
        int consecutiveFailures = 0;
        
        healthStatus.setConsecutiveSuccesses(consecutiveSuccesses);
        healthStatus.setConsecutiveFailures(consecutiveFailures);
        healthStatus.setLastSuccessTime(result.getCheckTime());
        healthStatus.setLastErrorMessage(null);
        
        int recoveryThreshold = healthCheckProperties.getRecoveryThreshold();
        if (consecutiveSuccesses >= recoveryThreshold) {
            healthStatus.setHealthState(HealthState.HEALTHY);
            updateInstanceStatus(instance, InstanceStatus.HEALTHY);
            log.info("实例已恢复健康: instanceId={}, consecutiveSuccesses={}", instance.getId(), consecutiveSuccesses);
        } else if (healthStatus.getHealthStateOrDefault() == HealthState.UNHEALTHY) {
            healthStatus.setHealthState(HealthState.DEGRADED);
            updateInstanceStatus(instance, InstanceStatus.DEGRADED);
            log.info("实例状态降级: instanceId={}, consecutiveSuccesses={}", instance.getId(), consecutiveSuccesses);
        }
    }
    
    private void handleUnhealthyResult(ModelInstance instance, InstanceHealthStatus healthStatus, HealthCheckResult result) {
        int consecutiveFailures = healthStatus.getConsecutiveFailuresOrDefault() + 1;
        int consecutiveSuccesses = 0;
        
        healthStatus.setConsecutiveFailures(consecutiveFailures);
        healthStatus.setConsecutiveSuccesses(consecutiveSuccesses);
        healthStatus.setLastFailureTime(result.getCheckTime());
        healthStatus.setLastErrorMessage(result.getErrorMessage());
        
        int failureThreshold = healthCheckProperties.getFailureThreshold();
        if (consecutiveFailures >= failureThreshold) {
            healthStatus.setHealthState(HealthState.UNHEALTHY);
            updateInstanceStatus(instance, InstanceStatus.UNHEALTHY);
            log.warn("实例不健康: instanceId={}, consecutiveFailures={}, error={}", 
                    instance.getId(), consecutiveFailures, result.getErrorMessage());
        } else if (healthStatus.getHealthStateOrDefault() == HealthState.HEALTHY) {
            healthStatus.setHealthState(HealthState.DEGRADED);
            updateInstanceStatus(instance, InstanceStatus.DEGRADED);
            log.warn("实例状态降级: instanceId={}, consecutiveFailures={}", instance.getId(), consecutiveFailures);
        }
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void markAsHealthy(Long instanceId) {
        ModelInstance instance = modelInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("实例不存在: instanceId={}", instanceId);
            return;
        }
        
        InstanceHealthStatus healthStatus = healthStatusMapper.findByInstanceId(instanceId);
        if (healthStatus != null) {
            healthStatus.setHealthState(HealthState.HEALTHY);
            healthStatus.setConsecutiveFailures(0);
            healthStatus.setConsecutiveSuccesses(healthStatus.getConsecutiveSuccessesOrDefault() + 1);
            healthStatus.setLastSuccessTime(LocalDateTime.now());
            healthStatus.setLastErrorMessage(null);
            healthStatus.setUpdatedAt(LocalDateTime.now());
            healthStatusMapper.updateById(healthStatus);
        }
        
        updateInstanceStatus(instance, InstanceStatus.HEALTHY);
        log.info("实例标记为健康: instanceId={}", instanceId);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void markAsDegraded(Long instanceId, String reason) {
        ModelInstance instance = modelInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("实例不存在: instanceId={}", instanceId);
            return;
        }
        
        InstanceHealthStatus healthStatus = healthStatusMapper.findByInstanceId(instanceId);
        if (healthStatus != null) {
            healthStatus.setHealthState(HealthState.DEGRADED);
            healthStatus.setLastErrorMessage(reason);
            healthStatus.setUpdatedAt(LocalDateTime.now());
            healthStatusMapper.updateById(healthStatus);
        }
        
        updateInstanceStatus(instance, InstanceStatus.DEGRADED);
        log.warn("实例标记为降级: instanceId={}, reason={}", instanceId, reason);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void markAsUnhealthy(Long instanceId, String reason) {
        ModelInstance instance = modelInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("实例不存在: instanceId={}", instanceId);
            return;
        }
        
        InstanceHealthStatus healthStatus = healthStatusMapper.findByInstanceId(instanceId);
        if (healthStatus != null) {
            healthStatus.setHealthState(HealthState.UNHEALTHY);
            healthStatus.setConsecutiveFailures(healthStatus.getConsecutiveFailuresOrDefault() + 1);
            healthStatus.setConsecutiveSuccesses(0);
            healthStatus.setLastFailureTime(LocalDateTime.now());
            healthStatus.setLastErrorMessage(reason);
            healthStatus.setUpdatedAt(LocalDateTime.now());
            healthStatusMapper.updateById(healthStatus);
        }
        
        updateInstanceStatus(instance, InstanceStatus.UNHEALTHY);
        log.error("实例标记为不健康: instanceId={}, reason={}", instanceId, reason);
    }
    
    private void updateInstanceStatus(ModelInstance instance, InstanceStatus status) {
        if (instance.getStatusOrDefault() != status) {
            instance.setStatus(status);
            instance.setUpdatedAt(LocalDateTime.now());
            modelInstanceMapper.updateById(instance);
        }
    }
}
