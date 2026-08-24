package com.rain.zhixueai.dispatcher.service;

import com.rain.zhixueai.dispatcher.entity.ModelGroup;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.scheduler.InstanceScheduler;
import com.rain.zhixueai.dispatcher.scheduler.SchedulerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelGroupDispatcherService {
    
    private final ModelGroupService modelGroupService;
    private final ModelInstanceService instanceService;
    private final SchedulerFactory schedulerFactory;
    private final SchedulingLogService logService;
    
    public ModelInstance selectInstance(Long groupId) {
        log.debug("选择模型组 {} 的实例", groupId);
        
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        
        ModelGroup group = modelGroupService.getGroupById(groupId);
        if (group == null) {
            log.error("模型组不存在: {}", groupId);
            logService.logSelection(groupId, null, requestId, "FAILURE", "模型组不存在", null);
            throw new IllegalArgumentException("模型组不存在: " + groupId);
        }
        
        if (!group.getEnabledOrDefault()) {
            log.warn("模型组 {} 未启用", groupId);
            logService.logSelection(groupId, null, requestId, "FAILURE", "模型组未启用", null);
            throw new IllegalStateException("模型组未启用: " + group.getName());
        }
        
        InstanceScheduler scheduler = schedulerFactory.getSchedulerForGroup(group);
        ModelInstance instance = scheduler.selectInstance(groupId);
        
        long durationMs = System.currentTimeMillis() - startTime;
        
        if (instance == null) {
            log.warn("模型组 {} 无可用实例", groupId);
            logService.logSelection(groupId, null, requestId, "FAILURE", "无可用实例", durationMs);
            return null;
        }
        
        logService.logSelection(groupId, instance.getId(), requestId, "SUCCESS", null, durationMs);
        
        log.info("调度成功: groupId={}, instanceId={}, strategy={}, duration={}ms",
                groupId, instance.getId(), scheduler.getStrategyType(), durationMs);
        
        return instance;
    }
    
    public ModelInstance selectInstance(String groupName) {
        log.debug("通过名称选择模型组 {} 的实例", groupName);
        
        if (groupName == null || groupName.trim().isEmpty()) {
            log.error("模型组名称为空");
            throw new IllegalArgumentException("模型组名称不能为空");
        }
        
        ModelGroup group = modelGroupService.getGroupByName(groupName);
        if (group == null) {
            log.error("模型组不存在: {}", groupName);
            throw new IllegalArgumentException("模型组不存在: " + groupName);
        }
        
        return selectInstance(group.getId());
    }
    
    public void releaseInstance(Long instanceId) {
        log.debug("释放实例: {}", instanceId);
        
        if (instanceId == null) {
            log.warn("实例ID为空，忽略释放请求");
            return;
        }
        
        ModelInstance instance = instanceService.getInstanceById(instanceId);
        if (instance == null) {
            log.warn("实例不存在: {}，忽略释放请求", instanceId);
            return;
        }
        
        ModelGroup group = modelGroupService.getGroupById(instance.getGroupId());
        if (group == null) {
            log.warn("实例 {} 所属的模型组不存在，使用默认调度器释放", instanceId);
            InstanceScheduler defaultScheduler = schedulerFactory.getScheduler(null);
            defaultScheduler.releaseInstance(instanceId);
            return;
        }
        
        InstanceScheduler scheduler = schedulerFactory.getSchedulerForGroup(group);
        scheduler.releaseInstance(instanceId);
        
        log.debug("实例 {} 释放成功", instanceId);
    }
    
    public void recordSuccess(Long instanceId) {
        log.debug("记录实例成功: {}", instanceId);
        instanceService.recordSuccess(instanceId);
    }
    
    public void recordFailure(Long instanceId, String errorMessage) {
        log.debug("记录实例失败: instanceId={}, error={}", instanceId, errorMessage);
        instanceService.recordFailure(instanceId, errorMessage);
    }
    
    public ModelGroup getGroupById(Long groupId) {
        return modelGroupService.getGroupById(groupId);
    }
    
    public ModelGroup getGroupByName(String groupName) {
        return modelGroupService.getGroupByName(groupName);
    }
    
    public ModelInstance getInstanceById(Long instanceId) {
        return instanceService.getInstanceById(instanceId);
    }
}
