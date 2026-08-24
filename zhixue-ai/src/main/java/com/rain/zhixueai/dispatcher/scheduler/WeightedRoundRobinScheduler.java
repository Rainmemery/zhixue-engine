package com.rain.zhixueai.dispatcher.scheduler;

import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;
import com.rain.zhixueai.dispatcher.mapper.ModelInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeightedRoundRobinScheduler implements InstanceScheduler {
    
    private final ModelInstanceMapper modelInstanceMapper;
    
    private final ConcurrentHashMap<Long, WeightedState> weightedStateMap = new ConcurrentHashMap<>();
    
    @Override
    public ModelInstance selectInstance(Long groupId) {
        log.debug("加权轮询调度: 选择模型组 {} 的实例", groupId);
        
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
        
        WeightedState state = weightedStateMap.computeIfAbsent(groupId, k -> new WeightedState());
        
        ModelInstance selectedInstance = selectBySmoothWeightedRoundRobin(availableInstances, state);
        
        if (selectedInstance != null) {
            log.debug("加权轮询调度: 选中实例 {} (weight={})", 
                    selectedInstance.getName(), selectedInstance.getWeightOrDefault());
        }
        
        return selectedInstance;
    }
    
    private ModelInstance selectBySmoothWeightedRoundRobin(List<ModelInstance> instances, WeightedState state) {
        int totalWeight = 0;
        ModelInstance selectedInstance = null;
        int maxCurrentWeight = Integer.MIN_VALUE;
        
        for (ModelInstance instance : instances) {
            int weight = instance.getWeightOrDefault();
            totalWeight += weight;
            
            Integer currentWeight = state.currentWeights.get(instance.getId());
            if (currentWeight == null) {
                currentWeight = 0;
            }
            currentWeight += weight;
            state.currentWeights.put(instance.getId(), currentWeight);
            
            if (currentWeight > maxCurrentWeight) {
                maxCurrentWeight = currentWeight;
                selectedInstance = instance;
            }
        }
        
        if (selectedInstance == null) {
            return null;
        }
        
        Integer currentWeight = state.currentWeights.get(selectedInstance.getId());
        if (currentWeight != null) {
            state.currentWeights.put(selectedInstance.getId(), currentWeight - totalWeight);
        }
        
        return selectedInstance;
    }
    
    @Override
    public void releaseInstance(Long instanceId) {
        log.debug("加权轮询调度: 释放实例 {}", instanceId);
    }
    
    @Override
    public SchedulingStrategy getStrategyType() {
        return SchedulingStrategy.WEIGHTED_ROUND_ROBIN;
    }
    
    public void resetState(Long groupId) {
        weightedStateMap.remove(groupId);
        log.debug("重置模型组 {} 的加权状态", groupId);
    }
    
    public void resetAllStates() {
        weightedStateMap.clear();
        log.debug("重置所有模型组的加权状态");
    }
    
    private static class WeightedState {
        final ConcurrentHashMap<Long, Integer> currentWeights = new ConcurrentHashMap<>();
    }
}
