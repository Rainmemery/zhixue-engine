package com.rain.zhixueai.dispatcher.service.impl;

import com.rain.zhixueai.dispatcher.dto.ModelInstanceCreateRequest;
import com.rain.zhixueai.dispatcher.dto.ModelInstanceUpdateRequest;
import com.rain.zhixueai.dispatcher.entity.InstanceHealthStatus;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.enums.HealthState;
import com.rain.zhixueai.dispatcher.enums.InstanceStatus;
import com.rain.zhixueai.dispatcher.mapper.InstanceHealthStatusMapper;
import com.rain.zhixueai.dispatcher.mapper.ModelInstanceMapper;
import com.rain.zhixueai.dispatcher.service.ModelInstanceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelInstanceServiceImpl implements ModelInstanceService {
    
    private final ModelInstanceMapper modelInstanceMapper;
    private final InstanceHealthStatusMapper healthStatusMapper;
    
    @Override
    public List<ModelInstance> getAllInstances() {
        log.debug("获取所有模型实例");
        return modelInstanceMapper.selectList(new LambdaQueryWrapper<ModelInstance>().orderByAsc(ModelInstance::getId));
    }
    
    @Override
    public List<ModelInstance> getInstancesByGroupId(Long groupId) {
        log.debug("获取模型组 {} 的所有实例", groupId);
        return modelInstanceMapper.findByGroupId(groupId);
    }
    
    @Override
    public List<ModelInstance> getHealthyInstancesByGroupId(Long groupId) {
        log.debug("获取模型组 {} 的健康实例", groupId);
        return modelInstanceMapper.findHealthyByGroupId(groupId);
    }
    
    @Override
    public ModelInstance getInstanceById(Long id) {
        log.debug("获取模型实例: {}", id);
        return modelInstanceMapper.selectById(id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelInstance createInstance(ModelInstanceCreateRequest request) {
        log.info("创建模型实例: name={}, groupId={}", request.getName(), request.getGroupId());
        
        ModelInstance instance = ModelInstance.builder()
                .groupId(request.getGroupId())
                .name(request.getName())
                .apiEndpoint(request.getApiEndpoint())
                .modelName(request.getModelName())
                .apiKey(request.getApiKey())
                .weight(request.getWeightOrDefault())
                .maxConcurrent(request.getMaxConcurrentOrDefault())
                .currentConnections(0)
                .status(InstanceStatus.DISABLED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        modelInstanceMapper.insert(instance);
        
        InstanceHealthStatus healthStatus = InstanceHealthStatus.builder()
                .instanceId(instance.getId())
                .healthState(HealthState.UNHEALTHY)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        healthStatusMapper.insert(healthStatus);
        
        log.info("模型实例创建成功: id={}", instance.getId());
        return instance;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelInstance updateInstance(Long id, ModelInstanceUpdateRequest request) {
        log.info("更新模型实例: id={}", id);
        
        ModelInstance instance = modelInstanceMapper.selectById(id);
        if (instance == null) {
            throw new IllegalArgumentException("模型实例不存在: " + id);
        }
        
        if (request.getName() != null) {
            instance.setName(request.getName());
        }
        if (request.getApiEndpoint() != null) {
            instance.setApiEndpoint(request.getApiEndpoint());
        }
        if (request.getModelName() != null) {
            instance.setModelName(request.getModelName());
        }
        if (request.getApiKey() != null) {
            instance.setApiKey(request.getApiKey());
        }
        if (request.getWeight() != null) {
            instance.setWeight(request.getWeight());
        }
        if (request.getMaxConcurrent() != null) {
            instance.setMaxConcurrent(request.getMaxConcurrent());
        }
        if (request.getStatus() != null) {
            instance.setStatus(request.getStatus());
        }
        
        instance.setUpdatedAt(LocalDateTime.now());
        modelInstanceMapper.updateById(instance);
        
        log.info("模型实例更新成功: id={}", id);
        return instance;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInstance(Long id) {
        log.info("删除模型实例: id={}", id);
        
        ModelInstance instance = modelInstanceMapper.selectById(id);
        if (instance == null) {
            throw new IllegalArgumentException("模型实例不存在: " + id);
        }
        
        InstanceHealthStatus healthStatus = healthStatusMapper.findByInstanceId(id);
        if (healthStatus != null) {
            healthStatusMapper.deleteById(healthStatus.getId());
            log.debug("删除实例健康状态记录: instanceId={}", id);
        }
        
        modelInstanceMapper.deleteById(id);
        
        log.info("模型实例删除成功: id={}", id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInstanceStatus(Long id, String status) {
        log.info("更新模型实例状态: id={}, status={}", id, status);
        
        ModelInstance instance = modelInstanceMapper.selectById(id);
        if (instance == null) {
            throw new IllegalArgumentException("模型实例不存在: " + id);
        }
        
        InstanceStatus instanceStatus = InstanceStatus.fromCode(status);
        instance.setStatus(instanceStatus);
        instance.setUpdatedAt(LocalDateTime.now());
        
        modelInstanceMapper.updateById(instance);
        
        log.info("模型实例状态更新成功: id={}, status={}", id, instanceStatus);
    }
    
    @Override
    public InstanceHealthStatus getInstanceHealth(Long instanceId) {
        log.debug("获取实例健康状态: instanceId={}", instanceId);
        return healthStatusMapper.findByInstanceId(instanceId);
    }

    @Override
    public void incrementConnections(Long instanceId) {
        log.debug("增加实例连接数: instanceId={}", instanceId);
        modelInstanceMapper.incrementConnections(instanceId);
    }

    @Override
    public void decrementConnections(Long instanceId) {
        log.debug("减少实例连接数: instanceId={}", instanceId);
        modelInstanceMapper.decrementConnections(instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordSuccess(Long instanceId) {
        log.debug("记录实例成功: instanceId={}", instanceId);
        healthStatusMapper.recordSuccess(instanceId);
    }
    
    @Override
    public void recordFailure(Long instanceId, String errorMessage) {
        log.debug("记录实例失败: instanceId={}, error={}", instanceId, errorMessage);
        healthStatusMapper.recordFailure(instanceId, errorMessage);
    }

    @Override
    public int sumAllCurrentConnections() {
        return modelInstanceMapper.sumAllCurrentConnections();
    }
}
