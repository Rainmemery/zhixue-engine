package com.rain.zhixueai.dispatcher.scheduler;

import com.rain.zhixueai.dispatcher.entity.ModelGroup;
import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerFactory {
    
    private final List<InstanceScheduler> schedulerList;
    
    private final Map<SchedulingStrategy, InstanceScheduler> schedulers = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        for (InstanceScheduler scheduler : schedulerList) {
            schedulers.put(scheduler.getStrategyType(), scheduler);
            log.info("注册调度器: {} -> {}", scheduler.getStrategyType(), scheduler.getClass().getSimpleName());
        }
    }
    
    public InstanceScheduler getScheduler(SchedulingStrategy strategy) {
        if (strategy == null) {
            strategy = SchedulingStrategy.ROUND_ROBIN;
        }
        
        InstanceScheduler scheduler = schedulers.get(strategy);
        if (scheduler == null) {
            log.warn("未找到调度策略 {} 的调度器，使用默认轮询调度器", strategy);
            scheduler = schedulers.get(SchedulingStrategy.ROUND_ROBIN);
        }
        
        return scheduler;
    }
    
    public InstanceScheduler getSchedulerForGroup(ModelGroup group) {
        if (group == null) {
            log.warn("模型组为空，使用默认轮询调度器");
            return getScheduler(SchedulingStrategy.ROUND_ROBIN);
        }
        
        SchedulingStrategy strategy = group.getSchedulingStrategyOrDefault();
        log.debug("为模型组 {} 获取调度器: {}", group.getName(), strategy);
        
        return getScheduler(strategy);
    }
    
    public boolean hasScheduler(SchedulingStrategy strategy) {
        return schedulers.containsKey(strategy);
    }
    
    public java.util.Set<SchedulingStrategy> getAvailableStrategies() {
        return schedulers.keySet();
    }
}
