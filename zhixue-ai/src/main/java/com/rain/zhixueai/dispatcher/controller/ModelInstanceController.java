package com.rain.zhixueai.dispatcher.controller;

import com.rain.zhixueai.dispatcher.dto.ModelInstanceCreateRequest;
import com.rain.zhixueai.dispatcher.dto.ModelInstanceUpdateRequest;
import com.rain.zhixueai.dispatcher.entity.InstanceHealthStatus;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.health.HealthCheckResult;
import com.rain.zhixueai.dispatcher.health.InstanceHealthChecker;
import com.rain.zhixueai.dispatcher.service.ModelInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dispatcher/instances")
@RequiredArgsConstructor
@Slf4j
public class ModelInstanceController {
    
    private final ModelInstanceService instanceService;
    private final InstanceHealthChecker healthChecker;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllInstances() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ModelInstance> instances = instanceService.getAllInstances();
            log.info("获取所有实例列表, 共{}个实例", instances.size());
            
            response.put("code", 200);
            response.put("data", Map.of("items", instances, "total", instances.size()));
            response.put("message", "success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取所有实例列表失败", e);
            response.put("code", 500);
            response.put("data", null);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getInstanceById(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            ModelInstance instance = instanceService.getInstanceById(id);
            if (instance == null) {
                log.warn("实例不存在, id: {}", id);
                response.put("code", 404);
                response.put("data", null);
                response.put("message", "实例不存在");
                return ResponseEntity.status(404).body(response);
            }
            
            log.info("获取实例成功, id: {}, name: {}", id, instance.getName());
            response.put("code", 200);
            response.put("data", instance);
            response.put("message", "success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取实例失败, id: {}", id, e);
            response.put("code", 500);
            response.put("data", null);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/group/{groupId}")
    public ResponseEntity<Map<String, Object>> getInstancesByGroup(@PathVariable Long groupId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ModelInstance> instances = instanceService.getInstancesByGroupId(groupId);
            log.info("获取模型组实例列表, groupId: {}, 共{}个实例", groupId, instances.size());
            
            response.put("code", 200);
            response.put("data", Map.of("items", instances, "total", instances.size(), "groupId", groupId));
            response.put("message", "success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取模型组实例列表失败, groupId: {}", groupId, e);
            response.put("code", 500);
            response.put("data", null);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> createInstance(@Valid @RequestBody ModelInstanceCreateRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            ModelInstance instance = instanceService.createInstance(request);
            log.info("创建实例成功, id: {}, name: {}, groupId: {}", 
                    instance.getId(), instance.getName(), instance.getGroupId());
            
            response.put("code", 200);
            response.put("data", instance);
            response.put("message", "创建成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建实例失败, name: {}, groupId: {}", request.getName(), request.getGroupId(), e);
            response.put("code", 500);
            response.put("data", null);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateInstance(
            @PathVariable Long id, 
            @Valid @RequestBody ModelInstanceUpdateRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            ModelInstance instance = instanceService.updateInstance(id, request);
            if (instance == null) {
                log.warn("更新实例失败, 实例不存在, id: {}", id);
                response.put("code", 404);
                response.put("data", null);
                response.put("message", "实例不存在");
                return ResponseEntity.status(404).body(response);
            }
            
            log.info("更新实例成功, id: {}, name: {}", id, instance.getName());
            response.put("code", 200);
            response.put("data", instance);
            response.put("message", "更新成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新实例失败, id: {}", id, e);
            response.put("code", 500);
            response.put("data", null);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteInstance(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            ModelInstance instance = instanceService.getInstanceById(id);
            if (instance == null) {
                log.warn("删除实例失败, 实例不存在, id: {}", id);
                response.put("code", 404);
                response.put("data", null);
                response.put("message", "实例不存在");
                return ResponseEntity.status(404).body(response);
            }
            
            instanceService.deleteInstance(id);
            log.info("删除实例成功, id: {}, name: {}", id, instance.getName());
            
            response.put("code", 200);
            response.put("data", null);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除实例失败, id: {}", id, e);
            response.put("code", 500);
            response.put("data", null);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            String status = body.get("status");
            if (status == null || status.isBlank()) {
                log.warn("更新实例状态失败, 缺少status参数, id: {}", id);
                response.put("code", 400);
                response.put("data", null);
                response.put("message", "缺少status参数");
                return ResponseEntity.badRequest().body(response);
            }
            
            ModelInstance instance = instanceService.getInstanceById(id);
            if (instance == null) {
                log.warn("更新实例状态失败, 实例不存在, id: {}", id);
                response.put("code", 404);
                response.put("data", null);
                response.put("message", "实例不存在");
                return ResponseEntity.status(404).body(response);
            }
            
            instanceService.updateInstanceStatus(id, status);
            log.info("更新实例状态成功, id: {}, status: {}", id, status);
            
            response.put("code", 200);
            response.put("data", Map.of("id", id, "status", status));
            response.put("message", "状态更新成功");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("更新实例状态失败, 无效的状态值, id: {}", id, e);
            response.put("code", 400);
            response.put("data", null);
            response.put("message", "无效的状态值: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("更新实例状态失败, id: {}", id, e);
            response.put("code", 500);
            response.put("data", null);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/{id}/health")
    public ResponseEntity<Map<String, Object>> getInstanceHealth(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            ModelInstance instance = instanceService.getInstanceById(id);
            if (instance == null) {
                log.warn("获取实例健康状态失败, 实例不存在, id: {}", id);
                response.put("code", 404);
                response.put("data", null);
                response.put("message", "实例不存在");
                return ResponseEntity.status(404).body(response);
            }
            
            InstanceHealthStatus healthStatus = instanceService.getInstanceHealth(id);
            log.info("获取实例健康状态成功, id: {}, healthState: {}", 
                    id, healthStatus != null ? healthStatus.getHealthState() : "null");
            
            response.put("code", 200);
            response.put("data", healthStatus);
            response.put("message", "success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取实例健康状态失败, id: {}", id, e);
            response.put("code", 500);
            response.put("data", null);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/{id}/health-check")
    public ResponseEntity<Map<String, Object>> performHealthCheck(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            ModelInstance instance = instanceService.getInstanceById(id);
            if (instance == null) {
                log.warn("手动健康检查失败, 实例不存在, id: {}", id);
                response.put("code", 404);
                response.put("data", null);
                response.put("message", "实例不存在");
                return ResponseEntity.status(404).body(response);
            }
            
            HealthCheckResult result = healthChecker.checkHealth(instance);
            log.info("手动健康检查完成, id: {}, healthy: {}, responseTimeMs: {}", 
                    id, result.isHealthy(), result.getResponseTimeMs());
            
            Map<String, Object> data = new HashMap<>();
            data.put("instanceId", id);
            data.put("instanceName", instance.getName());
            data.put("healthy", result.isHealthy());
            data.put("responseTimeMs", result.getResponseTimeMs());
            data.put("errorMessage", result.getErrorMessage());
            data.put("checkTime", result.getCheckTime());
            
            response.put("code", 200);
            response.put("data", data);
            response.put("message", result.isHealthy() ? "健康检查通过" : "健康检查失败");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("手动健康检查失败, id: {}", id, e);
            response.put("code", 500);
            response.put("data", null);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
