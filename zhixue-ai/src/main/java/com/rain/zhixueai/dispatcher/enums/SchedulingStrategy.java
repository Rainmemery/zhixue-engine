package com.rain.zhixueai.dispatcher.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 调度策略枚举
 * 定义模型组可用的调度策略类型
 * 
 * @author rain
 * @since 2026-04-19
 */
public enum SchedulingStrategy {
    
    ROUND_ROBIN("ROUND_ROBIN", "轮询调度"),

    WEIGHTED_ROUND_ROBIN("WEIGHTED_ROUND_ROBIN", "加权轮询调度"),

    LEAST_CONNECTION("LEAST_CONNECTION", "最少连接调度");
    
    private final String code;
    private final String description;
    
    SchedulingStrategy(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @JsonCreator
    public static SchedulingStrategy fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return ROUND_ROBIN;
        }
        
        for (SchedulingStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code)) {
                return strategy;
            }
        }
        
        for (SchedulingStrategy strategy : values()) {
            if (strategy.name().equalsIgnoreCase(code)) {
                return strategy;
            }
        }
        
        return ROUND_ROBIN;
    }
    
    @JsonValue
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
    
    @Override
    public String toString() {
        return code + "(" + description + ")";
    }
}
