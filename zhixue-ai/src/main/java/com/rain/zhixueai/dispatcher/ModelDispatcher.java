package com.rain.zhixueai.dispatcher;

import com.rain.zhixueai.client.AiClient;
import com.rain.zhixueai.client.OllamaClient;
import com.rain.zhixueai.client.OpenAIClient;
import com.rain.zhixueai.dispatcher.entity.ModelGroup;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.health.HealthCheckResult;
import com.rain.zhixueai.dispatcher.health.InstanceHealthChecker;
import com.rain.zhixueai.dispatcher.service.ModelGroupDispatcherService;
import com.rain.zhixueai.dispatcher.service.ModelGroupService;
import com.rain.zhixueai.dispatcher.service.ModelInstanceService;
import com.rain.zhixueai.dto.ModelConfig;
import com.rain.zhixueai.enums.AiModelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型调度器
 * 负责管理和调度不同的AI模型客户端
 * 支持基于模型组的调度和传统的模型类型调度
 * 
 * @author rain
 * @since 2026-02-05
 */
@Slf4j
@Component
public class ModelDispatcher {
    
    @Autowired
    private OllamaClient ollamaClient;
    
    @Autowired
    private OpenAIClient openAIClient;
    
    @Autowired
    private ModelGroupService modelGroupService;
    
    @Autowired
    private ModelGroupDispatcherService groupDispatcherService;
    
    @Autowired
    private ModelInstanceService instanceService;
    
    @Autowired
    private InstanceHealthChecker healthChecker;
    
    private final Map<AiModelType, AiClient> clientMap = new ConcurrentHashMap<>();
    private volatile AiModelType currentModelType = AiModelType.OPENAI;
    
    private volatile Long currentGroupId;
    private volatile Long currentInstanceId;
    
    @PostConstruct
    public void init() {
        clientMap.put(AiModelType.OLLAMA, ollamaClient);
        clientMap.put(AiModelType.OPENAI, openAIClient);
        
        Optional<ModelGroup> defaultGroup = modelGroupService.getDefaultGroup();
        if (defaultGroup.isPresent()) {
            this.currentGroupId = defaultGroup.get().getId();
            log.info("模型调度器初始化完成，默认模型组: {}", defaultGroup.get().getName());
        } else {
            log.info("模型调度器初始化完成，当前默认模型类型: {}", currentModelType);
        }
    }
    
