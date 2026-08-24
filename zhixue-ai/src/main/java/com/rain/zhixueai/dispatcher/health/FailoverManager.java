package com.rain.zhixueai.dispatcher.health;

import com.rain.zhixueai.dispatcher.entity.InstanceHealthStatus;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.enums.HealthState;
import com.rain.zhixueai.dispatcher.enums.InstanceStatus;
import com.rain.zhixueai.dispatcher.mapper.InstanceHealthStatusMapper;
import com.rain.zhixueai.dispatcher.mapper.ModelInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailoverManager {
    
    private final ModelInstanceMapper modelInstanceMapper;
    private final InstanceHealthStatusMapper healthStatusMapper;
    private final HealthStatusUpdateService healthStatusUpdateService;
    private final HealthCheckProperties healthCheckProperties;
    
    private final Map<Long, FailoverState> failoverStates = new ConcurrentHashMap<>();
    private final List<FailoverEventListener> eventListeners = new ArrayList<>();
    
    public void handleInstanceFailure(ModelInstance instance, String reason) {
        if (instance == null) {
            return;
        }
        
        Long instanceId = instance.getId();
        log.warn("处理实例故障: instanceId={}, reason={}", instanceId, reason);
        
        FailoverState state = failoverStates.computeIfAbsent(instanceId, id -> FailoverState.normal(instanceId));
        state.setFailed(true);
        state.setFailureTime(LocalDateTime.now());
        state.setFailureReason(reason);
        state.setFailoverAttempts(state.getFailoverAttempts() + 1);
        failoverStates.put(instanceId, state);
        
        healthStatusUpdateService.markAsUnhealthy(instanceId, reason);
        
        notifyEventListeners(FailoverEvent.failure(instanceId, reason));
        
        tryPerformFailover(instance.getGroupId(), instanceId);
    }
    
    public void handleInstanceRecovery(ModelInstance instance) {
        if (instance == null) {
            return;
        }
        
        Long instanceId = instance.getId();
        log.info("处理实例恢复: instanceId={}", instanceId);
        
        FailoverState state = failoverStates.get(instanceId);
        if (state != null && state.isFailed()) {
            state.setFailed(false);
            state.setRecoveryTime(LocalDateTime.now());
            state.setFailureReason(null);
            failoverStates.put(instanceId, state);
        }
        
        healthStatusUpdateService.markAsHealthy(instanceId);
        
        notifyEventListeners(FailoverEvent.recovery(instanceId));
    }
    
    private void tryPerformFailover(Long groupId, Long failedInstanceId) {
        List<ModelInstance> healthyInstances = findHealthyInstancesInGroup(groupId, failedInstanceId);
        
        if (healthyInstances.isEmpty()) {
            log.error("模型组 {} 没有可用的健康实例进行故障转移", groupId);
            notifyEventListeners(FailoverEvent.failoverFailed(failedInstanceId, "没有可用的健康实例"));
            return;
        }
        
        ModelInstance targetInstance = selectBestInstance(healthyInstances);
        if (targetInstance != null) {
            log.info("故障转移成功: 从实例 {} 转移到实例 {}", failedInstanceId, targetInstance.getId());
            notifyEventListeners(FailoverEvent.failoverSuccess(failedInstanceId, targetInstance.getId()));
        }
    }
    
    private List<ModelInstance> findHealthyInstancesInGroup(Long groupId, Long excludeInstanceId) {
        List<ModelInstance> instances = modelInstanceMapper.findByGroupId(groupId);
        List<ModelInstance> healthyInstances = new ArrayList<>();
        
        for (ModelInstance instance : instances) {
            if (instance.getId().equals(excludeInstanceId)) {
                continue;
            }
            
            if (instance.getStatusOrDefault().isAvailable()) {
                InstanceHealthStatus healthStatus = healthStatusMapper.findByInstanceId(instance.getId());
                if (healthStatus != null && healthStatus.getHealthStateOrDefault() == HealthState.HEALTHY) {
                    healthyInstances.add(instance);
                }
            }
        }
        
        return healthyInstances;
    }
    
    private ModelInstance selectBestInstance(List<ModelInstance> instances) {
        if (instances.isEmpty()) {
            return null;
        }
        
        ModelInstance best = null;
        int minConnections = Integer.MAX_VALUE;
        
        for (ModelInstance instance : instances) {
            int connections = instance.getCurrentConnectionsOrDefault();
            if (connections < minConnections) {
                minConnections = connections;
                best = instance;
            }
        }
        
        return best != null ? best : instances.get(0);
    }
    
    public FailoverState getFailoverState(Long instanceId) {
        return failoverStates.get(instanceId);
    }
    
    public Map<Long, FailoverState> getAllFailoverStates() {
        return new ConcurrentHashMap<>(failoverStates);
    }
    
    public void addEventListener(FailoverEventListener listener) {
        eventListeners.add(listener);
    }
    
    public void removeEventListener(FailoverEventListener listener) {
        eventListeners.remove(listener);
    }
    
    private void notifyEventListeners(FailoverEvent event) {
        for (FailoverEventListener listener : eventListeners) {
            try {
                listener.onFailoverEvent(event);
            } catch (Exception e) {
                log.error("故障转移事件监听器执行失败", e);
            }
        }
    }
    
    public boolean needsHealthCheck(ModelInstance instance) {
        if (instance == null) {
            return false;
        }
        
        InstanceStatus status = instance.getStatusOrDefault();
        if (status == InstanceStatus.DISABLED) {
            return false;
        }
        
        InstanceHealthStatus healthStatus = healthStatusMapper.findByInstanceId(instance.getId());
        if (healthStatus == null) {
            return true;
        }
        
        if (healthStatus.getHealthStateOrDefault() == HealthState.UNHEALTHY) {
            return true;
        }
        
        return true;
    }
}
