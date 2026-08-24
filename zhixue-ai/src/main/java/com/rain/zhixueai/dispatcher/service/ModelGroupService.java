package com.rain.zhixueai.dispatcher.service;

import com.rain.zhixueai.dispatcher.dto.ModelGroupCreateRequest;
import com.rain.zhixueai.dispatcher.dto.ModelGroupUpdateRequest;
import com.rain.zhixueai.dispatcher.entity.ModelGroup;

import java.util.List;
import java.util.Optional;

/**
 * 模型组服务接口
 * 
 * @author rain
 * @since 2026-04-19
 */
public interface ModelGroupService {
    
    List<ModelGroup> getAllGroups();
    
    List<ModelGroup> getEnabledGroups();
    
    ModelGroup getGroupById(Long id);
    
    ModelGroup getGroupByName(String name);
    
    Optional<ModelGroup> getDefaultGroup();
    
    ModelGroup createGroup(ModelGroupCreateRequest request);
    
    ModelGroup updateGroup(Long id, ModelGroupUpdateRequest request);
    
    void deleteGroup(Long id);
    
    void enableGroup(Long id, boolean enabled);
    
    void setDefaultGroup(Long id);
}
