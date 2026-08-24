package com.rain.zhixueai.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.rain.zhixueai.dto.AiMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON工具类
 * 提供JSON处理相关的通用方法
 * 
 * @author rain
 * @since 2026-02-05
 */
@Slf4j
public class JsonUtils {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    /**
     * 将JSON字符串转换为AiMessage列表
     * 
     * @param jsonStr JSON字符串
     * @return AiMessage列表
     * @throws IllegalArgumentException 当JSON格式不正确时抛出
     */
    public static List<AiMessage> parseMessagesFromJson(String jsonStr) throws IllegalArgumentException {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON字符串不能为空");
        }
        
        try {
            JSONArray jsonArray = JSON.parseArray(jsonStr);
            List<AiMessage> messages = new ArrayList<>();
            
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jsonObj = jsonArray.getJSONObject(i);
                AiMessage message = parseMessageFromJsonObject(jsonObj);
                messages.add(message);
            }
            
            return messages;
        } catch (Exception e) {
            log.error("解析JSON消息失败: {}", jsonStr, e);
            throw new IllegalArgumentException("JSON格式不正确: " + e.getMessage());
        }
    }
    
    /**
     * 将JSONObject转换为AiMessage
     * 
     * @param jsonObj JSONObject对象
     * @return AiMessage对象
     * @throws IllegalArgumentException 当JSON对象格式不正确时抛出
     */
    public static AiMessage parseMessageFromJsonObject(JSONObject jsonObj) throws IllegalArgumentException {
        if (jsonObj == null) {
            throw new IllegalArgumentException("JSON对象不能为空");
        }
        
        String role = jsonObj.getString("role");
        String content = jsonObj.getString("content");
        String timestampStr = jsonObj.getString("timestamp");
        
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("消息角色不能为空");
        }
        
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        
        LocalDateTime timestamp = LocalDateTime.now();
        if (timestampStr != null && !timestampStr.trim().isEmpty()) {
            try {
                timestamp = LocalDateTime.parse(timestampStr, ISO_FORMATTER);
            } catch (Exception e) {
                log.warn("解析时间戳失败，使用当前时间: {}", timestampStr);
            }
        }
        
        return new AiMessage(role, content, timestamp);
    }
    
    /**
     * 将AiMessage列表转换为JSON字符串
     * 
     * @param messages AiMessage列表
     * @return JSON字符串
     */
    public static String toJsonString(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }
        
        JSONArray jsonArray = new JSONArray();
        for (AiMessage message : messages) {
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("role", message.getRole());
            jsonObj.put("content", message.getContent());
            jsonObj.put("timestamp", message.getTimestamp().format(ISO_FORMATTER));
            jsonArray.add(jsonObj);
        }
        
        return jsonArray.toJSONString();
    }
    
    /**
     * 将对象转换为JSON字符串
     * 
     * @param obj 待转换的对象
     * @return JSON字符串
     */
    public static String objectToJson(Object obj) {
        if (obj == null) {
            return "{}";
        }
        return JSON.toJSONString(obj);
    }
    
    /**
     * 将JSON字符串转换为指定类型的对象
     * 
     * @param jsonStr JSON字符串
     * @param clazz 目标类型Class
     * @param <T> 泛型类型
     * @return 转换后的对象
     * @throws IllegalArgumentException 当转换失败时抛出
     */
    public static <T> T jsonToObject(String jsonStr, Class<T> clazz) throws IllegalArgumentException {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON字符串不能为空");
        }
        
        try {
            return JSON.parseObject(jsonStr, clazz);
        } catch (Exception e) {
            log.error("JSON转换对象失败: {}, 类型: {}", jsonStr, clazz.getSimpleName(), e);
            throw new IllegalArgumentException("JSON转换失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证JSON字符串是否为有效的JSON数组
     * 
     * @param jsonStr JSON字符串
     * @return 是否为有效JSON数组
     */
    public static boolean isValidJsonArray(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return false;
        }
        
        try {
            JSONArray.parse(jsonStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 验证JSON字符串是否为有效的JSON对象
     * 
     * @param jsonStr JSON字符串
     * @return 是否为有效JSON对象
     */
    public static boolean isValidJsonObject(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return false;
        }
        
        try {
            JSONObject.parse(jsonStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从JSON字符串中提取指定字段的值
     * 
     * @param jsonStr JSON字符串
     * @param fieldName 字段名
     * @return 字段值，如果不存在返回null
     */
    public static String extractField(String jsonStr, String fieldName) {
        if (jsonStr == null || jsonStr.trim().isEmpty() || fieldName == null) {
            return null;
        }
        
        try {
            JSONObject jsonObj = JSON.parseObject(jsonStr);
            return jsonObj.getString(fieldName);
        } catch (Exception e) {
            log.warn("提取JSON字段失败: {}, 字段: {}", jsonStr, fieldName, e);
            return null;
        }
    }
    
    /**
     * 转义JSON特殊字符
     * 
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    public static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t")
                   .replace("/", "\\/");
    }
    
    /**
     * 反转义JSON特殊字符
     * 
     * @param input 输入字符串
     * @return 反转义后的字符串
     */
    public static String unescapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\\"", "\"")
                   .replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t")
                   .replace("\\/", "/")
                   .replace("\\\\", "\\");
    }
}