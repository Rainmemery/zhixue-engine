package com.rain.zhixueai.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI角色枚举
 * 定义不同的AI助手角色类型
 * 
 * @author rain
 * @since 2026-02-05
 */
public enum AiRole {
    
    /**
     * 解释者角色 - 专门负责代码解释和概念讲解
     */
    EXPLAINER("explainer", "代码解释者"),

    /**
     * 评审者角色 - 进行代码质量和规范评审
     */
    REVIEWER("reviewer", "代码评审者 - 进行代码质量和规范评审"),

    /**
     * 提问者角色 - 智能引导学习和思考
     */
    QUESTIONER("questioner", "智能提问者 - 通过提问引导用户深入思考和学习"),

    AGENT_QUESTIONER("agent_questioner", "智能提问 Agent - 通过工具调用提供个性化学习引导");
    
    private final String code;
    private final String description;
    
    AiRole(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * JSON反序列化时使用此方法
     * 支持通过code字段值来匹配枚举
     * 
     * @param code 角色编码
     * @return 对应的角色枚举
     */
    @JsonCreator
    public static AiRole fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        
        for (AiRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        
        for (AiRole role : values()) {
            if (role.name().equalsIgnoreCase(code)) {
                return role;
            }
        }
        
        return null;
    }
    
    /**
     * JSON序列化时使用code字段值
     * 
     * @return 角色编码
     */
    @JsonValue
    public String getCode() {
        return code;
    }
    
    /**
     * 获取角色描述
     * 
     * @return 角色描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 判断是否为有效角色
     * 
     * @param code 角色编码
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