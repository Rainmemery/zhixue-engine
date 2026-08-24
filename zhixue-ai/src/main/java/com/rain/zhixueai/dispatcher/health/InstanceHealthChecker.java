package com.rain.zhixueai.dispatcher.health;

import com.rain.zhixueai.dispatcher.entity.ModelInstance;

public interface InstanceHealthChecker {
    
    HealthCheckResult checkHealth(ModelInstance instance);
    
    HealthCheckResult checkHealth(Long instanceId);
}
