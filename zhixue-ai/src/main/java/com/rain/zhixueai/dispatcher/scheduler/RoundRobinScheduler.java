package com.rain.zhixueai.dispatcher.scheduler;

import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;
import com.rain.zhixueai.dispatcher.mapper.ModelInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoundRobinScheduler implements InstanceScheduler {
    
    private final ModelInstanceMapper modelInstanceMapper;
    
    private final ConcurrentHashMap<Long, AtomicInteger> positionMap = new ConcurrentHashMap<>();
    
    @Override
    public ModelInstance selectInstance(Long groupId) {
        log.debug("轮询调度: 选择模型组 {} 的实例", groupId);
        
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
        
        AtomicInteger position = positionMap.computeIfAbsent(groupId, k -> new AtomicInteger(0));
        
        int currentIndex = Math.abs(position.getAndIncrement() % availableInstances.size());
        ModelInstance selectedInstance = availableInstances.get(currentIndex);
        
        log.debug("轮询调度: 选中实例 {} (index={})", selectedInstance.getName(), currentIndex);
        return selectedInstance;
    }
    
    @Override
    public void releaseInstance(Long instanceId) {
        log.debug("轮询调度: 释放实例 {}", instanceId);
    }
    
    @Override
    public SchedulingStrategy getStrategyType() {
        return SchedulingStrategy.ROUND_ROBIN;
    }
    
    public void resetPosition(Long groupId) {
        positionMap.remove(groupId);
        log.debug("重置模型组 {} 的轮询位置", groupId);
    }
    
    public void resetAllPositions() {
        positionMap.clear();
        log.debug("重置所有模型组的轮询位置");
    }
}
