package com.rain.zhixueai.dispatcher.dto;

import com.rain.zhixueai.dispatcher.enums.SchedulingStrategy;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 更新模型组请求DTO
 * 用于接收更新模型组的请求数据
 * 
 * @author rain
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelGroupUpdateRequest {
    
    @Size(max = 100, message = "模型组名称长度不能超过100个字符")
    private String name;
    
    private String modelType;
    
    @Size(max = 500, message = "描述长度不能超过500个字符")
    private String description;
    
    private SchedulingStrategy schedulingStrategy;
    
    private String defaultApiKey;
    
    @Size(max = 500, message = "基础URL长度不能超过500个字符")
    private String defaultBaseUrl;
    
    private Integer defaultTimeoutMs;
    
    private Integer defaultMaxRetries;
    
    private Integer priority;
    
    private Boolean enabled;
    
    private Boolean isDefault;
}
