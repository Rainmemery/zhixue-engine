package com.rain.zhixueai.dispatcher.service;

import com.rain.zhixueai.dispatcher.dto.ModelInstanceCreateRequest;
import com.rain.zhixueai.dispatcher.dto.ModelInstanceUpdateRequest;
import com.rain.zhixueai.dispatcher.entity.InstanceHealthStatus;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;

import java.util.List;

public interface ModelInstanceService {
    
    List<ModelInstance> getAllInstances();
    
    List<ModelInstance> getInstancesByGroupId(Long groupId);
    
    List<ModelInstance> getHealthyInstancesByGroupId(Long groupId);
    
    ModelInstance getInstanceById(Long id);
    
    ModelInstance createInstance(ModelInstanceCreateRequest request);
    
    ModelInstance updateInstance(Long id, ModelInstanceUpdateRequest request);
    
    void deleteInstance(Long id);
    
    void updateInstanceStatus(Long id, String status);
    
    InstanceHealthStatus getInstanceHealth(Long instanceId);
    
    void incrementConnections(Long instanceId);
    
    void decrementConnections(Long instanceId);
    
    void recordSuccess(Long instanceId);
    
    void recordFailure(Long instanceId, String errorMessage);

    int sumAllCurrentConnections();
}
