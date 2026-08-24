package com.rain.zhixueai.dispatcher.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 调度操作枚举
 * 定义调度日志中记录的操作类型
 * 
 * @author rain
 * @since 2026-04-19
 */
public enum SchedulingAction {
    
    SELECT("select", "选择实例"),
    
    FAILOVER("failover", "故障转移"),
    
    RECOVERY("recovery", "恢复实例"),
    
    HEALTH_CHECK("health_check", "健康检查");
    
    private final String code;
    private final String description;
    
    SchedulingAction(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @JsonCreator
    public static SchedulingAction fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return SELECT;
        }
        
        for (SchedulingAction action : values()) {
            if (action.code.equalsIgnoreCase(code)) {
                return action;
            }
        }
        
        for (SchedulingAction action : values()) {
            if (action.name().equalsIgnoreCase(code)) {
                return action;
            }
        }
        
        return SELECT;
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
