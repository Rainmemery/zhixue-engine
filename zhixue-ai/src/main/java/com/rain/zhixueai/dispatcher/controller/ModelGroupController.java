package com.rain.zhixueai.dispatcher.controller;

import com.rain.zhixueai.dispatcher.dto.ModelGroupCreateRequest;
import com.rain.zhixueai.dispatcher.dto.ModelGroupDTO;
import com.rain.zhixueai.dispatcher.dto.ModelGroupUpdateRequest;
import com.rain.zhixueai.dispatcher.dto.ModelInstanceDTO;
import com.rain.zhixueai.dispatcher.entity.ModelGroup;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.service.ModelGroupService;
import com.rain.zhixueai.dispatcher.service.ModelInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dispatcher/groups")
@RequiredArgsConstructor
@Slf4j
public class ModelGroupController {
    
    private final ModelGroupService modelGroupService;
    private final ModelInstanceService instanceService;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllGroups() {
        try {
            List<ModelGroup> groups = modelGroupService.getAllGroups();
            List<ModelGroupDTO> dtoList = groups.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("items", dtoList);
            result.put("total", dtoList.size());
            return success(result);
        } catch (Exception e) {
            log.error("获取模型组列表失败", e);
            return error("获取模型组列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getGroupById(@PathVariable Long id) {
        try {
            ModelGroup group = modelGroupService.getGroupById(id);
            if (group == null) {
                return error("模型组不存在", 404);
            }
            return success(convertToDTO(group));
        } catch (Exception e) {
            log.error("获取模型组失败, id: {}", id, e);
            return error("获取模型组失败: " + e.getMessage());
        }
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> createGroup(@Valid @RequestBody ModelGroupCreateRequest request) {
        try {
            ModelGroup existing = modelGroupService.getGroupByName(request.getName());
            if (existing != null) {
                return error("模型组名称已存在", 400);
            }
            
            ModelGroup created = modelGroupService.createGroup(request);
            log.info("创建模型组成功: {}", created.getName());
            return success(convertToDTO(created));
        } catch (Exception e) {
            log.error("创建模型组失败", e);
            return error("创建模型组失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody ModelGroupUpdateRequest request) {
        try {
            ModelGroup existing = modelGroupService.getGroupById(id);
            if (existing == null) {
                return error("模型组不存在", 404);
            }
            
            if (request.getName() != null && !request.getName().equals(existing.getName())) {
                ModelGroup nameExisting = modelGroupService.getGroupByName(request.getName());
                if (nameExisting != null) {
                    return error("模型组名称已存在", 400);
                }
            }
            
            ModelGroup updated = modelGroupService.updateGroup(id, request);
            log.info("更新模型组成功: {}", id);
            return success(convertToDTO(updated));
        } catch (Exception e) {
            log.error("更新模型组失败, id: {}", id, e);
            return error("更新模型组失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteGroup(@PathVariable Long id) {
        try {
            ModelGroup existing = modelGroupService.getGroupById(id);
            if (existing == null) {
                return error("模型组不存在", 404);
            }
            
            List<ModelInstance> instances = instanceService.getInstancesByGroupId(id);
            if (!instances.isEmpty()) {
                return error("该模型组下存在实例，请先删除实例", 400);
            }
            
            modelGroupService.deleteGroup(id);
            log.info("删除模型组成功: {}", id);
            return success(null);
        } catch (Exception e) {
            log.error("删除模型组失败, id: {}", id, e);
            return error("删除模型组失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}/enable")
    public ResponseEntity<Map<String, Object>> enableGroup(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        try {
            ModelGroup existing = modelGroupService.getGroupById(id);
            if (existing == null) {
                return error("模型组不存在", 404);
            }
            
            Boolean enabled = body.get("enabled");
            if (enabled == null) {
                return error("enabled参数不能为空", 400);
            }
            
            modelGroupService.enableGroup(id, enabled);
            log.info("{}模型组成功: {}", enabled ? "启用" : "禁用", id);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("enabled", enabled);
            result.put("message", enabled ? "启用成功" : "禁用成功");
            return success(result);
        } catch (Exception e) {
            log.error("启用/禁用模型组失败, id: {}", id, e);
            return error("启用/禁用模型组失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}/instances")
    public ResponseEntity<Map<String, Object>> getGroupInstances(@PathVariable Long id) {
        try {
            ModelGroup group = modelGroupService.getGroupById(id);
            if (group == null) {
                return error("模型组不存在", 404);
            }
            
            List<ModelInstance> instances = instanceService.getInstancesByGroupId(id);
            List<ModelInstanceDTO> dtoList = instances.stream()
                    .map(this::convertInstanceToDTO)
                    .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("groupId", id);
            result.put("groupName", group.getName());
            result.put("items", dtoList);
            result.put("total", dtoList.size());
            return success(result);
        } catch (Exception e) {
            log.error("获取模型组实例失败, id: {}", id, e);
            return error("获取模型组实例失败: " + e.getMessage());
        }
    }
    
    private ModelGroupDTO convertToDTO(ModelGroup group) {
        List<ModelInstance> instances = instanceService.getInstancesByGroupId(group.getId());
        List<ModelInstance> healthyInstances = instanceService.getHealthyInstancesByGroupId(group.getId());
        
        return ModelGroupDTO.builder()
                .id(group.getId())
                .name(group.getName())
                .modelType(group.getModelType())
                .description(group.getDescription())
                .schedulingStrategy(group.getSchedulingStrategy())
                .defaultApiKey(group.getDefaultApiKey())
                .defaultBaseUrl(group.getDefaultBaseUrl())
                .defaultTimeoutMs(group.getDefaultTimeoutMs())
                .defaultMaxRetries(group.getDefaultMaxRetries())
                .priority(group.getPriority())
                .enabled(group.getEnabled())
                .isDefault(group.getIsDefault())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .totalInstances(instances.size())
                .healthyInstances(healthyInstances.size())
                .availableInstances(healthyInstances.size())
                .build();
    }
    
    private ModelInstanceDTO convertInstanceToDTO(ModelInstance instance) {
        return ModelInstanceDTO.builder()
                .id(instance.getId())
                .groupId(instance.getGroupId())
                .name(instance.getName())
                .apiEndpoint(instance.getApiEndpoint())
                .modelName(instance.getModelName())
                .weight(instance.getWeight())
                .maxConcurrent(instance.getMaxConcurrent())
                .currentConnections(instance.getCurrentConnections())
                .status(instance.getStatus())
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .build();
    }
    
    private ResponseEntity<Map<String, Object>> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", data);
        return ResponseEntity.ok(result);
    }
    
    private ResponseEntity<Map<String, Object>> error(String message) {
        return error(message, 500);
    }
    
    private ResponseEntity<Map<String, Object>> error(String message, int code) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("message", message);
        result.put("data", null);
        return ResponseEntity.status(code >= 500 ? 500 : code).body(result);
    }
}
