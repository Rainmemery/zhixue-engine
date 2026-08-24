package com.rain.zhixueai.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI消息实体类
 * 用于表示对话中的单条消息
 * 
 * @author rain
 * @since 2026-02-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {
    
    /**
     * 消息角色 (user/assistant/system)
     */
    private String role;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 构造函数 - 自动设置当前时间戳
     * 
     * @param role 消息角色
     * @param content 消息内容
     */
    public AiMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * 判断是否为用户消息
     * 
     * @return 是否为用户消息
     */
    public boolean isUserMessage() {
        return "user".equalsIgnoreCase(role);
    }
    
    /**
     * 判断是否为AI助手消息
     * 
     * @return 是否为AI助手消息
     */
    public boolean isAssistantMessage() {
        return "assistant".equalsIgnoreCase(role);
    }
    
    /**
     * 判断是否为系统消息
     * 
     * @return 是否为系统消息
     */
    public boolean isSystemMessage() {
        return "system".equalsIgnoreCase(role);
    }
    
    /**
     * 获取格式化的时间字符串
     * 
     * @return ISO格式的时间字符串
     */
    public String getFormattedTimestamp() {
        return timestamp != null ? timestamp.toString() : "";
    }
}