    /**
     * 获取当前可用的模型实例（基于模型组调度）
     * 当没有可用的模型实例时返回 null，不会抛出异常
     */
    public ModelInstance getAvailableInstance() {
        if (currentGroupId == null) {
            log.warn("未配置模型组，尝试使用默认模型客户端");
            return null;
        }

        try {
            ModelInstance instance = groupDispatcherService.selectInstance(currentGroupId);
            if (instance == null) {
                log.warn("模型组 {} 没有可用的模型实例，尝试使用默认模型客户端", currentGroupId);
                return null;
            }

            this.currentInstanceId = instance.getId();
            return instance;
        } catch (Exception e) {
            log.error("获取可用模型实例失败: groupId={}, error={}", currentGroupId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据模型组ID获取可用实例
     * 当没有可用的模型实例时返回 null，不会抛出异常
     */
    public ModelInstance getAvailableInstance(Long groupId) {
        if (groupId == null) {
            return null;
        }
        try {
            return groupDispatcherService.selectInstance(groupId);
        } catch (Exception e) {
            log.error("获取可用模型实例失败: groupId={}, error={}", groupId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 切换到指定模型组
     */
    public void switchToGroup(Long groupId) {
        ModelGroup group = modelGroupService.getGroupById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("模型组不存在: " + groupId);
        }
        if (!group.getEnabledOrDefault()) {
            throw new IllegalStateException("模型组未启用: " + group.getName());
        }
        
        this.currentGroupId = groupId;
        log.info("已切换到模型组: {}", group.getName());
    }
    
    /**
     * 执行健康检查
     */
    public HealthCheckResult performHealthCheck(Long instanceId) {
        return healthChecker.checkHealth(instanceId);
    }
    
    /**
     * 获取当前活跃的AI客户端（兼容旧接口）
     */
    public AiClient getCurrentClient() {
        AiClient client = clientMap.get(currentModelType);
        if (client == null) {
            log.warn("未找到{}客户端，回退到Ollama客户端", currentModelType);
            return ollamaClient;
        }
        return client;
    }
    
    /**
     * 根据模型类型获取指定的AI客户端
     */
    public AiClient getClient(AiModelType modelType) {
        AiClient client = clientMap.get(modelType);
        if (client == null) {
            throw new IllegalArgumentException("不支持的模型类型: " + modelType);
        }
        return client;
    }
    
    /**
     * 切换当前使用的模型类型
     */
    public synchronized void switchModel(AiModelType modelType) throws IllegalArgumentException {
        if (!clientMap.containsKey(modelType)) {
            throw new IllegalArgumentException("不支持的模型类型: " + modelType);
        }
        
        AiClient targetClient = clientMap.get(modelType);
        if (!targetClient.isAvailable()) {
            throw new IllegalStateException("目标模型不可用: " + modelType);
        }
        
        AiModelType previousModel = this.currentModelType;
        this.currentModelType = modelType;
        
        log.info("模型已从 {} 切换到 {}", previousModel, modelType);
    }
    
    /**
     * 动态配置模型
     */
    public synchronized void configureModel(ModelConfig modelConfig) throws IllegalArgumentException {
        if (modelConfig == null) {
            throw new IllegalArgumentException("模型配置不能为空");
        }
        
        if (!modelConfig.isValid()) {
            throw new IllegalArgumentException("模型配置无效: " + modelConfig);
        }
        
        AiModelType modelType = modelConfig.getModelType();
        if (!clientMap.containsKey(modelType)) {
            throw new IllegalArgumentException("不支持的模型类型: " + modelType);
        }
        
        switch (modelType) {
            case OLLAMA:
                this.ollamaClient = new OllamaClient(modelConfig);
                clientMap.put(AiModelType.OLLAMA, this.ollamaClient);
                break;
            case OPENAI:
                this.openAIClient = new OpenAIClient(modelConfig);
                clientMap.put(AiModelType.OPENAI, this.openAIClient);
                break;
        }
        
        log.info("模型配置已更新: {}", modelConfig.getModelName());
    }
    
    /**
     * 检查指定模型是否可用
     */
    public boolean isModelAvailable(AiModelType modelType) {
        AiClient client = clientMap.get(modelType);
        return client != null && client.isAvailable();
    }
    
    /**
     * 获取当前模型类型
     */
    public AiModelType getCurrentModelType() {
        return currentModelType;
    }
    
    /**
     * 获取所有支持的模型类型
     */
    public AiModelType[] getSupportedModels() {
        return clientMap.keySet().toArray(new AiModelType[0]);
    }
    
    /**
     * 获取模型状态信息
     */
    public Map<String, Object> getModelStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        
        status.put("currentModel", currentModelType.getCode());
        status.put("supportedModels", getSupportedModelNames());
        
        if (currentGroupId != null) {
            ModelGroup group = modelGroupService.getGroupById(currentGroupId);
            status.put("currentGroup", group != null ? group.getName() : null);
            status.put("currentGroupId", currentGroupId);
        }
        
        if (currentInstanceId != null) {
            status.put("currentInstanceId", currentInstanceId);
        }
        
        Map<String, Boolean> availability = new java.util.HashMap<>();
        for (AiModelType modelType : clientMap.keySet()) {
            availability.put(modelType.getCode(), isModelAvailable(modelType));
        }
        status.put("availability", availability);
        
        List<ModelGroup> allGroups = modelGroupService.getAllGroups();
        status.put("totalGroups", allGroups.size());
        status.put("enabledGroups", allGroups.stream().filter(g -> g.getEnabledOrDefault()).count());
        
        return status;
    }
    
    private String[] getSupportedModelNames() {
        return clientMap.keySet().stream()
                .map(AiModelType::getCode)
                .toArray(String[]::new);
    }
    
    /**
     * 自动选择最佳可用模型
     */
    public AiModelType selectBestAvailableModel() {
        if (isModelAvailable(AiModelType.OLLAMA)) {
            return AiModelType.OLLAMA;
        }
        
        if (isModelAvailable(AiModelType.OPENAI)) {
            return AiModelType.OPENAI;
        }
        
        throw new IllegalStateException("没有可用的AI模型");
    }
    
    /**
     * 安全地切换到最佳可用模型
     */
    public boolean switchToBestAvailableModel() {
        try {
            AiModelType bestModel = selectBestAvailableModel();
            if (!bestModel.equals(currentModelType)) {
                switchModel(bestModel);
            }
            return true;
        } catch (Exception e) {
            log.error("切换到最佳模型失败", e);
            return false;
        }
    }
    
    /**
     * 重置为默认模型配置
     */
    public synchronized void resetToDefault() {
        this.ollamaClient = new OllamaClient(ModelConfig.getDefaultOllamaConfig());
        this.openAIClient = new OpenAIClient(ModelConfig.getDefaultOpenAIConfig());
        
        clientMap.put(AiModelType.OLLAMA, this.ollamaClient);
        clientMap.put(AiModelType.OPENAI, this.openAIClient);
        
        this.currentModelType = AiModelType.OLLAMA;
        
        Optional<ModelGroup> defaultGroup = modelGroupService.getDefaultGroup();
        if (defaultGroup.isPresent()) {
            this.currentGroupId = defaultGroup.get().getId();
        }
        
        log.info("模型配置已重置为默认设置");
    }
}
