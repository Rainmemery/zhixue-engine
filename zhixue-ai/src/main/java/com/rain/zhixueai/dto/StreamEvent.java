package com.rain.zhixueai.dto;

import com.rain.zhixueai.enums.StreamEventType;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 流式事件实体类
 * 用于封装流式传输中的单个事件
 * 
 * @author rain
 * @since 2026-02-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {
    
    /**
     * 事件类型
     */
    private StreamEventType eventType;
    
    /**
     * 事件数据
     */
    private String data;
    
    /**
     * 事件ID
     */
    private String id;
    
    /**
     * 重试时间(毫秒)
     */
    private Long retry;
    
    /**
     * 构造函数 - 创建数据事件
     * 
     * @param eventType 事件类型
     * @param data 事件数据
     */
    public StreamEvent(StreamEventType eventType, String data) {
        this.eventType = eventType;
        this.data = data;
    }
    
    /**
     * 构造函数 - 创建带ID的数据事件
     * 
     * @param eventType 事件类型
     * @param data 事件数据
     * @param id 事件ID
     */
    public StreamEvent(StreamEventType eventType, String data, String id) {
        this.eventType = eventType;
        this.data = data;
        this.id = id;
    }
    
    /**
     * 转换为标准化SSE格式字符串（仅data字段）
     * 格式：data: {"type":"eventType","content":"data"}
     * 
     * @return SSE格式的事件字符串
     */
    public String toSseFormat() {
        StringBuilder sb = new StringBuilder();
        
        // 直接构造标准化的data格式，不包含event字段
        sb.append("data: {");
        sb.append("\"type\":\"").append(eventType != null ? eventType.getCode() : "text").append("\",");
        sb.append("\"content\":\"").append(data != null ? escapeJson(data) : "").append("\"");
        
        // 如果有ID，添加到JSON中
        if (id != null && !id.isEmpty()) {
            sb.append(",\"id\":\"").append(escapeJson(id)).append("\"");
        }
        
        // 如果有重试时间，添加到JSON中
        if (retry != null && retry > 0) {
            sb.append(",\"retry\":").append(retry);
        }
        
        sb.append("}\n\n");
        
        return sb.toString();
    }
    
    /**
     * 创建评论事件
     * 
     * @param comment 评论内容
     * @return 评论事件
     */
    public static StreamEvent createComment(String comment) {
        StreamEvent event = new StreamEvent();
        event.setData(comment);
        return event;
    }
    
    /**
     * 创建心跳事件
     * 
     * @return 心跳事件
     */
    public static StreamEvent createHeartbeat() {
        StreamEvent event = new StreamEvent();
        event.setEventType(StreamEventType.TEXT);
        event.setData(": heartbeat");
        return event;
    }
    
    /**
     * 判断是否为心跳事件
     * 
     * @return 是否为心跳事件
     */
    public boolean isHeartbeat() {
        return data != null && data.startsWith(":");
    }
    
    /**
     * 转义JSON特殊字符
     * 
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t")
                   .replace("\f", "\\f")
                   .replace("\b", "\\b");
    }
}