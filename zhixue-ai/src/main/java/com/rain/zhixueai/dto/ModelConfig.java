package com.rain.zhixueai.dto;

import com.rain.zhixueai.enums.AiModelType;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 模型配置实体类
 * 用于配置和管理不同AI模型的连接参数
 * 
 * @author rain
 * @since 2026-02-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {
    
    /**
     * 模型类型
     */
    private AiModelType modelType;
    
    /**
     * 模型名称
     */
    private String modelName;
    
    /**
     * API密钥
     */
    private String apiKey;
    
    /**
     * 请求基础URL
     */
    private String baseUrl;
    
    /**
     * 超时时间(毫秒)
     */
    private Integer timeoutMs;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetries;
    
    /**
     * 连接池大小
     */
    private Integer connectionPoolSize;
    
    /**
     * 获取超时时间，默认30000毫秒
     * 
     * @return 超时时间
     */
    public Integer getTimeoutMsOrDefault() {
        return timeoutMs != null ? timeoutMs : 30000;
    }
    
    /**
     * 获取最大重试次数，默认3次
     * 
     * @return 最大重试次数
     */
    public Integer getMaxRetriesOrDefault() {
        return maxRetries != null ? maxRetries : 3;
    }
    
    /**
     * 获取连接池大小，默认10
     * 
     * @return 连接池大小
     */
    public Integer getConnectionPoolSizeOrDefault() {
        return connectionPoolSize != null ? connectionPoolSize : 10;
    }
    
    /**
     * 获取启用状态，默认true
     * 
     * @return 是否启用
     */
    public Boolean getEnabledOrDefault() {
        return enabled != null ? enabled : true;
    }
    
    /**
     * 验证配置是否完整
     * 
     * @return 配置是否有效
     */
    public boolean isValid() {
        if (modelType == null) {
            return false;
        }
        
        if (modelName == null || modelName.trim().isEmpty()) {
            return false;
        }
        
        // Ollama不需要API Key
        if (AiModelType.OPENAI.equals(modelType)) {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return false;
            }
        }
        
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取默认的Ollama配置
     * 
     * @return Ollama配置
     */
    public static ModelConfig getDefaultOllamaConfig() {
        return ModelConfig.builder()
                .modelType(AiModelType.OLLAMA)
                .modelName("qwen2.5-coder:3b-instruct-q5_K_M")
                .baseUrl("http://localhost:11434")
                .timeoutMs(300000)
                .maxRetries(3)
                .enabled(true)
                .build();
    }
    
    /**
     * 获取默认的OpenAI配置
     * 
     * @return OpenAI配置
     */
    public static ModelConfig getDefaultOpenAIConfig() {
        return ModelConfig.builder()
                .modelType(AiModelType.OPENAI)
                .modelName("Qwen3-Coder-Plus")
                .baseUrl("https://apis.iflow.cn/v1/chat/completions")
                .apiKey("sk-00439e2d8ebf391f5c033630712c8e52") // 需要配置实际的API Key
                .timeoutMs(300000)
                .maxRetries(3)
                .enabled(true) // 默认不启用
                .build();
    }
}