package com.rain.zhixueai.dispatcher.scheduler;

import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;
import com.rain.zhixueai.dispatcher.mapper.ModelInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeastConnectionScheduler implements InstanceScheduler {
    
    private final ModelInstanceMapper modelInstanceMapper;
    
    @Override
    public ModelInstance selectInstance(Long groupId) {
        log.debug("最少连接调度: 选择模型组 {} 的实例", groupId);
        
        List<ModelInstance> healthyInstances = modelInstanceMapper.findHealthyByGroupId(groupId);
        
        if (healthyInstances == null || healthyInstances.isEmpty()) {
            log.warn("模型组 {} 没有可用的健康实例", groupId);
            return null;
        }
        
        List<ModelInstance> availableInstances = healthyInstances.stream()
                .filter(ModelInstance::canAcceptConnection)
                .toList();
        
        if (availableInstances.isEmpty()) {
            log.warn("模型组 {} 所有健康实例均已达到最大并发限制", groupId);
            return null;
        }
        
        ModelInstance selectedInstance = availableInstances.stream()
                .min(Comparator.comparingInt(ModelInstance::getCurrentConnectionsOrDefault))
                .orElse(null);
        
        if (selectedInstance != null) {
            log.debug("最少连接调度: 选中实例 {} (connections={})", 
                    selectedInstance.getName(), selectedInstance.getCurrentConnectionsOrDefault());
        }
        
        return selectedInstance;
    }
    
    @Override
    public void releaseInstance(Long instanceId) {
        log.debug("最少连接调度: 释放实例 {}", instanceId);
    }
    
    @Override
    public SchedulingStrategy getStrategyType() {
        return SchedulingStrategy.LEAST_CONNECTION;
    }
}
