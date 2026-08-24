package com.rain.zhixueai.enums;

/**
 * 流式事件类型枚举
 * 定义流式响应中的不同事件类型
 * 
 * @author rain
 * @since 2026-02-05
 */
public enum StreamEventType {
    
    /**
     * 流式开始事件
     */
    STREAM_START("stream_start", "流式开始"),
    
    /**
     * 文本内容事件
     */
    TEXT("text", "文本内容"),
    
    /**
     * 思考过程事件
     */
    THINKING("thinking", "思考过程"),
    
    /**
     * 代码内容事件
     */
    CODE("code", "代码内容"),
    
    /**
     * 流式进度事件
     */
    STREAM_PROGRESS("stream_progress", "流式进度"),
    
    /**
     * 工具调用事件
     */
    TOOL_CALL("tool_call", "工具调用"),
    
    /**
     * 工具结果事件
     */
    TOOL_RESULT("tool_result", "工具结果"),
    
    /**
     * 完成事件
     */
    COMPLETE("complete", "完成事件");
    
    private final String code;
    private final String description;
    
    StreamEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * 根据编码获取事件类型枚举
     * 
     * @param code 事件类型编码
     * @return 对应的事件类型枚举，如果未找到返回TEXT
     */
    public static StreamEventType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return TEXT;
        }
        
        for (StreamEventType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return TEXT;
    }
    
    /**
     * 获取事件类型编码
     * 
     * @return 事件类型编码
     */
    public String getCode() {
        return code;
    }
    
    /**
     * 获取事件类型描述
     * 
     * @return 事件类型描述
     */
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return code + "(" + description + ")";
    }
}