package com.rain.zhixueai.dispatcher.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 健康状态枚举
 * 定义实例的健康检查状态
 * 
 * @author rain
 * @since 2026-04-19
 */
public enum HealthState {
    
    HEALTHY("HEALTHY", "健康"),

    DEGRADED("DEGRADED", "降级"),

    UNHEALTHY("UNHEALTHY", "不健康");
    
    private final String code;
    private final String description;
    
    HealthState(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @JsonCreator
    public static HealthState fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return UNHEALTHY;
        }
        
        for (HealthState state : values()) {
            if (state.code.equalsIgnoreCase(code)) {
                return state;
            }
        }
        
        for (HealthState state : values()) {
            if (state.name().equalsIgnoreCase(code)) {
                return state;
            }
        }
        
        return UNHEALTHY;
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
