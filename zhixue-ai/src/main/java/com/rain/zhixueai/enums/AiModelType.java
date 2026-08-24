package com.rain.zhixueai.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI模型类型枚举
 * 定义支持的不同AI模型类型
 * 
 * @author rain
 * @since 2026-02-05
 */
public enum AiModelType {
    
    OLLAMA("ollama", "Ollama本地模型"),
    
    OPENAI("openai", "OpenAI标准API"),
    
    EMBEDDING("embedding", "Embedding向量模型");
    
    private final String code;
    private final String description;
    
    AiModelType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * JSON反序列化时使用此方法
     * 支持通过code字段值来匹配枚举
     * 
     * @param code 模型类型编码
     * @return 对应的模型类型枚举
     */
    @JsonCreator
    public static AiModelType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return OLLAMA;
        }
        
        for (AiModelType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        
        for (AiModelType type : values()) {
            if (type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        
        return OLLAMA;
    }
    
    /**
     * JSON序列化时使用code字段值
     * 
     * @return 模型类型编码
     */
    @JsonValue
    public String getCode() {
        return code;
    }
    
    /**
     * 获取模型类型描述
     * 
     * @return 模型类型描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 判断是否为有效模型类型
     * 
     * @param code 模型类型编码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
    
    @Override
    public String toString() {
        return code + "(" + description + ")";
    }
}