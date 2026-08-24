package com.rain.zhixueai.dispatcher.scheduler;

import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;

public interface InstanceScheduler {
    
    ModelInstance selectInstance(Long groupId);
    
    void releaseInstance(Long instanceId);
    
    SchedulingStrategy getStrategyType();
}
