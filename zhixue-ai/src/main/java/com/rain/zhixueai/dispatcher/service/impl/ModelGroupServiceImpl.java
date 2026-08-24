package com.rain.zhixueai.dispatcher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rain.zhixueai.dispatcher.dto.ModelGroupCreateRequest;
import com.rain.zhixueai.dispatcher.dto.ModelGroupUpdateRequest;
import com.rain.zhixueai.dispatcher.entity.ModelGroup;
import com.rain.zhixueai.dispatcher.mapper.ModelGroupMapper;
import com.rain.zhixueai.dispatcher.service.ModelGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 模型组服务实现
 * 
 * @author rain
 * @since 2026-04-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelGroupServiceImpl implements ModelGroupService {
    
    private final ModelGroupMapper modelGroupMapper;
    
    @Override
    public List<ModelGroup> getAllGroups() {
        log.debug("获取所有模型组");
        return modelGroupMapper.findAllOrderByPriority();
    }
    
    @Override
    public List<ModelGroup> getEnabledGroups() {
        log.debug("获取所有启用的模型组");
        return modelGroupMapper.findAllEnabled();
    }
    
    @Override
    public ModelGroup getGroupById(Long id) {
        log.debug("获取模型组: id={}", id);
        return modelGroupMapper.selectById(id);
    }
    
    @Override
    public ModelGroup getGroupByName(String name) {
        log.debug("根据名称获取模型组: name={}", name);
        return modelGroupMapper.findByName(name);
    }
    
    @Override
    public Optional<ModelGroup> getDefaultGroup() {
        log.debug("获取默认模型组");
        ModelGroup defaultGroup = modelGroupMapper.findDefaultGroup();
        return Optional.ofNullable(defaultGroup);
    }
    
    @Override
    @Transactional
    public ModelGroup createGroup(ModelGroupCreateRequest request) {
        log.info("创建模型组: name={}", request.getName());
        
        ModelGroup existing = modelGroupMapper.findByName(request.getName());
        if (existing != null) {
            throw new IllegalArgumentException("模型组名称已存在: " + request.getName());
        }
        
        if (Boolean.TRUE.equals(request.getIsDefaultOrDefault())) {
            clearDefaultFlag();
        }
        
        ModelGroup group = ModelGroup.builder()
                .name(request.getName())
                .modelType(request.getModelTypeOrDefault())
                .description(request.getDescription())
                .schedulingStrategy(request.getSchedulingStrategyOrDefault())
                .defaultApiKey(request.getDefaultApiKey())
                .defaultBaseUrl(request.getDefaultBaseUrl())
                .defaultTimeoutMs(request.getDefaultTimeoutMsOrDefault())
                .defaultMaxRetries(request.getDefaultMaxRetriesOrDefault())
                .priority(request.getPriorityOrDefault())
                .enabled(request.getEnabledOrDefault())
                .isDefault(request.getIsDefaultOrDefault())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        modelGroupMapper.insert(group);
        log.info("创建模型组成功: id={}, name={}", group.getId(), group.getName());
        
        return group;
    }
    
    @Override
    @Transactional
    public ModelGroup updateGroup(Long id, ModelGroupUpdateRequest request) {
        log.info("更新模型组: id={}", id);
        
        ModelGroup existing = modelGroupMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模型组不存在: " + id);
        }
        
        if (request.getName() != null && !request.getName().equals(existing.getName())) {
            ModelGroup nameExists = modelGroupMapper.findByName(request.getName());
            if (nameExists != null) {
                throw new IllegalArgumentException("模型组名称已存在: " + request.getName());
            }
            existing.setName(request.getName());
        }
        
        if (request.getModelType() != null) {
            existing.setModelType(request.getModelType());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getSchedulingStrategy() != null) {
            existing.setSchedulingStrategy(request.getSchedulingStrategy());
        }
        if (request.getDefaultApiKey() != null) {
            existing.setDefaultApiKey(request.getDefaultApiKey());
        }
        if (request.getDefaultBaseUrl() != null) {
            existing.setDefaultBaseUrl(request.getDefaultBaseUrl());
        }
        if (request.getDefaultTimeoutMs() != null) {
            existing.setDefaultTimeoutMs(request.getDefaultTimeoutMs());
        }
        if (request.getDefaultMaxRetries() != null) {
            existing.setDefaultMaxRetries(request.getDefaultMaxRetries());
        }
        if (request.getPriority() != null) {
            existing.setPriority(request.getPriority());
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultFlag();
            existing.setIsDefault(true);
        } else if (Boolean.FALSE.equals(request.getIsDefault())) {
            existing.setIsDefault(false);
        }
        
        existing.setUpdatedAt(LocalDateTime.now());
        
        modelGroupMapper.updateById(existing);
        log.info("更新模型组成功: id={}, name={}", id, existing.getName());
        
        return existing;
    }
    
    @Override
    @Transactional
    public void deleteGroup(Long id) {
        log.info("删除模型组: id={}", id);
        
        ModelGroup existing = modelGroupMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模型组不存在: " + id);
        }
        
        int instanceCount = modelGroupMapper.countInstancesByGroupId(id);
        if (instanceCount > 0) {
            throw new IllegalStateException("模型组下存在 " + instanceCount + " 个关联实例，无法删除");
        }
        
        modelGroupMapper.deleteById(id);
        log.info("删除模型组成功: id={}, name={}", id, existing.getName());
    }
    
    @Override
    @Transactional
    public void enableGroup(Long id, boolean enabled) {
        log.info("{}模型组: id={}", enabled ? "启用" : "禁用", id);
        
        ModelGroup existing = modelGroupMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模型组不存在: " + id);
        }
        
        existing.setEnabled(enabled);
        existing.setUpdatedAt(LocalDateTime.now());
        
        modelGroupMapper.updateById(existing);
        log.info("{}模型组成功: id={}, name={}", enabled ? "启用" : "禁用", id, existing.getName());
    }
    
    @Override
    @Transactional
    public void setDefaultGroup(Long id) {
        log.info("设置默认模型组: id={}", id);
        
        ModelGroup existing = modelGroupMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模型组不存在: " + id);
        }
        
        clearDefaultFlag();
        
        existing.setIsDefault(true);
        existing.setUpdatedAt(LocalDateTime.now());
        
        modelGroupMapper.updateById(existing);
        log.info("设置默认模型组成功: id={}, name={}", id, existing.getName());
    }
    
    private void clearDefaultFlag() {
        modelGroupMapper.clearDefaultFlag();
    }
}
