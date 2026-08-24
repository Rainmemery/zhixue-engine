package com.rain.zhixueai.dispatcher.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 实例状态枚举
 * 定义模型实例的运行状态
 * 
 * @author rain
 * @since 2026-04-19
 */
public enum InstanceStatus {
    
    HEALTHY("HEALTHY", "健康"),

    DEGRADED("DEGRADED", "降级"),

    UNHEALTHY("UNHEALTHY", "不健康"),

    DISABLED("DISABLED", "已禁用");
    
    private final String code;
    private final String description;
    
    InstanceStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @JsonCreator
    public static InstanceStatus fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return DISABLED;
        }
        
        for (InstanceStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        
        for (InstanceStatus status : values()) {
            if (status.name().equalsIgnoreCase(code)) {
                return status;
            }
        }
        
        return DISABLED;
    }
    
    @JsonValue
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isAvailable() {
        return this == HEALTHY || this == DEGRADED;
    }
    
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
    
    @Override
    public String toString() {
        return code + "(" + description + ")";
    }
}
